package com.patbaumgartner.lovebox.telegram.sender.rest.clients;

public record GraphqlRequestBody(String operationName, Object variables, String query) {

}
