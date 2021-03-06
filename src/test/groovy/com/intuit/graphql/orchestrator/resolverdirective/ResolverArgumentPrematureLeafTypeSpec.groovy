package com.intuit.graphql.orchestrator.resolverdirective

import com.intuit.graphql.orchestrator.xtext.FieldContext
import spock.lang.Specification

class ResolverArgumentPrematureLeafTypeSpec extends Specification {

    def "produces Correct Error Message"() {
        given:
        final ResolverArgumentPrematureLeafType error = new ResolverArgumentPrematureLeafType(
                "argName", "enumType", new FieldContext("rootObject", "rootField"), "tax")

        expect:
        error.message.contains("Resolver argument 'argName' in 'rootObject:rootField': Premature enumType found in field 'tax'.")
    }
}
