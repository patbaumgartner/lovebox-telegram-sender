package com.patbaumgartner.lovebox.telegram.sender.lovebox;

/**
 * Request payload for {@code /v1/auth/checkEmail}.
 *
 * @param email the account e-mail address to check
 */
public record CheckEmailRequestBody(String email) {

}
