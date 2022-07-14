package com.intuit.graphql.orchestrator.xtext;

import static java.util.Objects.requireNonNull;

import com.intuit.graphql.graphQL.DirectiveDefinition;
import com.intuit.graphql.graphQL.FieldDefinition;
import com.intuit.graphql.graphQL.NamedType;
import com.intuit.graphql.graphQL.ObjectTypeDefinition;
import com.intuit.graphql.graphQL.TypeDefinition;
import com.intuit.graphql.graphQL.TypeSystemDefinition;
import com.intuit.graphql.orchestrator.ServiceProvider;
import com.intuit.graphql.orchestrator.federation.metadata.FederationMetadata;
import com.intuit.graphql.orchestrator.federation.metadata.FederationMetadata.EntityExtensionMetadata;
import com.intuit.graphql.orchestrator.metadata.RenamedMetadata;
import com.intuit.graphql.orchestrator.schema.Operation;
import com.intuit.graphql.orchestrator.schema.TypeMetadata;
import com.intuit.graphql.orchestrator.schema.transform.FieldResolverContext;
import com.intuit.graphql.utils.XtextTypeUtils;
import graphql.schema.FieldCoordinates;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import lombok.Getter;
import org.eclipse.xtext.resource.XtextResourceSet;

/**
 * Runtime graph represents the runtime elements required to build the runtime graphql schema. It also contains
 * batchloaders for optimization.
 */
@Getter
public class XtextGraph {

  private final ServiceProvider serviceProvider;
  private final XtextResourceSet xtextResourceSet;
  private final Map<Operation, ObjectTypeDefinition> operationMap;
  private final Map<FieldContext, DataFetcherContext> codeRegistry;
  private final Set<DirectiveDefinition> directives;
  private final Map<String, TypeDefinition> types;
  private final Map<String, TypeMetadata> typeMetadatas;
  private final List<FieldResolverContext> fieldResolverContexts;
  private final Map<FieldCoordinates, FieldDefinition> fieldCoordinates;

  private final boolean hasInterfaceOrUnion;
  private final boolean hasFieldResolverDefinition;

  //to be computed and cached
  private boolean cacheComputed = false;

  private Map<String, TypeDefinition> valueTypesByName;
  private final Map<String, TypeDefinition> entitiesByTypeName;
  private final Map<String, Map<String, TypeSystemDefinition>> entityExtensionsByNamespace;
  private final List<EntityExtensionMetadata> entityExtensionMetadatas;
  private final Map<String, FederationMetadata> federationMetadataByNamespace;
  private final Map<String, RenamedMetadata> renamedMetadataByNamespace;

  private XtextGraph(Builder builder) {
    serviceProvider = builder.serviceProvider;
    xtextResourceSet = requireNonNull(builder.xtextResourceSet, "Resource Set cannot be null");
    //TODO: Research on all Providers having an XtextResource instead of a ResourceSet
    operationMap = builder.operationMap;
    codeRegistry = builder.codeRegistry;
    directives = builder.directives;
    types = builder.types;
    typeMetadatas = builder.typeMetadatas;
    hasInterfaceOrUnion = builder.hasInterfaceOrUnion;
    hasFieldResolverDefinition = builder.hasFieldResolverDefinition;
    fieldResolverContexts = builder.fieldResolverContexts;
    valueTypesByName = builder.valueTypesByName;
    entitiesByTypeName = builder.entities;
    entityExtensionsByNamespace = builder.entityExtensionsByNamespace;
    entityExtensionMetadatas = builder.entityExtensionMetadatas;
    federationMetadataByNamespace = builder.federationMetadataByNamespace;
    renamedMetadataByNamespace = builder.renamedMetadataByNamespace;
    fieldCoordinates = builder.fieldCoordinates;
  }

  /**
   * Creates a new Builder
   *
   * @return the Builder
   */
  public static Builder newBuilder() {
    return new Builder();
  }

  /**
   * Creates a builder from a copy.
   *
   * @param copy the copy
   * @return the builder
   */
  public static Builder newBuilder(XtextGraph copy) {
    Builder builder = new Builder();
    builder.serviceProvider = copy.serviceProvider;
    builder.xtextResourceSet = copy.xtextResourceSet;
    builder.operationMap = copy.getOperationMap();
    builder.codeRegistry = copy.getCodeRegistry();
    builder.directives = copy.getDirectives();
    builder.types = copy.getTypes();
    builder.typeMetadatas = copy.getTypeMetadatas();
    builder.hasInterfaceOrUnion = copy.hasInterfaceOrUnion;
    builder.hasFieldResolverDefinition = copy.hasFieldResolverDefinition;
    builder.fieldResolverContexts = copy.fieldResolverContexts;
    builder.valueTypesByName = copy.valueTypesByName;
    builder.entities = copy.entitiesByTypeName;
    builder.entityExtensionsByNamespace = copy.entityExtensionsByNamespace;
    builder.entityExtensionMetadatas = copy.entityExtensionMetadatas;
    builder.federationMetadataByNamespace = copy.federationMetadataByNamespace;
    builder.renamedMetadataByNamespace = copy.renamedMetadataByNamespace;
    builder.fieldCoordinates = copy.fieldCoordinates;
    return builder;
  }

