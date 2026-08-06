package com.patbaumgartner.lovebox.telegram.sender.lovebox;

/**
 * Response payload of {@code /v1/auth/checkEmail}.
 *
 * @param existingUser whether an account exists for the given e-mail address
 * @param firstName the first name registered for the account, if any
 */
public record CheckEmailResponseBody(Boolean existingUser, String firstName) {

}
