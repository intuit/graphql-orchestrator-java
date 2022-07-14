package com.intuit.graphql.orchestrator.schema.transform;

import static com.intuit.graphql.orchestrator.utils.FederationConstants.FEDERATION_EXTENDS_DIRECTIVE;
import static com.intuit.graphql.orchestrator.utils.FederationConstants.FEDERATION_KEY_DIRECTIVE;
import static com.intuit.graphql.orchestrator.utils.FederationUtils.isTypeSystemForBaseType;
import static com.intuit.graphql.orchestrator.utils.XtextTypeUtils.getFieldDefinitions;
import static com.intuit.graphql.orchestrator.utils.XtextTypeUtils.toDescriptiveString;
import static com.intuit.graphql.orchestrator.utils.XtextUtils.definitionContainsDirective;
import static com.intuit.graphql.orchestrator.utils.XtextUtils.getAllTypes;
import static com.intuit.graphql.orchestrator.utils.XtextUtils.getTypeSystemDefinition;
import static graphql.schema.FieldCoordinates.coordinates;

import com.google.common.collect.Streams;
import com.intuit.graphql.graphQL.EnumTypeDefinition;
import com.intuit.graphql.graphQL.FieldDefinition;
import com.intuit.graphql.graphQL.InterfaceTypeDefinition;
import com.intuit.graphql.graphQL.InterfaceTypeExtensionDefinition;
import com.intuit.graphql.graphQL.ObjectTypeDefinition;
import com.intuit.graphql.graphQL.ObjectTypeExtensionDefinition;
import com.intuit.graphql.graphQL.TypeDefinition;
import com.intuit.graphql.graphQL.TypeSystemDefinition;
import com.intuit.graphql.graphQL.UnionTypeDefinition;
import com.intuit.graphql.orchestrator.schema.Operation;
import com.intuit.graphql.orchestrator.schema.SchemaTransformationException;
import com.intuit.graphql.orchestrator.schema.TypeMetadata;
import com.intuit.graphql.orchestrator.utils.DescriptionUtils;
import com.intuit.graphql.orchestrator.utils.FederationUtils;
import com.intuit.graphql.orchestrator.xtext.XtextGraph;
import graphql.schema.FieldCoordinates;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;

public class AllTypesTransformer implements Transformer<XtextGraph, XtextGraph> {


