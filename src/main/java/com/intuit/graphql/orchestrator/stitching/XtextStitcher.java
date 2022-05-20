package com.intuit.graphql.orchestrator.stitching;

import static com.intuit.graphql.orchestrator.xtext.DataFetcherContext.DataFetcherType.RESOLVER_ARGUMENT;
import static com.intuit.graphql.orchestrator.xtext.DataFetcherContext.DataFetcherType.RESOLVER_ON_FIELD_DEFINITION;
import static com.intuit.graphql.orchestrator.xtext.DataFetcherContext.DataFetcherType.SERVICE;
import static com.intuit.graphql.orchestrator.xtext.DataFetcherContext.DataFetcherType.STATIC;
import static graphql.schema.FieldCoordinates.coordinates;
import static java.util.Objects.requireNonNull;

import com.intuit.graphql.orchestrator.ServiceProvider;
import com.intuit.graphql.orchestrator.ServiceProvider.ServiceType;
import com.intuit.graphql.orchestrator.batch.BatchLoaderExecutionHooks;
import com.intuit.graphql.orchestrator.batch.FieldResolverBatchLoader;
import com.intuit.graphql.orchestrator.batch.GraphQLServiceBatchLoader;
import com.intuit.graphql.orchestrator.datafetcher.FieldResolverDirectiveDataFetcher;
import com.intuit.graphql.orchestrator.datafetcher.ResolverArgumentDataFetcher;
import com.intuit.graphql.orchestrator.datafetcher.RestDataFetcher;
import com.intuit.graphql.orchestrator.datafetcher.ServiceDataFetcher;
import com.intuit.graphql.orchestrator.resolverdirective.FieldResolverDataLoaderUtil;
import com.intuit.graphql.orchestrator.resolverdirective.ResolverArgumentDirective;
import com.intuit.graphql.orchestrator.resolverdirective.ResolverArgumentQueryBuilder;
import com.intuit.graphql.orchestrator.schema.Operation;
import com.intuit.graphql.orchestrator.schema.RuntimeGraph;
import com.intuit.graphql.orchestrator.schema.fold.XtextGraphFolder;
import com.intuit.graphql.orchestrator.schema.transform.AllTypesTransformer;
import com.intuit.graphql.orchestrator.schema.transform.DirectivesTransformer;
import com.intuit.graphql.orchestrator.schema.transform.DomainTypesTransformer;
import com.intuit.graphql.orchestrator.schema.transform.FieldResolverTransformerPostMerge;
import com.intuit.graphql.orchestrator.schema.transform.FieldResolverTransformerPreMerge;
import com.intuit.graphql.orchestrator.schema.transform.GraphQLAdapterTransformer;
import com.intuit.graphql.orchestrator.schema.transform.ResolverArgumentTransformer;
import com.intuit.graphql.orchestrator.schema.transform.Transformer;
import com.intuit.graphql.orchestrator.schema.transform.TypeExtensionTransformer;
import com.intuit.graphql.orchestrator.schema.transform.UnionAndInterfaceTransformer;
import com.intuit.graphql.orchestrator.utils.XtextToGraphQLJavaVisitor;
import com.intuit.graphql.orchestrator.xtext.DataFetcherContext.DataFetcherType;
import com.intuit.graphql.orchestrator.xtext.XtextGraph;
import com.intuit.graphql.orchestrator.xtext.XtextGraphBuilder;
import graphql.execution.DataFetcherResult;
import graphql.language.OperationDefinition;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLType;
import graphql.schema.StaticDataFetcher;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.dataloader.BatchLoader;

/**
 * The type Xtext stitcher.
 */
public class XtextStitcher implements Stitcher {

  private static final ResolverArgumentQueryBuilder queryBuilder = new ResolverArgumentQueryBuilder();

  private final List<Transformer<XtextGraph, XtextGraph>> preMergeTransformers;
  private final List<Transformer<XtextGraph, XtextGraph>> postMergeTransformers;
  private final BatchLoaderExecutionHooks<DataFetchingEnvironment, DataFetcherResult<Object>> batchLoaderHooks;

