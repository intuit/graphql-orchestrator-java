package com.intuit.graphql.orchestrator.datafetcher;

import com.intuit.graphql.orchestrator.xtext.DataFetcherContext;
import lombok.Getter;

@Getter
public class DataFetcherMetadata {

  private final DataFetcherContext dataFetcherContext;

  public DataFetcherMetadata(DataFetcherContext dataFetcherContext) {
    this.dataFetcherContext = dataFetcherContext;
  }
}
