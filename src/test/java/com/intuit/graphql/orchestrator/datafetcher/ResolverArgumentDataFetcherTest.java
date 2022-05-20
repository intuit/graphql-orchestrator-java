package com.intuit.graphql.orchestrator.datafetcher;

import static com.intuit.graphql.orchestrator.TestHelper.query;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.MockitoAnnotations.initMocks;

import com.intuit.graphql.orchestrator.resolverdirective.ResolverArgumentDirective;
import com.intuit.graphql.orchestrator.xtext.DataFetcherContext;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphqlErrorBuilder;
import graphql.Scalars;
import graphql.execution.DataFetcherResult;
import graphql.language.OperationDefinition;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

public class ResolverArgumentDataFetcherTest {

  public String namespace = "TEST";

  public String debtFieldName = "debt";

  public String incomeFieldName = "income";

  public ResolverArgumentDirective debtArgumentResolver = ResolverArgumentDirective.newBuilder()
      .argumentName("test_argument_name")
      .field(debtFieldName)
      .graphQLInputType(Scalars.GraphQLInt)
      .build();

  public ExecutionResult debtExecutionResult = ExecutionResultImpl
      .newExecutionResult()
      .data(Collections.singletonMap(debtFieldName, 1_000))
      .build();

  ResolverArgumentDirective incomeArgumentResolver = ResolverArgumentDirective.newBuilder()
      .argumentName("second_argument_name")
      .field(debtFieldName)
      .graphQLInputType(Scalars.GraphQLInt)
      .build();

  public ExecutionResult incomeExecutionResult = ExecutionResultImpl
      .newExecutionResult()
      .data(Collections.singletonMap(debtFieldName, 1_000))
      .build();

  public OperationDefinition debtQuery = query("{" + debtFieldName + "}");
  public OperationDefinition incomeQuery = query("{" + incomeFieldName + "}");

  @Mock
  public ResolverArgumentDataFetcherHelper mockHelper;

  @Mock
  public ArgumentResolver mockArgumentResolver;

  public ExecutionResult executionResult;

  private DataLoaderRegistry dataLoaderRegistry;

  @Before
  public void setup() {
    initMocks(this);
    executionResult = ExecutionResultImpl.newExecutionResult()
        .data(new HashMap<String, Object>() {{
          put(debtFieldName, 1_000_000);
        }})
        .build();

    dataLoaderRegistry = new DataLoaderRegistry();
    dataLoaderRegistry.register(namespace, DataLoader.newDataLoader(a -> null));
  }

  @Test
  public void testBuilder() {
    ResolverArgumentDataFetcher.newBuilder()
        .queriesByResolverArgument(Collections.emptyMap())
        .dataFetcherContext(DataFetcherContext.newBuilder().build())
        .build();
  }

  @Test(expected = NullPointerException.class)
  public void testBuilderResolverDataIsNull() {
    ResolverArgumentDataFetcher.newBuilder()
        .queriesByResolverArgument(null);
  }

  @Test(expected = NullPointerException.class)
  public void testBuilderDataFetcherContextIsNull() {
    ResolverArgumentDataFetcher.newBuilder().dataFetcherContext(null);
  }

