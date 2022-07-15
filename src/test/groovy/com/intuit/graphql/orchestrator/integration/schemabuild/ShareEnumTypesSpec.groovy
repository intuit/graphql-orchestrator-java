package com.intuit.graphql.orchestrator.integration.schemabuild

import com.intuit.graphql.orchestrator.ServiceProvider
import com.intuit.graphql.orchestrator.schema.RuntimeGraph
import com.intuit.graphql.orchestrator.schema.type.conflict.resolver.TypeConflictException
import com.intuit.graphql.orchestrator.stitching.SchemaStitcher
import graphql.schema.GraphQLEnumType
import helpers.BaseIntegrationTestSpecification

class ShareEnumTypesSpec extends BaseIntegrationTestSpecification {

    static String SCHEMA_1 = '''
        directive @dataClassification(level: EnumType!) on SCHEMA | SCALAR | OBJECT | FIELD_DEFINITION | ARGUMENT_DEFINITION | INTERFACE | UNION | ENUM | ENUM_VALUE | INPUT_OBJECT | INPUT_FIELD_DEFINITION

        type Query {
            fakeQuery: TestObject
        }
        
        type TestObject {
            field1: ID! @dataClassification(level : ENUM1)
        }
        
        enum EnumType {
            ENUM1
        }
    '''

    static String SCHEMA_2 = '''
        directive @dataClassification(level: EnumType!) on SCHEMA | SCALAR | OBJECT | FIELD_DEFINITION | ARGUMENT_DEFINITION | INTERFACE | UNION | ENUM | ENUM_VALUE | INPUT_OBJECT | INPUT_FIELD_DEFINITION

        extend type Query { 
            fakeQuery: TestObject 
        }
        
        type TestObject { 
            field1: ID! @dataClassification(level : ENUM1) 
        }
        
        enum EnumType { 
            ENUM2
        }
    '''

    def "can merge enum types if for federated services"() {
        given:
        ServiceProvider testServiceA = createQueryMatchingService("TEST_SERVICE_A",
                ServiceProvider.ServiceType.FEDERATION_SUBGRAPH, SCHEMA_1, null)
        ServiceProvider testServiceB = createQueryMatchingService("TEST_SERVICE_B",
                ServiceProvider.ServiceType.FEDERATION_SUBGRAPH, SCHEMA_2, null)
        List<ServiceProvider> services = [testServiceA, testServiceB]

        when:
        RuntimeGraph actualRuntimeGraph = SchemaStitcher.newBuilder()
                .services(services).build().stitchGraph();

        then:
        actualRuntimeGraph != null
        GraphQLEnumType enumType = actualRuntimeGraph.getType("EnumType")
        enumType.getValues().size() == 2
        enumType.getValue("ENUM1").getValue() == "ENUM1"
        enumType.getValue("ENUM2").getValue() == "ENUM2"
        enumType == actualRuntimeGraph.getExecutableSchema().getType("EnumType")
    }

    def "cannot merge enum types for non federated services"() {
        given:
        ServiceProvider testServiceA = createQueryMatchingService("TEST_SERVICE_A",
                ServiceProvider.ServiceType.GRAPHQL, SCHEMA_1, null)
        ServiceProvider testServiceB = createQueryMatchingService("TEST_SERVICE_B",
                ServiceProvider.ServiceType.GRAPHQL, SCHEMA_2, null)
        List<ServiceProvider> services = [testServiceA, testServiceB]

        when:
        RuntimeGraph actualRuntimeGraph = SchemaStitcher.newBuilder()
                .services(services).build().stitchGraph();

        then:
        thrown TypeConflictException.class
    }

    def "cannot merge enum types if one is federated and one is pure graphql"() {
        given:
        ServiceProvider testServiceA = createQueryMatchingService("TEST_SERVICE_A",
                ServiceProvider.ServiceType.GRAPHQL, SCHEMA_1, null)
        ServiceProvider testServiceB = createQueryMatchingService("TEST_SERVICE_B",
                ServiceProvider.ServiceType.FEDERATION_SUBGRAPH, SCHEMA_2, null)
        List<ServiceProvider> services = [testServiceA, testServiceB]

        when:
        RuntimeGraph actualRuntimeGraph = SchemaStitcher.newBuilder()
                .services(services).build().stitchGraph();

        then:
        actualRuntimeGraph != null
        GraphQLEnumType enumType = actualRuntimeGraph.getType("EnumType")
        enumType.getValues().size() == 2
        enumType.getValue("ENUM1").getValue() == "ENUM1"
        enumType.getValue("ENUM2").getValue() == "ENUM2"
        enumType == actualRuntimeGraph.getExecutableSchema().getType("EnumType")
    }


}
