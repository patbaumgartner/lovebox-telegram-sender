package com.patbaumgartner.lovebox.telegram.sender.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * Central registration of GraalVM native-image hints for the application.
 * <p>
 * Imports {@link TelegramBotsRuntimeHints} (reflection metadata for the Telegram Bot API
 * types, which the telegrambots library does not provide). Without these hints the native
 * image cannot (de)serialise Bot API payloads with Jackson.
 */
@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints({ TelegramBotsRuntimeHints.class })
public class NativeHintsConfiguration {

}
