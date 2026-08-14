package com.patbaumgartner.lovebox.telegram.sender.telegram;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * The parts of a Telegram photo caption that do not change while a message is delivered.
 * <p>
 * Captions are re-rendered from these parts on every status change. The previous
 * implementation patched the caption in place with
 * {@code caption.replaceAll("\\[.*]\\.", "[" + status + "].")}, which also rewrote any
 * {@code [...]}. the sender happened to write themselves, and threw
 * {@link IndexOutOfBoundsException} when a status or message contained {@code $1} or
 * another regular-expression replacement metacharacter.
 *
 * @param text the message the sender wrote, as shown in the caption preview
 * @param sentAt the instant the Lovebox API accepted the message
 */
record CaptionContent(String text, Instant sentAt) {

	/** Telegram rejects a {@code sendPhoto} whose caption exceeds this length. */
	static final int TELEGRAM_CAPTION_LIMIT = 1024;

	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

	private static final String PREFIX = "Message: \"";

	private static final String INFIX = "\" ";

	String render(String status) {
		String suffix = "%nStatus: [%s].%nExecuted: %s".formatted(status,
				this.sentAt.atZone(ZoneId.systemDefault()).format(TIME_FORMAT));
		int room = TELEGRAM_CAPTION_LIMIT - PREFIX.length() - INFIX.length() - suffix.length();
		return PREFIX + truncate(singleLine(this.text), room) + INFIX + suffix;
	}

	private static String singleLine(String text) {
		return text != null ? text.replace("\n", " ") : "";
	}

	private static String truncate(String text, int limit) {
		if (limit <= 0) {
			return "";
		}
		if (text.length() <= limit) {
			return text;
		}
		int end = limit - 1;
		if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) {
			end--;
		}
		return text.substring(0, Math.max(0, end)) + "…";
	}

}
