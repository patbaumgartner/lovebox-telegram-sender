package com.patbaumgartner.lovebox.telegram.sender.telegram;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration of the Telegram bot created via BotFather.
 *
 * @param username the bot username (informational, used in log output)
 * @param token the bot API token used for long-polling and sending messages
 */
@ConfigurationProperties(prefix = "bot")
public record LoveboxBotProperties(String username, String token) {

}
