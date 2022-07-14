package com.intuit.graphql.orchestrator.schema;

import com.intuit.graphql.orchestrator.ServiceProvider;
import com.intuit.graphql.orchestrator.federation.metadata.FederationMetadata;
import com.intuit.graphql.orchestrator.metadata.RenamedMetadata;
import com.intuit.graphql.orchestrator.schema.transform.FieldResolverContext;
import graphql.schema.FieldCoordinates;

public interface ServiceMetadata {

  /**
   * Check if the given typeName exists in provider's schema.
   *
   * @param typeName typeName to check
   * @return true or false
   */
  boolean hasType(String typeName);

  /**
   * Check if the given provider's schema (contains unions/interfaces and) requires typename to be injected in its
   * queries
   *
   * @return true or false
   */
  boolean requiresTypenameInjection();

  boolean hasFieldResolverDirective();

  ServiceProvider getServiceProvider();

  FieldResolverContext getFieldResolverContext(FieldCoordinates fieldCoordinates);

  @Deprecated
  boolean isOwnedByEntityExtension(FieldCoordinates fieldCoordinates);

  boolean isOwnedByThisService(FieldCoordinates fieldCoordinates);

  boolean isFederationService();

  boolean isEntity(String typename);

  FederationMetadata getFederationServiceMetadata();

  boolean shouldModifyDownStreamQuery();

  RenamedMetadata getRenamedMetadata();
}
