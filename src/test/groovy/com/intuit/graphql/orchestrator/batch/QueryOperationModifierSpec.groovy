package com.intuit.graphql.orchestrator.batch

import org.junit.Ignore
import spock.lang.Specification

import static com.intuit.graphql.orchestrator.TestHelper.fragmentDefinitions
import static com.intuit.graphql.orchestrator.TestHelper.schema

import com.intuit.graphql.orchestrator.TestHelper
import graphql.language.FragmentDefinition
import graphql.language.OperationDefinition
import graphql.schema.GraphQLSchema
import graphql.schema.idl.RuntimeWiring

class QueryOperationModifierSpec extends Specification {

    private String schema = '''
        type Query {
          foo: Foo
          complexFoo: [Foo!]!
          fooUnion: Union
        }

        interface Foo {
          a: String
        }

        type Bar implements Foo {
          a: String
          b: String
        }

        union Union = Bar
    '''

    private RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
            .type("Foo", { wiring -> wiring.typeResolver({ env -> env.getSchema().getObjectType("Bar") }) })
            .type("Union", { wiring -> wiring.typeResolver({ env -> env.getSchema().getObjectType("Bar") }) })
            .build()

    private GraphQLSchema graphQLSchema

    def setup() {
        graphQLSchema = schema(schema, runtimeWiring)
    }

    def "adds Typename To Query"() {
        given:
        final String query = '''
            {
                foo {
                    a
                }
            }
        '''
        final OperationDefinition queryOperation = TestHelper.query(query)

        QueryOperationModifier queryOperationModifier = new QueryOperationModifier()

        when:
        final OperationDefinition definition = queryOperationModifier
                .modifyQuery(graphQLSchema, queryOperation, Collections.emptyMap(), Collections.emptyMap())

        final List<String> preOrderResult = GraphQLTestUtil
                .printPreOrder(definition, graphQLSchema, Collections.emptyMap())

        then:
        preOrderResult == ["foo", "a", "__typename"]
    }

    def "adds Typename To Query For Complex Objects"() {
        given:
        final String query = '''
            {
                complexFoo {
                    a
                }
            }
        '''

        final OperationDefinition queryOperation = TestHelper.query(query)

        QueryOperationModifier queryOperationModifier = new QueryOperationModifier()

        final OperationDefinition definition = queryOperationModifier
                .modifyQuery(graphQLSchema, queryOperation, Collections.emptyMap(), Collections.emptyMap())

        when:
        final List<String> preOrderResult = GraphQLTestUtil
                .printPreOrder(definition, graphQLSchema, Collections.emptyMap())

        then:
        preOrderResult == ["complexFoo", "a", "__typename"]
    }

    def "does Not Modify Query With Typename"() {
        given:
        final String queryWithTypename = '''
            {
                foo {
                    a
                    __typename
                }
            }
        '''

        final OperationDefinition queryOperation = TestHelper.query(queryWithTypename)

        QueryOperationModifier queryOperationModifier = new QueryOperationModifier()

        final OperationDefinition definition = queryOperationModifier
                    .modifyQuery(graphQLSchema, queryOperation, Collections.emptyMap(), Collections.emptyMap())

        when:
        final List<String> preOrderResult = GraphQLTestUtil
                .printPreOrder(definition, graphQLSchema, Collections.emptyMap())

        then:
        preOrderResult == ["foo", "a", "__typename"]
    }

    def "adds Typename To Union"() {
        given:
        final String queryWithUnion = "{ fooUnion { ...on Bar { b } } }"

        final OperationDefinition queryOperation = TestHelper.query(queryWithUnion)

        final QueryOperationModifier queryOperationModifier = new QueryOperationModifier()

        final OperationDefinition definition = queryOperationModifier
                .modifyQuery(graphQLSchema, queryOperation, Collections.emptyMap(), Collections.emptyMap())

        when:
        final List<String> preOrderResult = GraphQLTestUtil
                .printPreOrder(definition, graphQLSchema, Collections.emptyMap())

        then:
        preOrderResult == ["fooUnion", "inline:b", "inline:__typename"]
    }

    @Ignore("to be done later")
    def "adds Typename To Query In Fragment Definition"() {
        given:
        final String queryWithFragment = '''
            query {
                foo {
                    ...FooFragment
                }
            }

            fragment FooFragment on Foo {
                a
            }
        '''

        final OperationDefinition queryOperation = TestHelper.query(queryWithFragment)
        final Map<String, FragmentDefinition> fragmentsByName = fragmentDefinitions(queryWithFragment)

        final QueryOperationModifier queryOperationModifier = new QueryOperationModifier()

        when:
        final OperationDefinition definition = queryOperationModifier
                .modifyQuery(graphQLSchema, queryOperation, fragmentsByName, Collections.emptyMap())

        then:
        noExceptionThrown()
    }

}
