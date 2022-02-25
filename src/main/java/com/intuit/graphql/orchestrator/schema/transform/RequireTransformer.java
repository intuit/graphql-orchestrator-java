package com.intuit.graphql.orchestrator.schema.transform;

import com.google.common.annotations.VisibleForTesting;
import com.intuit.graphql.graphQL.Directive;
import com.intuit.graphql.graphQL.FieldDefinition;
import com.intuit.graphql.graphQL.InterfaceTypeDefinition;
import com.intuit.graphql.graphQL.ObjectTypeDefinition;
import com.intuit.graphql.graphQL.TypeDefinition;
import com.intuit.graphql.orchestrator.federation.exceptions.InvalidFieldSetReferenceException;
import com.intuit.graphql.orchestrator.federation.requiresdirective.RequireValidator;
import com.intuit.graphql.orchestrator.stitching.StitchingException;
import com.intuit.graphql.orchestrator.utils.XtextTypeUtils;
import com.intuit.graphql.orchestrator.xtext.XtextGraph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static com.intuit.graphql.orchestrator.utils.FederationUtils.FEDERATION_REQUIRES_DIRECTIVE;
import static com.intuit.graphql.orchestrator.utils.XtextTypeUtils.getDirectivesFromDefinition;
import static com.intuit.graphql.orchestrator.utils.XtextTypeUtils.getFieldDefinitions;

public class RequireTransformer implements Transformer<XtextGraph, XtextGraph> {

    @VisibleForTesting
    RequireValidator requireValidator = new RequireValidator();

    @Override
    public XtextGraph transform(final XtextGraph source) {

        if(source.getServiceProvider().isFederationProvider()) {
            source.getTypes().values().stream()
                    .peek(typeDefinition -> getFieldDefinitions(typeDefinition, true)
                            .stream()
                            .map(fieldDefinition -> getDirectivesFromDefinition(fieldDefinition, FEDERATION_REQUIRES_DIRECTIVE))
                            .flatMap(Collection::stream)
                            .peek(directive -> requireValidator.validate(source, typeDefinition, directive))
                            .count()
                    )
                    .count();
        }

        return source;
    }

}
