package com.intuit.graphql.orchestrator.schema

import com.intuit.graphql.graphQL.Directive
import com.intuit.graphql.graphQL.DirectiveDefinition
import com.intuit.graphql.graphQL.FieldDefinition
import com.intuit.graphql.orchestrator.ServiceProvider
import com.intuit.graphql.orchestrator.utils.FederationConstants
import graphql.schema.FieldCoordinates
import org.eclipse.emf.common.util.BasicEList
import spock.lang.Specification

class ServiceMetadataImplSpec extends Specification {

    FieldCoordinates fieldCoordinates = FieldCoordinates.coordinates("ParentType", "fieldName")

    Directive externalDirectiveMock
    FieldDefinition fieldDefinitionWithExternalDirectiveMock
    FieldDefinition basicFieldDefinitionMock
    ServiceProvider serviceProviderMock

    void setup() {
        def externalDirectiveDefinitionMock = Mock(DirectiveDefinition.class)
        externalDirectiveDefinitionMock.getName() >> FederationConstants.FEDERATION_EXTERNAL_DIRECTIVE

        serviceProviderMock = Mock(ServiceProvider.class)
        serviceProviderMock.isFederationProvider() >> false
        externalDirectiveMock = Mock(Directive.class)
        externalDirectiveMock.getDefinition() >> externalDirectiveDefinitionMock
        fieldDefinitionWithExternalDirectiveMock = Mock(FieldDefinition.class)
        fieldDefinitionWithExternalDirectiveMock.getDirectives() >> new BasicEList<Directive>([externalDirectiveMock])

        basicFieldDefinitionMock = Mock(FieldDefinition.class);
        basicFieldDefinitionMock.getDirectives() >> new BasicEList<Directive>()
    }

    def "isOwnedByThisService() tests a field with @external returns false"() {
        given:
        ServiceMetadata specUnderTest = ServiceMetadataImpl.newBuilder()
                .fieldCoordinates([(fieldCoordinates): fieldDefinitionWithExternalDirectiveMock])
                .serviceProvider(serviceProviderMock)
                .build()

        when:
        def actual = specUnderTest.isOwnedByThisService(fieldCoordinates)

        then:
        actual == false
    }

    def "isOwnedByThisService() tests a field without directives returns true"() {
        given:
        ServiceMetadata specUnderTest = ServiceMetadataImpl.newBuilder()
                .fieldCoordinates([(fieldCoordinates): basicFieldDefinitionMock])
                .serviceProvider(serviceProviderMock)
                .build()

        when:
        def actual = specUnderTest.isOwnedByThisService(fieldCoordinates)

        then:
        actual == true
    }

    def "isOwnedByThisService() tests a field not in fieldCoordinates map"() {
        given:
        ServiceMetadata specUnderTest = ServiceMetadataImpl.newBuilder()
                .fieldCoordinates(Collections.emptyMap())
                .serviceProvider(serviceProviderMock)
                .build()

        when:
        def actual = specUnderTest.isOwnedByThisService(fieldCoordinates)

        then:
        actual == false
    }

    def "isOwnedByThisService() tests a field with null fieldCoordinates map"() {
        given:
        ServiceMetadata specUnderTest = ServiceMetadataImpl.newBuilder()
                .fieldCoordinates(null)
                .serviceProvider(serviceProviderMock)
                .build()

        when:
        def actual = specUnderTest.isOwnedByThisService(fieldCoordinates)

        then:
        actual == false
    }

}
