package com.patbaumgartner.lovebox.telegram.sender.telegram;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "bot")
public class LoveboxBotProperties {

    /* Telegram username. */
    private String username;
    /* Telegram token. */
    private String token;
}
