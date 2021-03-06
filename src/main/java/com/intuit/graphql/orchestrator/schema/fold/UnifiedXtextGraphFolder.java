package com.intuit.graphql.orchestrator.schema.fold;

import static com.intuit.graphql.orchestrator.schema.fold.FieldMergeValidations.checkMergeEligibility;
import static com.intuit.graphql.orchestrator.utils.DescriptionUtils.mergeDescriptions;
import static com.intuit.graphql.orchestrator.utils.FederationConstants.FEDERATION_INACCESSIBLE_DIRECTIVE;
import static com.intuit.graphql.orchestrator.utils.FederationUtils.isBaseType;
import static com.intuit.graphql.orchestrator.utils.FederationUtils.isEntityExtensionType;
import static com.intuit.graphql.orchestrator.utils.TypeReferenceUtil.updateTypeReferencesInObjectType;
import static com.intuit.graphql.orchestrator.utils.XtextTypeUtils.isEntity;
import static com.intuit.graphql.orchestrator.utils.XtextTypeUtils.objectTypeContainsFieldWithName;
import static com.intuit.graphql.orchestrator.utils.XtextUtils.getDirectiveWithNameFromDefinition;
import static com.intuit.graphql.orchestrator.xtext.DataFetcherContext.STATIC_DATAFETCHER_CONTEXT;
import static com.intuit.graphql.orchestrator.xtext.GraphQLFactoryDelegate.createObjectType;
import static com.intuit.graphql.utils.XtextTypeUtils.typeName;
import static com.intuit.graphql.utils.XtextTypeUtils.unwrapAll;

import com.intuit.graphql.graphQL.EnumTypeDefinition;
import com.intuit.graphql.graphQL.EnumValueDefinition;
import com.intuit.graphql.graphQL.FieldDefinition;
import com.intuit.graphql.graphQL.InputObjectTypeDefinition;
import com.intuit.graphql.graphQL.InputValueDefinition;
import com.intuit.graphql.graphQL.InterfaceTypeDefinition;
import com.intuit.graphql.graphQL.NamedType;
import com.intuit.graphql.graphQL.ObjectType;
import com.intuit.graphql.graphQL.ObjectTypeDefinition;
import com.intuit.graphql.graphQL.TypeDefinition;
import com.intuit.graphql.orchestrator.ServiceProvider;
import com.intuit.graphql.orchestrator.schema.Operation;
import com.intuit.graphql.orchestrator.schema.type.conflict.resolver.TypeConflictException;
import com.intuit.graphql.orchestrator.schema.type.conflict.resolver.XtextTypeConflictResolver;
import com.intuit.graphql.orchestrator.stitching.StitchingException;
import com.intuit.graphql.orchestrator.xtext.DataFetcherContext;
import com.intuit.graphql.orchestrator.xtext.DataFetcherContext.DataFetcherType;
import com.intuit.graphql.orchestrator.xtext.FieldContext;
import com.intuit.graphql.orchestrator.xtext.UnifiedXtextGraph;
import com.intuit.graphql.orchestrator.xtext.XtextGraph;
import com.intuit.graphql.utils.XtextTypeUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.util.EcoreUtil;

public class UnifiedXtextGraphFolder implements Foldable<XtextGraph, UnifiedXtextGraph> {

  private Map<FieldContext, DataFetcherContext> accCodeRegistry;
  private Map<String, TypeDefinition> nestedTypes;

  @Override
  public UnifiedXtextGraph fold(UnifiedXtextGraph initVal, Collection<XtextGraph> list) {
    UnifiedXtextGraph accumulator = initVal;
    this.accCodeRegistry = initVal.getCodeRegistry();

    for (XtextGraph current : list) {
      accumulator = merge(accumulator, current);
    }
    return accumulator;
  }

