package com.intuit.graphql.orchestrator.datafetcher;

import static com.intuit.graphql.orchestrator.batch.DefaultBatchResultTransformer.toSingleResult;
import static graphql.language.AstPrinter.printAstCompact;

import com.intuit.graphql.orchestrator.batch.DefaultQueryResponseModifier;
import com.intuit.graphql.orchestrator.batch.QueryResponseModifier;
import com.intuit.graphql.orchestrator.schema.ServiceMetadata;
import com.intuit.graphql.orchestrator.xtext.DataFetcherContext;
import graphql.ExecutionInput;
import graphql.GraphQLContext;
import graphql.language.Document;
import graphql.language.OperationDefinition;
import graphql.language.OperationDefinition.Operation;
import graphql.language.SelectionSet;
import graphql.language.VariableDefinition;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import java.util.List;

public class RestDataFetcher extends DataFetcherMetadata implements DataFetcher {

  private final ServiceMetadata serviceMetadata;
  private final QueryResponseModifier queryResponseModifier = new DefaultQueryResponseModifier();

  public RestDataFetcher(final ServiceMetadata serviceMetadata, final DataFetcherContext dataFetcherContext) {
    super(dataFetcherContext);
    this.serviceMetadata = serviceMetadata;
  }

  @Override
  public Object get(DataFetchingEnvironment dataFetchingEnvironment) {

    GraphQLContext context = dataFetchingEnvironment.getContext();

    OperationDefinition operationDefinition = dataFetchingEnvironment.getOperationDefinition();

    Operation operation = operationDefinition.getOperation();

    String operationName = operationDefinition.getName();

    SelectionSet selectionSet = SelectionSet.newSelectionSet().selection(dataFetchingEnvironment.getField()).build();

    final List<VariableDefinition> variableDefinitions = operationDefinition.getVariableDefinitions();

    OperationDefinition query = OperationDefinition.newOperationDefinition()
        .name(operationName)
        .variableDefinitions(variableDefinitions)
        .selectionSet(selectionSet)
        .operation(operation)
        .build();

    Document document = Document.newDocument()
        .definition(query)
        .build();

    ExecutionInput i = ExecutionInput.newExecutionInput()
        .context(context)
        .root(document)
        .query(printAstCompact(document)) // No need to stringify query for REST
        .operationName(query.getName())
        .variables(dataFetchingEnvironment.getVariables())
        .build();

    // very important
    context.put(Document.class, document);
    context.put(DataFetchingEnvironment.class, dataFetchingEnvironment);

    return this.serviceMetadata.getServiceProvider()
        .query(i, context)
        .thenApply(queryResponseModifier::modify)
        .thenApply(queryResponse -> toSingleResult(queryResponse, dataFetchingEnvironment));
  }

}