  /**
   * Check if the given provider's schema (contains unions/interfaces and) requires typename to be injected in its
   * queries
   *
   * @return true or false
   */
  public boolean requiresTypenameInjection() {
    return isHasInterfaceOrUnion();
  }

  public FederationMetadata getFederationServiceMetadata() {
    return getFederationMetadataByNamespace().get(serviceProvider.getNameSpace());
  }

  public RenamedMetadata getRenamedMetadata() {
    return getRenamedMetadataByNamespace().get(serviceProvider.getNameSpace());
  }

  public TypeDefinition getType(final NamedType namedType) {
    return types.get(XtextTypeUtils.typeName(namedType));
  }

  public TypeDefinition getType(final String typeName) {
    return types.get(typeName);
  }

  public void addType(TypeDefinition type) {
    this.types.put(type.getName(), type);
  }

  public TypeDefinition removeType(TypeDefinition type) {
    return this.types.remove(type.getName());
  }

  public Map<String, TypeDefinition> getEntitiesByTypeName() {
    return entitiesByTypeName;
  }

  /**
   * Gets operation.
   *
   * @param operation the operation
   * @return the operation
   */
  public ObjectTypeDefinition getOperationType(Operation operation) {
    return operationMap.get(operation);
  }

  public Operation getOperation(String typename) {
    return operationMap.entrySet()
            .stream()
            .filter(entry -> entry.getValue().getName().equals(typename))
            .map(Map.Entry::getKey)
            .findAny().orElse(null);
  }

  public boolean isOperationType(TypeDefinition typeDefinition) {
    return operationMap.containsValue(typeDefinition);
  }

  /**
   * Transform runtime graph.
   *
   * @param builderConsumer the builder consumer
   * @return the runtime graph
   */
  public XtextGraph transform(Consumer<Builder> builderConsumer) {
    Builder builder = newBuilder(this);
    builderConsumer.accept(builder);
    return builder.build();
  }

  public Map<String, Map<String, TypeSystemDefinition>> getEntityExtensionsByNamespace() {
    return this.entityExtensionsByNamespace;
  }

  public void addFederationMetadata(FederationMetadata federationMetadata) {
    this.federationMetadataByNamespace.put(serviceProvider.getNameSpace(), federationMetadata);
  }

  public void addToEntityExtensionMetadatas(EntityExtensionMetadata entityExtensionMetadatas) {
    this.entityExtensionMetadatas.add(entityExtensionMetadatas);
  }

  public void addRenamedMetadata(RenamedMetadata renamedMetadata) {
    this.renamedMetadataByNamespace.put(serviceProvider.getNameSpace(), renamedMetadata);
  }


  /**
   * The type Builder.
   */
  public static final class Builder {

    private ServiceProvider serviceProvider = null;
    private XtextResourceSet xtextResourceSet = null;
    private Map<Operation, ObjectTypeDefinition> operationMap = new HashMap<>();
    private Map<FieldContext, DataFetcherContext> codeRegistry = new HashMap<>();
    private Set<DirectiveDefinition> directives = new HashSet<>();
    private Map<String, TypeDefinition> types = new HashMap<>();
    private Map<String, TypeMetadata> typeMetadatas = new HashMap<>();
    private Map<String, TypeDefinition> valueTypesByName = new HashMap<>();
    private Map<String, TypeDefinition> entities = new HashMap<>();
    private Map<String, Map<String, TypeSystemDefinition>> entityExtensionsByNamespace = new HashMap<>();
    private List<EntityExtensionMetadata> entityExtensionMetadatas = new ArrayList<>();
    private List<FieldResolverContext> fieldResolverContexts = new ArrayList<>();
    private Map<String, FederationMetadata> federationMetadataByNamespace = new HashMap<>();
    private Map<String, RenamedMetadata> renamedMetadataByNamespace = new HashMap<>();
    private Map<FieldCoordinates, FieldDefinition> fieldCoordinates = new HashMap<>();
    private boolean hasInterfaceOrUnion = false;
    private boolean hasFieldResolverDefinition = false;

    private Builder() {
    }

    /**
     * Operation map builder.
     *
     * @param operationMap the operation map
     * @return the builder
     */
    public Builder operationMap(Map<Operation, ObjectTypeDefinition> operationMap) {
      this.operationMap.putAll(operationMap);
      return this;
    }

    /**
     * Object Types
     *
     * @param codeRegistry the codeRegistry map
     * @return the builder
     */
    public Builder codeRegistry(Map<FieldContext, DataFetcherContext> codeRegistry) {
      this.codeRegistry.putAll(codeRegistry);
      return this;
    }

