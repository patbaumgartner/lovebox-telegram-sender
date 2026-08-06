package com.patbaumgartner.lovebox.telegram.sender.lovebox;

/**
 * Generic GraphQL request envelope for {@code /v1/graphql}.
 *
 * @param operationName the GraphQL operation name, may be {@code null}
 * @param variables the GraphQL variables, may be {@code null}
 * @param query the GraphQL document
 */
public record GraphqlRequestBody(String operationName, Object variables, String query) {

}
