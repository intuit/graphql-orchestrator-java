package com.intuit.graphql.orchestrator.resolverdirective

import spock.lang.Specification

import static com.intuit.graphql.orchestrator.resolverdirective.ResolverDirectiveDefinitionTestUtil.createResolverArguments
import static com.intuit.graphql.orchestrator.resolverdirective.ResolverDirectiveDefinitionTestUtil.createResolverField

import com.intuit.graphql.graphQL.Argument
import com.intuit.graphql.graphQL.Directive
import com.intuit.graphql.graphQL.FieldDefinition
import com.intuit.graphql.graphQL.ObjectType
import com.intuit.graphql.graphQL.ObjectTypeDefinition
import com.intuit.graphql.orchestrator.xtext.GraphQLFactoryDelegate
import org.apache.commons.lang3.StringUtils

class ResolverDirectiveDefinitionSpec extends Specification {

    private static final String TEST_RESOLVER_FIELDNAME = "resolverField"
    private static final String TEST_RESOLVER_ARGUMENT_NAME1 = "testFieldArg1"
    private static final String TEST_RESOLVER_ARGUMENT_VALUE1 = '$testFieldValue1'
    private static final String TEST_RESOLVER_ARGUMENT_NAME2 = "testFieldArg1"
    private static final String TEST_RESOLVER_ARGUMENT_VALUE2 = '$testFieldValue2'

    private ResolverDirectiveDefinition subjectUnderTest

    def setup() {
        Argument resolverField = createResolverField(TEST_RESOLVER_FIELDNAME)
        Argument resolverArguments = createResolverArguments(TEST_RESOLVER_ARGUMENT_NAME1,
                TEST_RESOLVER_ARGUMENT_VALUE1,
                TEST_RESOLVER_ARGUMENT_NAME2, TEST_RESOLVER_ARGUMENT_VALUE2)

        Directive directive = GraphQLFactoryDelegate.createDirective()
        directive.getArguments().add(resolverField)
        directive.getArguments().add(resolverArguments)

        subjectUnderTest = ResolverDirectiveDefinition.from(directive)
    }

    def "can Create Resolver Directive Definition Test"() {
        given:
        ResolverArgumentDefinition resolverArgumentEntry1 = subjectUnderTest.getArguments().get(0)
        ResolverArgumentDefinition resolverArgumentEntry2 = subjectUnderTest.getArguments().get(1)

        expect:
        subjectUnderTest.getField() == TEST_RESOLVER_FIELDNAME

        resolverArgumentEntry1.getName() == TEST_RESOLVER_ARGUMENT_NAME1
        resolverArgumentEntry1.getValue() == TEST_RESOLVER_ARGUMENT_VALUE1

        resolverArgumentEntry2.getName() == TEST_RESOLVER_ARGUMENT_NAME2
        resolverArgumentEntry2.getValue() == TEST_RESOLVER_ARGUMENT_VALUE2
    }

    def "unexpected Argument For Resolver Directive Definition Test"() {
        given:
        // FootType has testField.  testField has @resolver(field: "resolverField", argument: [...]])
        // argument is not valid.
        Argument resolverArguments = GraphQLFactoryDelegate.createArgument()
        resolverArguments.setName("argument"); // should be arguments

        Argument resolverField = createResolverField(TEST_RESOLVER_FIELDNAME)

        Directive directive = GraphQLFactoryDelegate.createDirective()
        directive.getArguments().add(resolverField)
        directive.getArguments().add(resolverArguments)

        // container
        ObjectTypeDefinition fooObjType = GraphQLFactoryDelegate.createObjectTypeDefinition()
        fooObjType.setName("FooType")

        ObjectType objectType = GraphQLFactoryDelegate.createObjectType()
        objectType.setType(fooObjType)

        FieldDefinition fieldDefinition = GraphQLFactoryDelegate.createFieldDefinition()
        fieldDefinition.setName(TEST_RESOLVER_FIELDNAME)
        fieldDefinition.setNamedType(objectType)
        fieldDefinition.getDirectives().add(directive)

        when:
        ResolverDirectiveDefinition.from(directive)

        then:
        thrown(ResolverDirectiveException)
    }

    def "unexpected Field For Resolver Directive Definition Test"() {
        given:
        String resolverFieldName = StringUtils.EMPTY; // cannot be empty

        Argument resolverField = createResolverField(resolverFieldName)
        Argument resolverArguments = createResolverArguments(TEST_RESOLVER_ARGUMENT_NAME1,
                TEST_RESOLVER_ARGUMENT_VALUE1,
                TEST_RESOLVER_ARGUMENT_NAME2, TEST_RESOLVER_ARGUMENT_VALUE2)

        Directive directive = GraphQLFactoryDelegate.createDirective()
        directive.getArguments().add(resolverField)
        directive.getArguments().add(resolverArguments)

        // container
        ObjectTypeDefinition fooObjType = GraphQLFactoryDelegate.createObjectTypeDefinition()
        fooObjType.setName("FooType")

        ObjectType nonNullType = GraphQLFactoryDelegate.createObjectType()
        nonNullType.setType(fooObjType)

        FieldDefinition fieldDefinition = GraphQLFactoryDelegate.createFieldDefinition()
        fieldDefinition.setName(TEST_RESOLVER_FIELDNAME)
        fieldDefinition.setNamedType(nonNullType)
        fieldDefinition.getDirectives().add(directive)

        when:
        ResolverDirectiveDefinition.from(directive)

        then:
        thrown(ResolverDirectiveException)
    }

    def "null Directive Test"() {
        when:
        ResolverDirectiveDefinition.from(null)

        then:
        thrown(NullPointerException)
    }

    def "extract Required Fields From"() {
        given:
        Set<String> actual = ResolverDirectiveDefinition.extractRequiredFieldsFrom(subjectUnderTest)

        expect:
        actual.size() == 2
        actual.contains("testFieldValue1")
        actual.contains("testFieldValue2")
    }
}
