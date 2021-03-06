package com.intuit.graphql.orchestrator.resolverdirective;

public class ArgumentDefinitionNotAllowed extends ResolverDirectiveException {

  private static final String ERR_MSG = "Field %s in container type %s with resolver directive not allowed "
      + "to have argument definitions.";

  public ArgumentDefinitionNotAllowed(String fieldName, String containerName) {
    super(String.format(ERR_MSG, fieldName, containerName));
  }
}