  @Test
  public void singleArgument() {
    DataFetcherResult<Object> dataFetcherResult = DataFetcherResult.newResult()
        .data("test_response").build();
    Map<ResolverArgumentDirective, CompletableFuture<ExecutionResult>> mockArgumentResults = new HashMap<>();
    mockArgumentResults.put(debtArgumentResolver, CompletableFuture.completedFuture(debtExecutionResult));

    doReturn(mockArgumentResults).when(mockArgumentResolver)
        .resolveArguments(any(DataFetchingEnvironment.class), anyMap());

    doReturn(CompletableFuture.completedFuture(dataFetcherResult)).when(mockHelper)
        .callBatchLoaderWithArguments(any(DataFetchingEnvironment.class), anyMap());

    Map<ResolverArgumentDirective, OperationDefinition> map = new HashMap<>();

    map.put(debtArgumentResolver, debtQuery);

    ResolverArgumentDataFetcher resolverArgumentDataFetcher = ResolverArgumentDataFetcher.newBuilder()
        .queriesByResolverArgument(map).dataFetcherContext(DataFetcherContext.newBuilder().namespace(namespace).build())
        .build();

    resolverArgumentDataFetcher.argumentResolver = mockArgumentResolver;
    resolverArgumentDataFetcher.helper = mockHelper;

    DataFetchingEnvironment dataFetchingEnvironment = DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
        .dataLoaderRegistry(dataLoaderRegistry)
        .build();

    final DataFetcherResult<Object> result = resolverArgumentDataFetcher
        .get(dataFetchingEnvironment).join();

    assertThat(result.getData()).isEqualTo("test_response");
  }

  @Test
  public void multipleArguments() {
    ArgumentCaptor<Map> helperArgumentCaptor = ArgumentCaptor.forClass(Map.class);
    DataFetcherResult<Object> dataFetcherResult = DataFetcherResult.newResult()
        .data("test_response").build();

    Map<ResolverArgumentDirective, CompletableFuture<ExecutionResult>> mockResolverData = new HashMap<>();
    mockResolverData.put(debtArgumentResolver, CompletableFuture.completedFuture(debtExecutionResult));
    mockResolverData.put(incomeArgumentResolver, CompletableFuture.completedFuture(incomeExecutionResult));

    doReturn(mockResolverData).when(mockArgumentResolver)
        .resolveArguments(any(DataFetchingEnvironment.class), helperArgumentCaptor.capture());

    doReturn(CompletableFuture.completedFuture(dataFetcherResult)).when(mockHelper)
        .callBatchLoaderWithArguments(any(DataFetchingEnvironment.class), anyMap());

    Map<ResolverArgumentDirective, OperationDefinition> map = new HashMap<>();
    map.put(debtArgumentResolver, debtQuery);
    map.put(incomeArgumentResolver, incomeQuery);

    ResolverArgumentDataFetcher dataFetcher = ResolverArgumentDataFetcher.newBuilder()
        .dataFetcherContext(DataFetcherContext.newBuilder().namespace(namespace).build())
        .queriesByResolverArgument(map)
        .build();

    dataFetcher.argumentResolver = mockArgumentResolver;
    dataFetcher.helper = mockHelper;

    DataFetchingEnvironment dataFetchingEnvironment = DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
        .dataLoaderRegistry(dataLoaderRegistry)
        .build();

    DataFetcherResult<Object> result = dataFetcher.get(dataFetchingEnvironment).join();

    assertThat(result.getData()).isEqualTo("test_response");

    assertThat(helperArgumentCaptor.getValue()).hasSize(2);
  }

  @Test
  public void errors() {
    ExecutionResult errorResult = ExecutionResultImpl.newExecutionResult()
        .addError(GraphqlErrorBuilder.newError().message("boom").build())
        .build();

    Map<ResolverArgumentDirective, CompletableFuture<ExecutionResult>> resolvedArguments = new HashMap<>();
    resolvedArguments.put(debtArgumentResolver, CompletableFuture.completedFuture(errorResult));

    doReturn(resolvedArguments)
        .when(mockArgumentResolver).resolveArguments(any(DataFetchingEnvironment.class), anyMap());

    ResolverArgumentDataFetcher resolverArgumentDataFetcher = ResolverArgumentDataFetcher.newBuilder()
        .queriesByResolverArgument(Collections.emptyMap())
        .dataFetcherContext(DataFetcherContext.newBuilder().namespace(namespace).build())
        .build();

    resolverArgumentDataFetcher.argumentResolver = mockArgumentResolver;

    final DataFetcherResult<Object> result = resolverArgumentDataFetcher
        .get(mock(DataFetchingEnvironment.class)).join();

    assertThat(result.getErrors()).hasSize(1);
  }
}