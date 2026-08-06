package com.patbaumgartner.lovebox.telegram.sender.lovebox;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.PostExchange;

/**
 * Declarative HTTP interface for the (undocumented) Lovebox mobile-app API.
 */
public interface LoveboxRestClient {

	@PostExchange("/v1/auth/checkEmail")
	ResponseEntity<CheckEmailResponseBody> checkEmail(@RequestBody CheckEmailRequestBody request);

	@PostExchange("/v1/auth/loginWithPassword")
	ResponseEntity<LoginWithPasswordResponseBody> loginWithPassword(@RequestBody LoginWithPasswordRequestBody request);

	@PostExchange("/v1/graphql")
	ResponseEntity<String> graphql(@RequestHeader("authorization") String authorization,
			@RequestBody GraphqlRequestBody request);

}
