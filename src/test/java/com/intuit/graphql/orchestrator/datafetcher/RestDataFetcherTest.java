package com.intuit.graphql.orchestrator.datafetcher;

import static graphql.schema.DataFetchingEnvironmentImpl.newDataFetchingEnvironment;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.intuit.graphql.orchestrator.ServiceProvider.ServiceType;
import com.intuit.graphql.orchestrator.TestServiceProvider;
import com.intuit.graphql.orchestrator.batch.QueryExecutor;
import com.intuit.graphql.orchestrator.schema.ServiceMetadata;
import com.intuit.graphql.orchestrator.xtext.DataFetcherContext;
import com.intuit.graphql.orchestrator.xtext.XtextGraph;
import graphql.GraphQLContext;
import graphql.execution.DataFetcherResult;
import graphql.execution.MergedField;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.language.OperationDefinition.Operation;
import graphql.language.SelectionSet;
import graphql.schema.DataFetchingEnvironment;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.assertj.core.api.AssertionsForInterfaceTypes;
import org.junit.Before;
import org.junit.Test;

public class RestDataFetcherTest {

  private DataFetchingEnvironment dataFetchingEnvironment;

  @Before
  public void setup() {
    Map<String, Object> variables = ImmutableMap.of(
        "key1", "val1",
        "key2", "val2"
    );

    SelectionSet selectionSet = SelectionSet.newSelectionSet()
        .selection(Field.newField("subField1").build())
        .selection(Field.newField("subField2").build()).build();

    Field topLevelField = Field.newField("topLevelField").selectionSet(selectionSet).build();

    OperationDefinition operationDefinition = OperationDefinition.newOperationDefinition()
        .name("TestRestQuery")
        .operation(Operation.QUERY)
        .selectionSet(selectionSet)
        .build();

    dataFetchingEnvironment = createTestDataFetchingEnvironment(
        operationDefinition,
        MergedField.newMergedField(topLevelField)
            .build(),
        variables
    );
  }

  @SuppressWarnings("unchecked")
  @Test
  public void canExecuteRequest() {

    QueryExecutor queryExecutor = (executionInput, context) -> {
      assertThat(executionInput).isNotNull();
      // assertThat(executionInput.getQuery()).isNotNull();
      assertThat(executionInput.getOperationName()).isNotNull();
      assertThat(executionInput.getVariables()).isNotNull();
      Document document = context.get(Document.class);
      DataFetchingEnvironment dfe = context.get(DataFetchingEnvironment.class);

      assertThat(document).isNotNull();
      assertThat(dfe).isNotNull();
      assertThat(dfe).isEqualTo(dataFetchingEnvironment);

      Map<String, Object> responseMap = ImmutableMap.of("data", ImmutableMap.of(
          "topLevelField", ImmutableMap.of(
              "subField1", "stringVal1",
              "subField2", "stringVal2"
          ))
      );

      return CompletableFuture.completedFuture(responseMap);
    };

    TestServiceProvider testServiceProvider = TestServiceProvider.newBuilder().queryFunction(queryExecutor)
        .serviceType(ServiceType.REST).build();
    ServiceMetadata serviceMetadata = mock(XtextGraph.class);
    DataFetcherContext dataFetcherContext = mock(DataFetcherContext.class);
    when(serviceMetadata.getServiceProvider()).thenReturn(testServiceProvider);

    RestDataFetcher restDataFetcher = new RestDataFetcher(serviceMetadata, dataFetcherContext);

    ((CompletableFuture<DataFetcherResult<Map<String, Object>>>) restDataFetcher.get(dataFetchingEnvironment))
        .whenComplete((dataFetcherResult, throwable) -> {
          assertThat(throwable).isNull();
          assertThat(dataFetcherResult).isNotNull();
          Map<String, Object> data = (Map<String, Object>) dataFetcherResult.getData();
          AssertionsForInterfaceTypes.assertThat(data).containsOnlyKeys("data");
          AssertionsForInterfaceTypes.assertThat((Map<String, Object>) data.get("topLevelField"))
              .containsOnlyKeys("subField1", "subField2");
        });
  }

// TODO see if needed
  // public void canExecuteRequestWithEmptySelectionSet() {
  // }

  private DataFetchingEnvironment createTestDataFetchingEnvironment(OperationDefinition opDef, MergedField field,
      Map<String, Object> variables) {
    return newDataFetchingEnvironment()
        .context(GraphQLContext.newContext().build())
        .mergedField(field)
        .operationDefinition(opDef)
        .variables(variables)
        .build();
  }
}