  /**
   * Merge runtime graph.
   *
   * @param accumulator the current graph
   * @param current the new comer graph
   * @return the runtime graph
   */
  private UnifiedXtextGraph merge(UnifiedXtextGraph accumulator, XtextGraph current) {

    this.nestedTypes = new HashMap<>(); // To be re-initialized for every merge
    Map<Operation, ObjectTypeDefinition> newOperationMap = new EnumMap<>(Operation.class);
    for (Operation op : Operation.values()) {
      newOperationMap.put(
          op,
          merge(
              accumulator.getOperationType(op),
              current.getOperationType(op),
              current.getServiceProvider()));
    }

    if (current.getServiceProvider().isFederationProvider()) {
      current
          .getValueTypesByName()
          .values()
          .forEach(
              incomingSharedType -> {
                TypeDefinition preexistingTypeDefinition =
                    accumulator.getType(incomingSharedType.getName());
                if (incomingSharedType instanceof EnumTypeDefinition) {
                  mergeSharedValueType(
                      (EnumTypeDefinition) preexistingTypeDefinition,
                      (EnumTypeDefinition) incomingSharedType,
                      current.getServiceProvider());
                } else if (incomingSharedType instanceof ObjectTypeDefinition) {
                  mergeSharedValueType(
                      (ObjectTypeDefinition) preexistingTypeDefinition,
                      (ObjectTypeDefinition) incomingSharedType,
                      current.getServiceProvider());
                } else if (incomingSharedType instanceof InterfaceTypeDefinition) {
                  mergeSharedValueType(
                      accumulator,
                      (InterfaceTypeDefinition) preexistingTypeDefinition,
                      (InterfaceTypeDefinition) incomingSharedType,
                      current.getServiceProvider());
                }
              }
          );
    }

    resolveTypeConflicts(accumulator, current);

    // transform the current graph with the new operation and code registry builder
    return accumulator.transform(
        builder -> {
          builder.operationMap(newOperationMap);
          builder.codeRegistry(this.accCodeRegistry);
          builder.types(current.getTypes());
          builder.typeMetadatas(current.getTypeMetadatas());
          builder.types(nestedTypes);
          builder.directives(current.getDirectives());
          builder.fieldResolverContexts(current.getFieldResolverContexts());
          builder.valueTypesByName(current.getValueTypesByName());
          builder.entityExtensionMetadatas(current.getEntityExtensionMetadatas());
          builder.entitiesByTypeName(current.getEntitiesByTypeName());
          builder.entityExtensionsByNamespace(current.getEntityExtensionsByNamespace());
          builder.federationMetadataByNamespace(current.getFederationMetadataByNamespace());
          builder.renamedMetadataByNamespace(current.getRenamedMetadataByNamespace());
        });
  }

  private void resolveTypeConflicts(
      final UnifiedXtextGraph accumulator,
      final XtextGraph currentGraph)
      throws TypeConflictException {

    final Map<String, TypeDefinition> existing = accumulator.getTypes();
    final Map<String, TypeDefinition> current = currentGraph.getTypes();

    final Set<String> operationTypeNames =
        currentGraph.getOperationMap().values().stream()
            .map(TypeDefinition::getName)
            .collect(Collectors.toSet());

    for (String typeName : current.keySet()) {
      // ignore type resolution for Query, Mutation, or Subscription types.
      if (operationTypeNames.contains(typeName)) {
        continue;
      }

      TypeDefinition existingType = existing.get(typeName);
      if (Objects.nonNull(existingType) && !nestedTypes.containsKey(existingType.getName())) {
        TypeDefinition conflictingType = current.get(typeName);
        XtextTypeConflictResolver.INSTANCE.resolve(
            conflictingType,
            existingType,
            currentGraph.getServiceProvider().isFederationProvider());
        mergeSharedType(existingType, conflictingType, accumulator, currentGraph);
      }
    }
  }

  private void mergeSharedType(TypeDefinition existingType, TypeDefinition incomingType,
      UnifiedXtextGraph accumulator, XtextGraph incomingXtextGraph) {
    boolean isFederated = incomingXtextGraph.getServiceProvider().isFederationProvider();
    if (isFederated && isEntity(existingType) && isEntity(incomingType)) {
      mergeEntityTypes(existingType, incomingType, accumulator, incomingXtextGraph);
    }
  }

