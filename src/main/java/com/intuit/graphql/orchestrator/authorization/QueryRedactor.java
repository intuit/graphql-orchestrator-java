package com.intuit.graphql.orchestrator.authorization;

import static com.intuit.graphql.orchestrator.resolverdirective.FieldResolverDirectiveUtil.hasResolverDirective;

import com.google.common.collect.ImmutableMap;
import com.intuit.graphql.orchestrator.common.FieldPosition;
import graphql.GraphQLContext;
import graphql.analysis.QueryVisitorFieldEnvironment;
import graphql.analysis.QueryVisitorStub;
import graphql.language.Field;
import graphql.schema.GraphQLFieldsContainer;
import graphql.util.TraverserContext;
import graphql.util.TreeTransformerUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;

@Builder
public class QueryRedactor<ClaimT> extends QueryVisitorStub {

  private Pair<String, Object> claimData;
  private AuthorizationContext<ClaimT> authorizationContext;
  private GraphQLContext graphQLContext;

  @Getter
  private final List<DeclinedField> declinedFields = new ArrayList<>();

  @Override
  public void visitField(QueryVisitorFieldEnvironment queryVisitorFieldEnvironment) {
    Field field = queryVisitorFieldEnvironment.getField();
    Map<String, Object> fieldArguments = queryVisitorFieldEnvironment.getArguments();
    FieldPath fieldPath = createFieldPath(queryVisitorFieldEnvironment);
    queryVisitorFieldEnvironment.getTraverserContext().setVar(FieldPath.class, fieldPath);

    if (hasResolverDirective(queryVisitorFieldEnvironment.getFieldDefinition())) {
      TreeTransformerUtil.deleteNode(queryVisitorFieldEnvironment.getTraverserContext());
    }

    FieldPosition fieldPosition = createFieldPosition(queryVisitorFieldEnvironment);
    if (requiresFieldAuthorization(fieldPosition) && !fieldAccessIsAllowed(fieldPosition, fieldArguments)) {
      TreeTransformerUtil.deleteNode(queryVisitorFieldEnvironment.getTraverserContext());
      this.declinedFields.add(new DeclinedField(field, fieldPath.toString()));
    }
  }

  private FieldPosition createFieldPosition(QueryVisitorFieldEnvironment queryVisitorFieldEnvironment) {
    Field field = queryVisitorFieldEnvironment.getField();
    GraphQLFieldsContainer parentType = (GraphQLFieldsContainer) queryVisitorFieldEnvironment.getParentType();
    return new FieldPosition(parentType.getName(), field.getName());
  }

  private FieldPath createFieldPath(QueryVisitorFieldEnvironment queryVisitorFieldEnvironment) {
    Field field = queryVisitorFieldEnvironment.getField();
    FieldPath fieldPath;
    if (isRoot(queryVisitorFieldEnvironment)) {
      fieldPath = new FieldPath(field.getName());
    } else {
      FieldPath parentFieldPath = getParentFieldPath(queryVisitorFieldEnvironment);
      fieldPath = parentFieldPath.createChildPath(field.getName());
    }
    return fieldPath;
  }

  private FieldPath getParentFieldPath(QueryVisitorFieldEnvironment queryVisitorFieldEnvironment) {
    return queryVisitorFieldEnvironment.getParentEnvironment().getTraverserContext().getVar(FieldPath.class);
  }

  private boolean isRoot(QueryVisitorFieldEnvironment queryVisitorFieldEnvironment) {
    TraverserContext<?> currentTraverserCtx = queryVisitorFieldEnvironment.getTraverserContext();
    TraverserContext<?> parentTraverserCtx = currentTraverserCtx.getParentContext();
    return parentTraverserCtx.isRootContext();
  }

  private boolean requiresFieldAuthorization(FieldPosition fieldPosition) {
    return authorizationContext.getFieldAuthorization().requiresAccessControl(fieldPosition);
  }

  private boolean fieldAccessIsAllowed(FieldPosition fieldPosition, Map<String, Object> fieldArguments) {
    Map<String, Object> authData = ImmutableMap.of(
            claimData.getLeft(), claimData.getRight(),
            "fieldArguments", fieldArguments
    );

    FieldAuthorizationRequest fieldAuthorizationRequest = FieldAuthorizationRequest.builder()
            .fieldPosition(fieldPosition)
            .clientId(this.authorizationContext.getClientId())
            .graphQLContext(graphQLContext)
            .authData(authData)
            .build();

    return authorizationContext.getFieldAuthorization().isAccessAllowed(fieldAuthorizationRequest);
  }

}
