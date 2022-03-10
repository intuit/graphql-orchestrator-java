package com.intuit.graphql.orchestrator.utils;

import static com.intuit.graphql.orchestrator.utils.FederationConstants.FEDERATION_EXTENDS_DIRECTIVE;
import static com.intuit.graphql.orchestrator.utils.FederationConstants.FED_FIELD_DIRECTIVE_NAMES_SET;
import static com.intuit.graphql.orchestrator.utils.FederationConstants.FED_TYPE_DIRECTIVES_NAMES_SET;
import static com.intuit.graphql.orchestrator.utils.XtextTypeUtils.getFieldDefinitions;

import com.intuit.graphql.graphQL.Directive;
import com.intuit.graphql.graphQL.FieldDefinition;
import com.intuit.graphql.graphQL.InterfaceTypeExtensionDefinition;
import com.intuit.graphql.graphQL.ObjectTypeExtensionDefinition;
import com.intuit.graphql.graphQL.TypeDefinition;
import com.intuit.graphql.orchestrator.federation.exceptions.EmptyFieldsArgumentFederationDirective;
import com.intuit.graphql.orchestrator.federation.exceptions.InvalidFieldSetReferenceException;
import com.intuit.graphql.orchestrator.xtext.XtextGraph;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.language.SelectionSet;
import graphql.parser.Parser;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

public class FederationUtils {
    private FederationUtils(){}

    public static boolean isFederationDirective(Directive directive) {
        String directiveName = directive.getDefinition().getName();
        return FED_TYPE_DIRECTIVES_NAMES_SET.contains(directiveName)
            || FED_FIELD_DIRECTIVE_NAMES_SET.contains(directiveName);
    }

    public static boolean isBaseType(TypeDefinition typeDefinition) {
        return !(typeDefinition instanceof ObjectTypeExtensionDefinition || typeDefinition instanceof InterfaceTypeExtensionDefinition) &&
                typeDefinition.getDirectives().stream()
                .map(directive -> directive.getDefinition().getName())
                .noneMatch(name -> StringUtils.equals(FEDERATION_EXTENDS_DIRECTIVE, name));
    }

    public static String getUniqueIdFromFieldSet(String fieldSet) {
        if(!fieldSet.startsWith("{")) {
            fieldSet = StringUtils.join(StringUtils.SPACE, "{", fieldSet, "}");
        }

        Document graphqlDocument = Parser.parse(fieldSet);

        List<OperationDefinition> definitions = graphqlDocument.getDefinitions().stream()
                .map(OperationDefinition.class::cast).collect(Collectors.toList());

        return convertSelectionSetToUniqueId(definitions.get(0).getSelectionSet());
    }

    private static String convertSelectionSetToUniqueId(SelectionSet selectionSet) {
        Comparator<Field> fieldComparable = Comparator.comparing(Field::getName);

        List<Field> fields = selectionSet.getSelections().stream()
                .map(Field.class::cast).sorted(fieldComparable).collect(Collectors.toList());

        String directChildrenUniqueId = fields.stream().map(Field::getName).reduce("", (partialId, fieldName) -> partialId + fieldName);
        String descendantsUniqueId = fields.stream().filter(field -> CollectionUtils.isNotEmpty(field.getChildren())).map(field -> {
            SelectionSet childSelections =  field.getChildren().stream().map(SelectionSet.class::cast).collect(Collectors.toList()).get(0);
            return convertSelectionSetToUniqueId(childSelections);
        }).reduce("", (partialId, childSelectionNames) -> partialId + childSelectionNames);

        return directChildrenUniqueId + descendantsUniqueId;
    }
}