package com.intuit.graphql.orchestrator.xtext

import spock.lang.Specification

import static com.intuit.graphql.orchestrator.xtext.XtextScalars.*

class XtextScalarsSpec extends Specification {

    def "test Does Not Return Same Instance"() {
        expect:
        !newBigDecimalType().is(newBigDecimalType())
        !newBigIntType().is(newBigIntType())
        !newBooleanType().is(newBooleanType())
        !newByteType().is(newByteType())
        !newCharType().is(newCharType())
        !newFloatType().is(newFloatType())
        !newIntType().is(newIntType())
        !newIdType().is(newIdType())
        !newStringType().is(newStringType())
        !newLongType().is(newLongType())
        !newShortType().is(newShortType())
        !newFieldSetType().is(newFieldSetType())
    }

    def "test Standard Scalar Map"() {
        expect:
        XtextScalars.STANDARD_SCALARS.size() == 12
    }
}