  @Override
  public XtextGraph transform(XtextGraph source) {
    Map<String, TypeDefinition> types = getAllTypes(source.getXtextResourceSet())
        .filter(type -> !source.isOperationType(type))
        .filter(this::isNotEmpty)
        .collect(Collectors.toMap(TypeDefinition::getName, Function.identity(),
            (t1, t2) -> {
              throw new SchemaTransformationException(
                  String.format("Duplicate TypeDefinition %s in namespace: %s", toDescriptiveString(t1),
                      source.getServiceProvider().getNameSpace()));
            }));
    updateDescWithNamespace(types, source.getServiceProvider().getNameSpace());

    Map<FieldCoordinates, FieldDefinition> fieldCoordinates = collectFieldCoordinates(types);
    fieldCoordinates.putAll(collectOperationsFieldCoordinates(source.getOperationMap()));

    if (source.getServiceProvider().isFederationProvider()) {
      Map<String, TypeDefinition> baseEntities = getAllTypes(source.getXtextResourceSet())
          .filter(typeDefinition -> definitionContainsDirective(typeDefinition, FEDERATION_KEY_DIRECTIVE))
          .filter(FederationUtils::isBaseType)
          .collect(Collectors.toMap(TypeDefinition::getName, Function.identity()));

      Map<String, TypeSystemDefinition> extensionEntities = Streams.concat(
          getTypeSystemDefinition(source.getXtextResourceSet())
              .filter(FederationUtils::isTypeSystemForBaseType)
              .filter(typeSystemDefinition -> definitionContainsDirective(typeSystemDefinition.getType(),
                  FEDERATION_KEY_DIRECTIVE))
              .filter(typeSystemDefinition -> definitionContainsDirective(typeSystemDefinition.getType(),
                  FEDERATION_EXTENDS_DIRECTIVE)),
          getTypeSystemDefinition(source.getXtextResourceSet())
              .filter(FederationUtils::isTypeSystemForExtensionType)
              .filter(typeSystemDefinition ->
                  typeSystemDefinition.getTypeExtension() instanceof ObjectTypeExtensionDefinition
                      || typeSystemDefinition.getTypeExtension() instanceof InterfaceTypeExtensionDefinition)
              .filter(typeSystemDefinition -> definitionContainsDirective(typeSystemDefinition.getTypeExtension(),
                  FEDERATION_KEY_DIRECTIVE))
      ).collect(Collectors.toMap(
          (typeSystemDefinition -> isTypeSystemForBaseType(typeSystemDefinition) ? typeSystemDefinition.getType()
              .getName() : typeSystemDefinition.getTypeExtension().getName()),
          Function.identity())
      );

      HashMap<String, Map<String, TypeSystemDefinition>> extensionEntitiesByNamespace = new HashMap<>();
      extensionEntitiesByNamespace.put(source.getServiceProvider().getNameSpace(), extensionEntities);

      Map<String, TypeDefinition> valueTypes = types.values().stream()
          .filter(typeDefinition -> !definitionContainsDirective(typeDefinition, FEDERATION_KEY_DIRECTIVE))
          .collect(Collectors.toMap(TypeDefinition::getName, Function.identity()));

      return source.transform(builder -> builder
          .types(types)
          .typeMetadatas(createTypeMetadatas(types))
          .valueTypesByName(valueTypes)
          .entitiesByTypeName(baseEntities)
          .fieldCoordinates(fieldCoordinates)
          .entityExtensionsByNamespace(extensionEntitiesByNamespace)
      );
    }

    return source.transform(builder -> builder
        .types(types)
        .fieldCoordinates(fieldCoordinates)
        .typeMetadatas(createTypeMetadatas(types))
    );
  }

  private Map<String, TypeMetadata> createTypeMetadatas(Map<String, TypeDefinition> types) {
    Map<String, TypeMetadata> output = new HashMap<>();
    types.forEach((typename, typeDefinition) ->
        output.put(typename, new TypeMetadata(typeDefinition))
    );
    return output;
  }

  private boolean isNotEmpty(TypeDefinition typeDefinition) {
    if (typeDefinition instanceof ObjectTypeDefinition) {
      return CollectionUtils.isNotEmpty(((ObjectTypeDefinition) typeDefinition).getFieldDefinition());
    }
    if (typeDefinition instanceof InterfaceTypeDefinition) {
      return CollectionUtils.isNotEmpty(((InterfaceTypeDefinition) typeDefinition).getFieldDefinition());
    }
    if (typeDefinition instanceof UnionTypeDefinition) {
      return Objects.nonNull(((UnionTypeDefinition) typeDefinition).getUnionMemberShip());
    }
    if (typeDefinition instanceof EnumTypeDefinition) {
      return CollectionUtils.isNotEmpty(((EnumTypeDefinition) typeDefinition).getEnumValueDefinition());
    }
    return true;
  }

  private void updateDescWithNamespace(Map<String, TypeDefinition> types, String namespace) {
    types.forEach((k, v) -> {
      v.setDesc(DescriptionUtils.attachNamespace(namespace, v.getDesc()));
    });
  }

  private Map<FieldCoordinates, FieldDefinition> collectOperationsFieldCoordinates(
      Map<Operation, ObjectTypeDefinition> operationMap) {
    Map<String, TypeDefinition> operationTypeMap = operationMap.values().stream()
        .collect(Collectors.toMap(ObjectTypeDefinition::getName, Function.identity()));
    return collectFieldCoordinates(operationTypeMap);
  }

  private Map<FieldCoordinates, FieldDefinition> collectFieldCoordinates(Map<String, TypeDefinition> types) {
    Map<FieldCoordinates, FieldDefinition> output = new HashMap<>();
    types.forEach( (typeName, typeDefinition) ->
        getFieldDefinitions(typeDefinition, true)
            .forEach(fieldDefinition ->
                    output.put(coordinates(typeName, fieldDefinition.getName()), fieldDefinition)
            )
    );
    return output;
  }
}
