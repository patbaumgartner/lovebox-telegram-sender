package com.patbaumgartner.lovebox.telegram.sender.rest.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "LoveboxApi", url = "https://app-api.loveboxlove.com")
public interface LoveboxRestClient {

    @PostMapping(path = "/v1/auth/checkEmail")
    ResponseEntity<CheckEmailResponseBody> checkEmail(CheckEmailRequestBody request);

    @PostMapping(path = "/v1/auth/loginWithPassword")
    ResponseEntity<LoginWithPasswordResponseBody> loginWithPassword(LoginWithPasswordlRequestBody request);

    @PostMapping(path = "/v1/graphql")
    ResponseEntity<String> graphql(@RequestHeader("authorization") String authorization, GraphqlRequestBody request);
}
