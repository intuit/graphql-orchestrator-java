package com.intuit.graphql.orchestrator.resolverdirective

import spock.lang.Specification

import static com.intuit.graphql.orchestrator.utils.XtextUtils.getAllTypes
import static com.intuit.graphql.orchestrator.utils.XtextUtils.getOperationType
import static com.intuit.graphql.orchestrator.xtext.XtextScalars.STANDARD_SCALARS

import com.intuit.graphql.graphQL.ObjectTypeDefinition
import com.intuit.graphql.graphQL.TypeDefinition
import com.intuit.graphql.orchestrator.schema.Operation
import com.intuit.graphql.orchestrator.schema.transform.ResolverArgumentListTypeNotSupported
import com.intuit.graphql.orchestrator.xtext.FieldContext
import com.intuit.graphql.orchestrator.xtext.UnifiedXtextGraph
import com.intuit.graphql.orchestrator.xtext.XtextResourceSetBuilder
import java.util.stream.Stream
import org.eclipse.xtext.resource.XtextResourceSet

class ResolverDirectiveTypeResolverSpec extends Specification {

    private ResolverDirectiveTypeResolver resolver

    public UnifiedXtextGraph source

    def setup() {
        this.resolver = new ResolverDirectiveTypeResolver()

        final String schemaString = '''
            schema { query: Query } 
            type Query { a: A list: [A] premature_leaf: Int some_enum: Enum } 
            type A { b: Int } enum Enum { A }
        '''

        final XtextResourceSet schemaResource = XtextResourceSetBuilder.newBuilder()
                .file("schema", schemaString)
                .build()

        final ObjectTypeDefinition queryOperation = getOperationType(Operation.QUERY, schemaResource)

        Map<String, TypeDefinition> types =
                Stream.concat(getAllTypes(schemaResource), STANDARD_SCALARS.stream())
                .inject([:]) {map, it -> map << [(it.getName()): it]}

        source = UnifiedXtextGraph.newBuilder()
                .query(queryOperation)
                .types(types)
                .build()
    }

    def "resolves Root Type Of Field"() {
        given:
        String field = "a.b"

        final TypeDefinition result = resolver.resolveField(field, source, "someArg", Mock(FieldContext.class))

        expect:
        result.getName() == "Int"
    }

    def "field Does Not Exist"() {
        given:
        String field = "c.d"

        when:
        resolver.resolveField(field, source, "someArg", Mock(FieldContext.class))

        then:
        thrown(ResolverArgumentFieldRootObjectDoesNotExist)
    }

    def "lists Not Supported"() {
        given:
        final String field = "list"

        when:
        resolver.resolveField(field, source, "someArg", Mock(FieldContext.class))

        then:
        thrown(ResolverArgumentListTypeNotSupported)
    }

    def "premature Leaf Type Scalar"() {
        given:
        final String field = "premature_leaf.nested"

        when:
        resolver.resolveField(field, source, "someArg", Mock(FieldContext.class))

        then:
        thrown(ResolverArgumentPrematureLeafType)
    }

    def "premature Leaf Type Enum"() {
        given:
        final String field = "some_enum.nested"

        when:
        resolver.resolveField(field, source, "someArg", Mock(FieldContext.class))

        then:
        thrown(ResolverArgumentPrematureLeafType)
    }
}
