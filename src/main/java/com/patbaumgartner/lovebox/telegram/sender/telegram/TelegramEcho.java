package com.patbaumgartner.lovebox.telegram.sender.telegram;

/**
 * A Telegram message this bot sent to echo a Lovebox message, addressed well enough to
 * edit its caption later.
 * <p>
 * Deliberately not the library's {@code Message}: that object is a snapshot of the state
 * at send time, so reading its caption back after an edit yields a stale value.
 *
 * @param chatId the chat the echo was sent to
 * @param messageId the Telegram message id of the echo
 */
record TelegramEcho(long chatId, int messageId) {

}