  private void mergeEntityTypes(TypeDefinition existingType, TypeDefinition incomingType,
      UnifiedXtextGraph accumulator, XtextGraph incomingXtextGraph) {
    /*
      Accumulator contains some fields that refer to Entity Base type and some to Entity
      Extension.  The goal is to have all fields refer to the Base Type since fields of
      Entity extension will be merged to the Base Type.

      Either the base type or extension type may come first in the accumulator.  The reason
      for the if else below.
     */
    if(isBaseType(existingType) && isEntityExtensionType(incomingType)) {
      incomingXtextGraph.getTypes().put(existingType.getName(), existingType);
      updateTypeReferencesInUnifiedXtextGraph(incomingType, existingType, accumulator);
    } else if (isBaseType(incomingType) && isEntityExtensionType(existingType)) {
      accumulator.getTypes().put(incomingType.getName(), incomingType);
      updateTypeReferencesInUnifiedXtextGraph(existingType, incomingType, accumulator);
    } // else do nothing
  }

  private void updateTypeReferencesInUnifiedXtextGraph(TypeDefinition targetType,
      TypeDefinition replacementType, UnifiedXtextGraph unifiedXtextGraph) {
    unifiedXtextGraph
        .getOperationMap()
        .forEach(
            (operation, objectTypeDefinition) -> {
              updateTypeReferencesInObjectType(targetType, replacementType, objectTypeDefinition);
            });
  }

  /**
   * Merge the two graphql objects.
   *
   * @return merged object
   */
  private ObjectTypeDefinition merge(
      ObjectTypeDefinition current,
      ObjectTypeDefinition newComer,
      ServiceProvider newComerServiceProvider) {
    // nothing to merge
    if (newComer == null) {
      return current;
    }
    // transform the current to add the new types
    String parentType = current.getName();

    newComer
        .getFieldDefinition()
        .forEach(
            newField -> {
              Optional<FieldDefinition> currentField =
                  current.getFieldDefinition().stream()
                      .filter(ec -> newField.getName().equals(ec.getName()))
                      .findFirst();

              if (currentField.isPresent()) {
                checkMergeEligibility(parentType, currentField.get(), newField);

                // XtextNestedTypeConflictResolver.INSTANCE.resolve(newField.getNamedType(),
                // currentField.get().getNamedType());

                // nested merge begins
                current.getFieldDefinition().remove(currentField.get());
                FieldDefinition nestedField =
                    merge(parentType, currentField.get(), newField, newComerServiceProvider);
                current.getFieldDefinition().add(nestedField);

                collectNestedTypes(nestedField);
              } else {
                addNewFieldToObject(current, newField, newComerServiceProvider);
              }
            });

    current.setDesc(mergeDescriptions(current.getDesc(), newComer.getDesc()));

    return current;
  }

  /**
   * Merge the two graphql objects.
   *
   * @return merged object
   */
  private TypeDefinition mergeSharedValueType(
      EnumTypeDefinition current,
      EnumTypeDefinition newComer,
      ServiceProvider newComerServiceProvider) {
    // nothing to merge
    if (current == null || !newComerServiceProvider.isFederationProvider()) {
      return current;
    }
    // transform the current to add the new types
    newComer
        .getEnumValueDefinition()
        .forEach(
            enumValue -> {
              Optional<EnumValueDefinition> currentEnum =
                  current.getEnumValueDefinition().stream()
                      .filter(ec -> enumValue.getEnumValue().equals(ec.getEnumValue()))
                      .findFirst();

              if (!currentEnum.isPresent()) {
                addNewFieldToObject(current, enumValue, newComerServiceProvider);
              }
            });

    current.setDesc(mergeDescriptions(current.getDesc(), newComer.getDesc()));

    return current;
  }

  private TypeDefinition mergeSharedValueType(
      ObjectTypeDefinition current,
      ObjectTypeDefinition newComer,
      ServiceProvider newComerServiceProvider) {
    if (current == null || !newComerServiceProvider.isFederationProvider()) {
      return current;
    }

    newComer
        .getFieldDefinition()
        .forEach(
            newField -> {
              Optional<FieldDefinition> currentField =
                  current.getFieldDefinition().stream()
                      .filter(fieldName -> newField.getName().equals(fieldName.getName()))
                      .findFirst();

                if (!currentField.isPresent()) {
                    addNewFieldToObject(current, newField, newComerServiceProvider);
                } else {
                    mergeInaccessibleDirectiveToField(newField, currentField.get());
                }
            }
        );

    current.setDesc(mergeDescriptions(current.getDesc(), newComer.getDesc()));

    return current;
  }