  private XtextStitcher(final Builder builder) {
    preMergeTransformers = builder.preMergeTransformers;
    postMergeTransformers = builder.postMergeTransformers;
    batchLoaderHooks = builder.batchLoaderHooks;
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  /**
   * Uses Graph Transformers to transform and enrich provider schemas. These transformed graphs are stitched into a
   * single graph, which is then used to create an executable RuntimeGraph.
   *
   * @param serviceProviders the service contexts
   * @return runtime graph
   */
  @Override
  public RuntimeGraph stitch(List<ServiceProvider> serviceProviders) {

    //Transform Graphs
    Map<String, XtextGraph> xtextGraphMap = serviceProviders.stream()
        .map(XtextGraphBuilder::build)
        .map(xtextGraph -> transform(xtextGraph, preMergeTransformers))
        .collect(Collectors.toMap(graph -> graph.getServiceProvider().getNameSpace(), Function.identity(),
            (g1, g2) -> {
              throw new StitchingException(
                  String.format("Duplicate Namespace: %s", g1.getServiceProvider().getNameSpace()));
            }));

    //Stitch Graphs
    XtextGraph stitchedGraph = new XtextGraphFolder().fold(XtextGraph.emptyGraph(), xtextGraphMap.values());

    //Transform after merge
    XtextGraph stitchedTransformedGraph = transform(stitchedGraph, postMergeTransformers);

    //Executable RuntimeGraph with BatchLoaders and DataFetchers
    final Map<String, BatchLoader> batchLoaders = getBatchLoaders(xtextGraphMap);

    stitchedTransformedGraph.getFieldResolverContexts().forEach(fieldResolverContext -> {
      FieldResolverBatchLoader fieldResolverDataLoader = FieldResolverBatchLoader
          .builder()
          .fieldResolverContext(fieldResolverContext)
          .build();

      String batchLoaderKey = FieldResolverDataLoaderUtil.createDataLoaderKeyFrom(fieldResolverContext);
      batchLoaders.put(batchLoaderKey, fieldResolverDataLoader);

    });

    final GraphQLCodeRegistry.Builder codeRegistryBuilder = getCodeRegistry(stitchedTransformedGraph, xtextGraphMap);

    final RuntimeGraph.Builder runtimeGraphBuilder = createRuntimeGraph(stitchedTransformedGraph);

    RuntimeGraph runtimeGraph = runtimeGraphBuilder
        .batchLoaders(batchLoaders)
        .codeRegistry(codeRegistryBuilder)
        .build();

    runtimeGraph.getExecutableSchema();

    return runtimeGraph;
  }

  /**
   * Creates a namespace vs batch loader map for corresponding data providers per graph
   *
   * @param xtextGraphMap map of namespace vs xtext graph for all data providers
   * @return map of namespace vs batch loader
   */
  private Map<String, BatchLoader> getBatchLoaders(Map<String, XtextGraph> xtextGraphMap) {

    HashMap<String, BatchLoader> batchLoaderMap = new HashMap<>();
    xtextGraphMap.forEach((namespace, graph) -> {
      if (graph.getServiceProvider().getSeviceType() == ServiceType.GRAPHQL || graph.getServiceProvider()
          .isFederationProvider()) {
        batchLoaderMap.put(namespace,
            GraphQLServiceBatchLoader
                .newQueryExecutorBatchLoader()
                .queryExecutor(graph.getServiceProvider())
                .serviceMetadata(graph)
                .batchLoaderExecutionHooks(batchLoaderHooks)
                .build());
      }
    });
    return batchLoaderMap;
  }

  /**
   * Builds GraphQL Code Registry for a xtext graph using field context and data fetcher context
   *
   * @param mergedGraph the post-merged and post-transformed graph
   * @param graphsByNamespace the individual graphs that were used to build the merged graph
   * @return GraphQL Code Registry Builder
   */
  private GraphQLCodeRegistry.Builder getCodeRegistry(XtextGraph mergedGraph,
      Map<String, XtextGraph> graphsByNamespace) {

    GraphQLCodeRegistry.Builder builder = GraphQLCodeRegistry.newCodeRegistry();

    mergedGraph.getCodeRegistry().forEach((fieldContext, dataFetcherContext) -> {
      FieldCoordinates coordinates = coordinates(fieldContext.getParentType(), fieldContext.getFieldName());
      DataFetcherType type = dataFetcherContext.getDataFetcherType();

      if (type == STATIC) {
        builder.dataFetcher(coordinates, new StaticDataFetcher(Collections.emptyMap()));
      } else if (type == SERVICE) {
        final XtextGraph serviceMetadata = graphsByNamespace.get(dataFetcherContext.getNamespace());
        builder.dataFetcher(coordinates,
            dataFetcherContext.getServiceType() == ServiceType.REST
                ? new RestDataFetcher(serviceMetadata, dataFetcherContext)
                : new ServiceDataFetcher(serviceMetadata, dataFetcherContext)
        );
      } else if (type == RESOLVER_ARGUMENT) {
        final XtextToGraphQLJavaVisitor visitor = XtextToGraphQLJavaVisitor.newBuilder().build();

        final List<GraphQLArgument> graphqlArguments = visitor
            .createGraphqlArguments(mergedGraph.getResolverArgumentFields().get(fieldContext));

        Map<ResolverArgumentDirective, OperationDefinition> map = graphqlArguments.stream()
            .map(ResolverArgumentDirective::fromGraphQLArgument)
            .collect(
                Collectors.toMap(Function.identity(), d -> queryBuilder.buildQuery(d.getField(), d.getInputType())));

        builder.dataFetcher(coordinates, ResolverArgumentDataFetcher.newBuilder()
            .dataFetcherContext(dataFetcherContext)
            .queriesByResolverArgument(map)
            .build()
        );
      } else if (type == RESOLVER_ON_FIELD_DEFINITION) {
        builder.dataFetcher(coordinates, new FieldResolverDirectiveDataFetcher(dataFetcherContext));
      }
    });
    return builder;
  }

  /**
   * Creates a {@link RuntimeGraph} from an {@link XtextGraph} by converting xtext AST to GraphQL Java AST
   *
   * @param xtextGraph xtext graph
   * @return RuntimeGraph Builder
   */
  private RuntimeGraph.Builder createRuntimeGraph(XtextGraph xtextGraph) {

    XtextToGraphQLJavaVisitor visitor = XtextToGraphQLJavaVisitor.newBuilder().build();

    //fill operations
    final Map<Operation, GraphQLObjectType> operationMap = new EnumMap<>(Operation.class);

    xtextGraph.getOperationMap()
        .forEach((operation, objectTypeDefinition) ->
            operationMap.put(operation, (GraphQLObjectType) visitor.doSwitch(objectTypeDefinition)));

    return RuntimeGraph.newBuilder()
        .operationMap(operationMap)
        .objectTypes(visitor.getGraphQLObjectTypes())
        .additionalTypes(getAdditionalTypes(xtextGraph, visitor.getGraphQLObjectTypes()))
        .additionalDirectives(getAdditionalDirectives(xtextGraph, visitor.getDirectiveDefinitions()));
  }

  /**
   * Collects additional types (not referenced from schema top level) by filtering visited types from all types in
   * schema.
   *
   * @param xtextGraph the xtext graph
   * @param visited types referenced from schema top level
   * @return map of type names to additional types
   */
  private Map<String, GraphQLType> getAdditionalTypes(XtextGraph xtextGraph, Map<String, GraphQLType> visited) {

    XtextToGraphQLJavaVisitor localVisitor = XtextToGraphQLJavaVisitor.newBuilder().graphqlObjectTypes(visited).build();

    return xtextGraph.getTypes().values().stream()
        .filter(type -> !xtextGraph.isOperationType(type))
        .filter(type -> !(visited.containsKey(type.getName())))
        .collect(Collectors.toMap(type -> type.getName(), type -> (GraphQLType) localVisitor.doSwitch(type)));
  }

  private Set<GraphQLDirective> getAdditionalDirectives(XtextGraph xtextGraph, Map<String, GraphQLDirective> visited) {
    XtextToGraphQLJavaVisitor localVisitor = XtextToGraphQLJavaVisitor.newBuilder().directiveDefinitions(visited)
        .build();

    return xtextGraph.getDirectives().stream()
        .map(localVisitor::doSwitch)
        .map(GraphQLDirective.class::cast)
        .collect(Collectors.toSet());
  }

  private XtextGraph transform(XtextGraph xtextGraph, List<Transformer<XtextGraph, XtextGraph>> transformers) {

    for (Transformer<XtextGraph, XtextGraph> transformer : transformers) {
      xtextGraph = transformer.transform(xtextGraph);
    }
    return xtextGraph;
  }

  public static final class Builder {

    private List<Transformer<XtextGraph, XtextGraph>> preMergeTransformers = defaultPreMergeTransformers();
    private List<Transformer<XtextGraph, XtextGraph>> postMergeTransformers = defaultPostMergeTransformers();
    private BatchLoaderExecutionHooks<DataFetchingEnvironment, DataFetcherResult<Object>> batchLoaderHooks = BatchLoaderExecutionHooks.DEFAULT_HOOKS;

    /**
     * Provides the default pre-merge transformers that each operate on the individual provider graphs.
     */
    private List<Transformer<XtextGraph, XtextGraph>> defaultPreMergeTransformers() {
      return Arrays.asList(
          new TypeExtensionTransformer(),
          new DomainTypesTransformer(),
          new AllTypesTransformer(),
          new DirectivesTransformer(),
          new UnionAndInterfaceTransformer(),
          new FieldResolverTransformerPreMerge()
      );
    }

    /**
     * Provides the default post-merge transformers that operate on the stitched and merged graph.
     */
    private List<Transformer<XtextGraph, XtextGraph>> defaultPostMergeTransformers() {
      return Arrays.asList(
          new ResolverArgumentTransformer(),
          new FieldResolverTransformerPostMerge(),
          new GraphQLAdapterTransformer()
      );
    }

    private Builder() {
    }

    public Builder preMergeTransformers(final List<Transformer<XtextGraph, XtextGraph>> val) {
      preMergeTransformers = requireNonNull(val);
      return this;
    }

    public Builder postMergeTransformers(final List<Transformer<XtextGraph, XtextGraph>> val) {
      postMergeTransformers = requireNonNull(val);
      return this;
    }

    public Builder batchLoaderHooks(
        final BatchLoaderExecutionHooks<DataFetchingEnvironment, DataFetcherResult<Object>> hooks) {
      batchLoaderHooks = requireNonNull(hooks);
      return this;
    }

    public XtextStitcher build() {
      return new XtextStitcher(this);
    }
  }
}
