package com.patbaumgartner.lovebox.telegram.sender.telegram;

/**
 * Who sees the image that was put on the Lovebox.
 */
public enum EchoMode {

	/** Echo back only to the chat the message came from. */
	SENDER,

	/**
	 * Echo to every chat in {@code bot.allowed-chat-ids}, so a couple sharing one box
	 * sees each other's notes and their delivery status.
	 */
	ALL_ALLOWED

}
