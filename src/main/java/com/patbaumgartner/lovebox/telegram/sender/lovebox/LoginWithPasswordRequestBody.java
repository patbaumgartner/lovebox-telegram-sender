package com.patbaumgartner.lovebox.telegram.sender.lovebox;

/**
 * Request payload for {@code /v1/auth/loginWithPassword}.
 *
 * @param email the account e-mail address
 * @param password the account password
 */
public record LoginWithPasswordRequestBody(String email, String password) {

}
