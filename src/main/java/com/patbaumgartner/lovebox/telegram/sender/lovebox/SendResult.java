package com.patbaumgartner.lovebox.telegram.sender.lovebox;

import java.time.Instant;

/**
 * Result of sending a message to the Lovebox.
 *
 * @param messageId the Lovebox message identifier
 * @param sentAt the instant the Lovebox API accepted the message
 * @param status the initial delivery status label (e.g. {@code sending})
 */
public record SendResult(String messageId, Instant sentAt, String status) {

}
