package com.intuit.graphql.orchestrator.schema.transform;

import static java.lang.String.format;

import com.intuit.graphql.graphQL.TypeDefinition;
import com.intuit.graphql.orchestrator.federation.EntityTypeMerger;
import com.intuit.graphql.orchestrator.federation.Federation2PureGraphQLUtil;
import com.intuit.graphql.orchestrator.federation.extendsdirective.EntityExtension;
import com.intuit.graphql.orchestrator.federation.extendsdirective.exceptions.EntityExtensionException;
import com.intuit.graphql.orchestrator.xtext.XtextGraph;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class KeyTransformerPostMerge implements Transformer<XtextGraph, XtextGraph> {

  private static final EntityTypeMerger entityTypeMerger = new EntityTypeMerger();

  @Override
  public XtextGraph transform(XtextGraph xtextGraph) {
    Map<String, TypeDefinition> entitiesByTypename = xtextGraph.getEntitiesByTypeName();
    Map<String, List<TypeDefinition>> entityExtensionsByNamespace =
        xtextGraph.getEntityExtensionsByNamespace();

    entityExtensionsByNamespace.keySet().stream()
        .flatMap(namespace -> entityExtensionsByNamespace.get(namespace).stream())
        .map(entityTypeExtension -> {
          String entityTypename = entityTypeExtension.getName();
          TypeDefinition entityBaseType = entitiesByTypename.get(entityTypename);
          if (Objects.isNull(entityBaseType)) {
            // TODO, add source namespace if possible sourceService=%s
            String errMsg = format("Basetype does not found.  typename=%s", entityTypename);
            throw new EntityExtensionException(errMsg);
          }
          return new EntityExtension(entityBaseType, entityTypeExtension);
        })
        .forEach(entityTypeMerger::mergeIntoBaseType);

    entitiesByTypename.values().forEach(Federation2PureGraphQLUtil::makeAsPureGraphQL);

    return xtextGraph;
  }

}
