package com.patbaumgartner.lovebox.telegram.sender.image;

/**
 * Signals that a message cannot be rendered for the Lovebox display.
 * <p>
 * The message of this exception is written for the person who sent it: the bot forwards
 * it to the Telegram chat unchanged, so it must explain what to do differently rather
 * than name an internal cause.
 */
public class UnsupportedMessageException extends IllegalArgumentException {

	private static final long serialVersionUID = 1L;

	public UnsupportedMessageException(String message) {
		super(message);
	}

}
