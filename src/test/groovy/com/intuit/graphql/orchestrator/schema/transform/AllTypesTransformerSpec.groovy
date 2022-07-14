package com.intuit.graphql.orchestrator.schema.transform

import com.google.common.collect.ImmutableMap
import com.intuit.graphql.orchestrator.ServiceProvider
import com.intuit.graphql.orchestrator.TestServiceProvider
import com.intuit.graphql.orchestrator.schema.SchemaTransformationException
import com.intuit.graphql.orchestrator.xtext.XtextGraph
import com.intuit.graphql.orchestrator.xtext.XtextGraphBuilder
import spock.lang.Specification

class AllTypesTransformerSpec extends Specification {

    def "test Operation Types Are Filtered By Transformer"() {
        given:
        String schema = '''
            schema { query: QueryType } 
            type QueryType { a: A }
            type A { b: B } 
            type B {c: C} 
            type C { field1: String, field2: String }
        '''

        when:
        XtextGraph xtextGraph = XtextGraphBuilder
                .build(TestServiceProvider.newBuilder().namespace("SVC1").serviceType(ServiceProvider.ServiceType.GRAPHQL)
                        .sdlFiles(ImmutableMap.of("schema.graphqls", schema)).build());

        def transformed = new AllTypesTransformer().transform(xtextGraph);

        then:
        !transformed.getTypes().containsKey("QueryType")
        transformed.getTypes().size() == 3
        transformed.getTypeMetadatas().size() == 3
        transformed.getFieldCoordinates().size() == 5
    }

    def "test Throws Exception On Duplicate Type Definition"() {
        given:
        String schema = '''
            schema { query: QueryType } type QueryType { a: A } 
            type A { b: B } type A {c: C} type B {c: C} 
            type C { field1: String, field2: String }
        '''

        when:
        XtextGraph xtextGraph = XtextGraphBuilder
                .build(TestServiceProvider.newBuilder().namespace("SVC1").serviceType(ServiceProvider.ServiceType.GRAPHQL)
                        .sdlFiles(ImmutableMap.of("schema.graphqls", schema)).build());
        new AllTypesTransformer().transform(xtextGraph)

        then:
        def exception = thrown( SchemaTransformationException )
        exception.message.contains("Duplicate TypeDefinition")
        exception.message.contains("A")
        exception.message.contains("SVC1")
    }

}
