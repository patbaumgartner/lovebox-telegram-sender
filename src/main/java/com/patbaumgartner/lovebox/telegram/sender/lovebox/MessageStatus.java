package com.patbaumgartner.lovebox.telegram.sender.lovebox;

/**
 * Identifier and delivery status of a message known to the Lovebox API.
 *
 * @param messageId the Lovebox message identifier
 * @param status the current delivery status label (e.g. {@code sending}, {@code read})
 */
public record MessageStatus(String messageId, String status) {

}