  private TypeDefinition mergeSharedValueType(
      UnifiedXtextGraph prexistingInfo,
      InterfaceTypeDefinition current,
      InterfaceTypeDefinition newComer,
      ServiceProvider newComerServiceProvider) {
    if (current == null || !newComerServiceProvider.isFederationProvider()) {
      return current;
    }

    newComer
        .getFieldDefinition()
        .forEach(
            newField -> {
              Optional<FieldDefinition> currentField =
                  current.getFieldDefinition().stream()
                      .filter(fieldName -> newField.getName().equals(fieldName.getName()))
                      .findFirst();

                if (!currentField.isPresent()) {
                    List<String> implementingTypesNotContainingField = (prexistingInfo.getTypes() != null) ? prexistingInfo.getTypes()
                            .values()
                            .stream()
                            .filter(ObjectTypeDefinition.class::isInstance)
                            .map(ObjectTypeDefinition.class::cast)
                            .filter(objectTypeDefinition -> objectTypeImplementsInterface(objectTypeDefinition, newComer))
                            .filter(implementingObjectType -> !objectTypeContainsFieldWithName(implementingObjectType, newField.getName()))
                            .map(ObjectTypeDefinition::getName)
                            .collect(Collectors.toList()) : new ArrayList<>();

                    //Based on federation spec, new fields for interfaces must be resolvable by all types implementing to be valid new fields
                    if(!implementingTypesNotContainingField.isEmpty()) {
                        throw new StitchingException( String.format(
                                "Implementing types %s do not contain the new field %s from interface %s",
                                StringUtils.join(implementingTypesNotContainingField, ","),
                                newField.getName(),
                                newComer.getName()
                        )
                        );
                    } else {
                        addNewFieldToObject(current, newField, newComerServiceProvider);
                    }
                } else {
                    mergeInaccessibleDirectiveToField(newField, currentField.get());
                }
            });

    current.setDesc(mergeDescriptions(current.getDesc(), newComer.getDesc()));

    return current;
  }

  private boolean objectTypeImplementsInterface(ObjectTypeDefinition typeDefinition, InterfaceTypeDefinition interfaceDefinition) {
      return typeDefinition.getImplementsInterfaces().getNamedType()
                .stream().anyMatch(namedType -> typeName(namedType).equals(interfaceDefinition.getName()));
  }

  /**
   * Store the type of a nested field, and all types of its input arguments.
   *
   * @param nestedField a nested field
   */
  private void collectNestedTypes(FieldDefinition nestedField) {
    TypeDefinition type = XtextTypeUtils.getObjectType(nestedField.getNamedType());
    nestedTypes.put(type.getName(), type);

    if (Objects.nonNull(nestedField.getArgumentsDefinition())) {
      // collect argument types
      collectNestedInputTypes(nestedField.getArgumentsDefinition().getInputValueDefinition());
    }
  }

  /**
   * Recursively traverse input arguments of a field (which is nested) and store all the input types
   * as nested.
   *
   * @param inputs the input arguments of a nested field
   */
  private void collectNestedInputTypes(EList<InputValueDefinition> inputs) {
    inputs.stream()
        .forEach(
            inputValueDefinition -> {
              NamedType namedType = unwrapAll(inputValueDefinition.getNamedType());
              if (com.intuit.graphql.orchestrator.utils.XtextTypeUtils.isObjectType(namedType)) {
                InputObjectTypeDefinition inputObjectTypeDefinition =
                    (InputObjectTypeDefinition) XtextTypeUtils.getObjectType(namedType);
                if (!nestedTypes.containsKey(inputObjectTypeDefinition.getName())) {
                  nestedTypes.put(inputObjectTypeDefinition.getName(), inputObjectTypeDefinition);
                  collectNestedInputTypes(inputObjectTypeDefinition.getInputValueDefinition());
                }
              }
            });
  }

