package com.intuit.graphql.orchestrator.datafetcher;

import com.intuit.graphql.orchestrator.resolverdirective.FieldResolverDataLoaderUtil;
import com.intuit.graphql.orchestrator.xtext.DataFetcherContext;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import java.util.Objects;

public class FieldResolverDirectiveDataFetcher extends DataFetcherMetadata implements DataFetcher<Object> {


  public FieldResolverDirectiveDataFetcher(DataFetcherContext dataFetcherContext) {
    super(dataFetcherContext);
    Objects.requireNonNull(dataFetcherContext, "DataFetcherContext is required");
    Objects.requireNonNull(dataFetcherContext.getFieldResolverContext(),
        "FieldResolverContext is required");
  }

  @Override
  public Object get(final DataFetchingEnvironment dataFetchingEnvironment) {
    return dataFetchingEnvironment
        .getDataLoader(
            FieldResolverDataLoaderUtil.createDataLoaderKeyFrom(getDataFetcherContext().getFieldResolverContext()))
        .load(dataFetchingEnvironment);
  }

}
