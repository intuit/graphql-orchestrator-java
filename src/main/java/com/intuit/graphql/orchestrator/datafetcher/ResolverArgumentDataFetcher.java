package com.intuit.graphql.orchestrator.datafetcher;

import com.intuit.graphql.orchestrator.resolverdirective.ResolverArgumentDirective;
import com.intuit.graphql.orchestrator.xtext.DataFetcherContext;
import graphql.ExecutionResult;
import graphql.GraphQLError;
import graphql.VisibleForTesting;
import graphql.execution.Async;
import graphql.execution.DataFetcherResult;
import graphql.language.OperationDefinition;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * This class is responsible for executing data fetching for resolver arguments by querying downstream resovler services
 * for argument data, injecting the data as arguments to the original query, and sending it to the downstream service
 * that needs the data.
 */
public class ResolverArgumentDataFetcher extends DataFetcherMetadata implements
    DataFetcher<CompletableFuture<DataFetcherResult<Object>>> {

  private final Map<ResolverArgumentDirective, OperationDefinition> resolverQueryByDirective;

  @VisibleForTesting
  ResolverArgumentDataFetcherHelper helper;

  @VisibleForTesting
  ArgumentResolver argumentResolver;

  private ResolverArgumentDataFetcher(final Builder builder) {
    super(builder.dataFetcherContext);
    this.resolverQueryByDirective = builder.resolverQueryByDirective;
    this.helper = new ResolverArgumentDataFetcherHelper(builder.dataFetcherContext.getNamespace());
    this.argumentResolver = ArgumentResolver.newBuilder().build();
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  @Override
  public CompletableFuture<DataFetcherResult<Object>> get(final DataFetchingEnvironment environment) {

    Map<ResolverArgumentDirective, CompletableFuture<ExecutionResult>> serviceFuturesByArgument = argumentResolver
        .resolveArguments(environment, resolverQueryByDirective);

    return Async.each(new ArrayList<>(serviceFuturesByArgument.values()))
        .thenCompose(results -> handleResolverArguments(environment, results, serviceFuturesByArgument));
  }

  private CompletionStage<DataFetcherResult<Object>> handleResolverArguments(DataFetchingEnvironment env,
      List<ExecutionResult> results,
      Map<ResolverArgumentDirective, CompletableFuture<ExecutionResult>> completedResults) {
    List<GraphQLError> aggregatedErrors = results.stream()
        .flatMap(result -> result.getErrors().stream())
        .collect(Collectors.toList());

    if (!aggregatedErrors.isEmpty()) {
      DataFetcherResult<Object> executionResultWithErrors = DataFetcherResult.newResult()
          .errors(aggregatedErrors)
          .build();

      return CompletableFuture.completedFuture(executionResultWithErrors);
    }

    final CompletionStage<DataFetcherResult<Object>> batchLoaderFuture = helper
        .callBatchLoaderWithArguments(env, extractArguments(completedResults));

    /*
    n.b. this additional dispatch is necessary as the dispatch that is performed by GraphQL java has already been initiated
    due to the nature of this data fetcher initially getting argument info before retrieving data. If we don't
    call dispatch, the DataLoader will never call to the downstream service to fetch data.
     */
    env.getDataLoader(getDataFetcherContext().getNamespace()).dispatch();

    return batchLoaderFuture;
  }


  private Map<ResolverArgumentDirective, Object> extractArguments(
      Map<ResolverArgumentDirective, CompletableFuture<ExecutionResult>> completedFutures) {
    Map<ResolverArgumentDirective, Object> results = new HashMap<>(completedFutures.size());

    completedFutures.forEach((resolverArgumentDirective, executionResultFuture) -> {
      ExecutionResult executionResult = executionResultFuture.join();

      Object argumentData = filterArgumentData(resolverArgumentDirective.getField(), executionResult.getData());

      results.put(resolverArgumentDirective, argumentData);
    });

    return results;
  }

  private Object filterArgumentData(String path, Map<String, Object> data) {
    String[] pathToSearch = path.split("\\.");
    Object retVal = data;

    for (final String toSearch : pathToSearch) {
      if (retVal == null) {
        return null;
      }
      retVal = ((Map) retVal).get(toSearch);
    }

    return retVal;
  }

  public static final class Builder {

    private Map<ResolverArgumentDirective, OperationDefinition> resolverQueryByDirective = new HashMap<>();
    private DataFetcherContext dataFetcherContext;

    private Builder() {
    }

    public Builder queriesByResolverArgument(final Map<ResolverArgumentDirective, OperationDefinition> val) {
      resolverQueryByDirective = Objects.requireNonNull(val);
      return this;
    }

    public Builder dataFetcherContext(final DataFetcherContext val) {
      dataFetcherContext = Objects.requireNonNull(val);
      return this;
    }

    public ResolverArgumentDataFetcher build() {
      return new ResolverArgumentDataFetcher(this);
    }
  }
}
