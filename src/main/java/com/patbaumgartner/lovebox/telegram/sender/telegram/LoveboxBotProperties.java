package com.patbaumgartner.lovebox.telegram.sender.telegram;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bot")
public record LoveboxBotProperties(String username, String token) {

}