    public Builder dataFetcherContext(FieldContext fieldCoordinate, DataFetcherContext dataFetcherContext) {
      this.codeRegistry.put(requireNonNull(fieldCoordinate), requireNonNull(dataFetcherContext));
      return this;
    }

    /**
     * Query builder.
     *
     * @param query the query
     * @return the builder
     */
    public Builder query(ObjectTypeDefinition query) {
      this.operationMap.put(Operation.QUERY, requireNonNull(query));
      return this;
    }

    /**
     * Mutation builder.
     *
     * @param mutation the mutation
     * @return the builder
     */
    public Builder mutation(ObjectTypeDefinition mutation) {
      this.operationMap.put(Operation.MUTATION, requireNonNull(mutation));
      return this;
    }

    /**
     * Service Context builder.
     *
     * @param serviceProvider the service provider
     * @return the builder
     */
    public Builder serviceProvider(ServiceProvider serviceProvider) {
      requireNonNull(serviceProvider);
      this.serviceProvider = serviceProvider;
      return this;
    }

    /**
     * XtextResourceSet builder.
     *
     * @param xtextResourceSet the resource set
     * @return the builder
     */
    public Builder xtextResourceSet(XtextResourceSet xtextResourceSet) {
      requireNonNull(xtextResourceSet);
      this.xtextResourceSet = xtextResourceSet;
      return this;
    }

    /**
     * Directives builder.
     *
     * @param directiveDefinitions the set of directives
     * @return the builder
     */
    public Builder directives(Set<DirectiveDefinition> directiveDefinitions) {
      requireNonNull(directiveDefinitions);
      this.directives.addAll(directiveDefinitions);
      return this;
    }

    /**
     * Types builder.
     *
     * @param types the map of xtext graphql types
     * @return the builder
     */
    public Builder types(Map<String, TypeDefinition> types) {
      requireNonNull(types);
      this.types.putAll(types);
      return this;
    }

    /**
     * Types builder.
     *
     * @param typeMetadatas the map of TypeMetadata
     * @return the builder
     */
    public Builder typeMetadatas(Map<String, TypeMetadata> typeMetadatas) {
      requireNonNull(typeMetadatas);
      this.typeMetadatas.putAll(typeMetadatas);
      return this;
    }

    /**
     * isHasInterfaceOrUnion builder.
     *
     * @param hasInterfaceOrUnion boolean value
     * @return the builder
     */
    public Builder hasInterfaceOrUnion(boolean hasInterfaceOrUnion) {
      this.hasInterfaceOrUnion = hasInterfaceOrUnion;
      return this;
    }

    /**
     * hasFieldResolverDefinition builder.
     *
     * @param hasFieldResolverDefinition boolean value
     * @return the builder
     */
    public Builder hasFieldResolverDefinition(boolean hasFieldResolverDefinition) {
      this.hasFieldResolverDefinition = hasFieldResolverDefinition;
      return this;
    }

    public Builder fieldResolverContexts(List<FieldResolverContext> fieldResolverContexts) {
      this.fieldResolverContexts.addAll(fieldResolverContexts);
      return this;
    }

    public Builder valueTypesByName(Map<String, TypeDefinition> valueTypes) {
      requireNonNull(valueTypes);
      this.valueTypesByName.putAll(valueTypes);
      return this;
    }

    public Builder entitiesByTypeName(Map<String, TypeDefinition> entities) {
      requireNonNull(entities);
      this.entities.putAll(entities);
      return this;
    }

    public Builder entityExtensionsByNamespace(
            Map<String, Map<String, TypeSystemDefinition>> entityExtensionsByNamespace) {
      requireNonNull(entityExtensionsByNamespace);
      this.entityExtensionsByNamespace.putAll(entityExtensionsByNamespace);
      return this;
    }

    public Builder federationMetadataByNamespace(Map<String, FederationMetadata> federationMetadataByNamespace) {
      requireNonNull(federationMetadataByNamespace);
      this.federationMetadataByNamespace.putAll(federationMetadataByNamespace);
      return this;
    }

    public Builder entityExtensionMetadatas(List<EntityExtensionMetadata> entityExtensionMetadatas) {
      requireNonNull(entityExtensionMetadatas);
      this.entityExtensionMetadatas.addAll(entityExtensionMetadatas);
      return this;
    }

    public Builder renamedMetadataByNamespace(Map<String, RenamedMetadata> renamedMetadataByNamespace) {
      requireNonNull(renamedMetadataByNamespace);
      this.renamedMetadataByNamespace.putAll(renamedMetadataByNamespace);
      return this;
    }

    public Builder fieldCoordinates(Map<FieldCoordinates, FieldDefinition> fieldCoordinates) {
      this.fieldCoordinates.putAll(fieldCoordinates);
      return this;
    }

    /**
     * Build runtime graph.
     *
     * @return the runtime graph
     */
    public XtextGraph build() {
      return new XtextGraph(this);
    }
  }
}
