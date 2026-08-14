package com.patbaumgartner.lovebox.telegram.sender.telegram;

import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration of the Telegram bot created via BotFather.
 * <p>
 * A Telegram bot is reachable by anyone who knows its username, and usernames are
 * discoverable. {@code allowedChatIds} is therefore mandatory whenever the bot is
 * enabled: without it, any stranger could print messages on someone's physical Lovebox.
 * Invalid combinations fail the application at startup rather than at the first incoming
 * message.
 *
 * @param enabled whether Telegram long-polling is active; when {@code false} no bot is
 * registered, which is what the GraalVM AOT training run uses
 * @param username the bot username (informational, used in log output)
 * @param token the bot API token used for long-polling and sending messages
 * @param allowedChatIds the Telegram chat ids the bot accepts messages from and echoes
 * to; every other chat gets a refusal
 * @param echoMode who receives the echo of a message that went to the Lovebox
 */
@ConfigurationProperties(prefix = "bot")
public record LoveboxBotProperties(

		@DefaultValue("true") boolean enabled,

		String username,

		String token,

		Set<Long> allowedChatIds,

		@DefaultValue("SENDER") EchoMode echoMode

) {

	public LoveboxBotProperties {
		allowedChatIds = (allowedChatIds != null) ? Set.copyOf(allowedChatIds) : Set.of();
		if (enabled) {
			if (token == null || token.isBlank()) {
				throw new IllegalArgumentException(
						"bot.token must be set when bot.enabled is true; create a bot with @BotFather to get one");
			}
			if (allowedChatIds.isEmpty()) {
				throw new IllegalArgumentException("bot.allowed-chat-ids must list at least one Telegram chat id "
						+ "when bot.enabled is true, otherwise anyone who finds the bot could write to your Lovebox. "
						+ "Send the bot a message and it replies with the chat id to add.");
			}
		}
	}

	/**
	 * @param chatId the Telegram chat the update came from
	 * @return whether this chat may use the bot
	 */
	public boolean isAllowed(long chatId) {
		return this.allowedChatIds.contains(chatId);
	}

	/**
	 * @param senderChatId the chat a message was received from
	 * @return the chats the rendered image and its delivery status are echoed to
	 */
	public Set<Long> echoRecipients(long senderChatId) {
		return this.echoMode == EchoMode.ALL_ALLOWED ? this.allowedChatIds : Set.of(senderChatId);
	}

}
