package com.patbaumgartner.lovebox.telegram.sender.rest.clients;

import org.springframework.http.ResponseEntity;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

public interface LoveboxRestClient {

	@PostExchange("/v1/auth/checkEmail")
	ResponseEntity<CheckEmailResponseBody> checkEmail(@RequestBody CheckEmailRequestBody request);

	@PostExchange("/v1/auth/loginWithPassword")
	ResponseEntity<LoginWithPasswordResponseBody> loginWithPassword(@RequestBody LoginWithPasswordlRequestBody request);

	@PostExchange("/v1/graphql")
	ResponseEntity<String> graphql(@RequestHeader("authorization") String authorization,
			@RequestBody GraphqlRequestBody request);

}
