package com.patbaumgartner.lovebox.telegram.sender.lovebox;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response payload of {@code /v1/auth/loginWithPassword}.
 *
 * @param id the account identifier
 * @param firstName the first name registered for the account
 * @param email the account e-mail address
 * @param token the bearer token for subsequent GraphQL requests
 */
public record LoginWithPasswordResponseBody(@JsonProperty("_id") String id, String firstName, String email,
		String token) {

}