  private void addNewFieldToObject(
      ObjectTypeDefinition objectTypeDefinition,
      FieldDefinition fieldDefinition,
      ServiceProvider serviceProvider) {
    addFieldContextToRegistry(
        objectTypeDefinition.getName(), fieldDefinition.getName(), serviceProvider);
    objectTypeDefinition.getFieldDefinition().add(EcoreUtil.copy(fieldDefinition));
  }

  private void addNewFieldToObject(
      EnumTypeDefinition enumTypeDefinition,
      EnumValueDefinition valueDefinition,
      ServiceProvider serviceProvider) {
    addFieldContextToRegistry(
        enumTypeDefinition.getName(), valueDefinition.getEnumValue(), serviceProvider);
    enumTypeDefinition.getEnumValueDefinition().add(EcoreUtil.copy(valueDefinition));
  }

  private void addNewFieldToObject(
      InterfaceTypeDefinition interfaceTypeDefinition,
      FieldDefinition fieldDefinition,
      ServiceProvider serviceProvider) {
    addFieldContextToRegistry(
        interfaceTypeDefinition.getName(), fieldDefinition.getName(), serviceProvider);
    interfaceTypeDefinition.getFieldDefinition().add(EcoreUtil.copy(fieldDefinition));
  }

  private void addFieldContextToRegistry(
      String parentName, String definitionName, ServiceProvider serviceProvider) {
    final FieldContext fieldContext = new FieldContext(parentName, definitionName);

    accCodeRegistry.put(
        fieldContext,
        DataFetcherContext.newBuilder()
            .namespace(serviceProvider.getNameSpace())
            .serviceType(serviceProvider.getSeviceType())
            .build());
  }

  /**
   * Merge the two field definitions
   *
   * @return merged field
   */
  private FieldDefinition merge(
      String parentType,
      FieldDefinition current,
      FieldDefinition newComer,
      ServiceProvider newComerServiceProvider) {

    // Do only if both fields are objects. might support other types in the future.
    final ObjectTypeDefinition currentType =
        (ObjectTypeDefinition) ((ObjectType) current.getNamedType()).getType();
    final ObjectTypeDefinition newComerType =
        (ObjectTypeDefinition) ((ObjectType) newComer.getNamedType()).getType();

    // Get datafetcher of current field
    DataFetcherContext dfContext =
        accCodeRegistry.get(new FieldContext(parentType, current.getName()));
    if (dfContext.getDataFetcherType() != DataFetcherType.STATIC) {
      accCodeRegistry.put(
          new FieldContext(parentType, current.getName()), STATIC_DATAFETCHER_CONTEXT);
      copyParentDataFetcher(currentType, dfContext);
    }

    // recurse for next level merging
    FieldDefinition copyCurrent = EcoreUtil.copy(current);

    ObjectType objectType = createObjectType();
    objectType.setType(merge(currentType, newComerType, newComerServiceProvider));
    copyCurrent.setNamedType(objectType);
    return copyCurrent;
  }

    private void mergeInaccessibleDirectiveToField(FieldDefinition newFieldDef, FieldDefinition preexisitingFieldDef) {
        getDirectiveWithNameFromDefinition(newFieldDef, FEDERATION_INACCESSIBLE_DIRECTIVE).ifPresent(
                newInaccessibleDirective -> {
                    if(!getDirectiveWithNameFromDefinition(preexisitingFieldDef, FEDERATION_INACCESSIBLE_DIRECTIVE).isPresent()) {
                        preexisitingFieldDef.getDirectives().add(newInaccessibleDirective);
                    }
                }
        );
    }

    private void copyParentDataFetcher(
      ObjectTypeDefinition objectTypeDefinition, DataFetcherContext dataFetcherContext) {
    objectTypeDefinition
        .getFieldDefinition()
        .forEach(
            fieldDefinition ->
                accCodeRegistry.put(
                    new FieldContext(objectTypeDefinition.getName(), fieldDefinition.getName()),
                    dataFetcherContext));
  }
}
