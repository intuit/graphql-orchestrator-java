package com.intuit.graphql.orchestrator.datafetcher;

import com.intuit.graphql.orchestrator.schema.ServiceMetadata;
import com.intuit.graphql.orchestrator.xtext.DataFetcherContext;
import graphql.GraphQLContext;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import lombok.Getter;

@Getter
public class ServiceDataFetcher extends DataFetcherMetadata implements DataFetcher {

  /**
   * One of Query or Mutation or Subscription
   */
  private final ServiceMetadata serviceMetadata;


  public ServiceDataFetcher(ServiceMetadata serviceMetadata, DataFetcherContext dataFetcherContext) {
    super(dataFetcherContext);
    this.serviceMetadata = serviceMetadata;
  }

  @Override
  public Object get(final DataFetchingEnvironment environment) {
    String dfeFieldName = environment.getField().getName();
    GraphQLContext context = environment.getContext();
    context.put(dfeFieldName, this.serviceMetadata);
    return environment
        .getDataLoader(serviceMetadata.getServiceProvider().getNameSpace())
        .load(environment);
  }
}
