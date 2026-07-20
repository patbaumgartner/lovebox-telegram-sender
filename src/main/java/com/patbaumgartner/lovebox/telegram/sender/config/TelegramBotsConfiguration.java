package com.patbaumgartner.lovebox.telegram.sender.config;

import java.util.List;

import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.starter.TelegramBotInitializer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Explicit replacement for the telegrambots starter auto-configuration
 * ({@code TelegramBotStarterConfiguration}), which silently fails in the GraalVM native
 * image: the starter injects the bots as
 * {@code ObjectProvider<List<SpringLongPollingBot>>} and resolves them via
 * {@code getIfAvailable()}. In the AOT-processed native image the generic signature of
 * that provider is not resolvable through the generated CGLIB configuration proxy, so the
 * provider yields the empty-list fallback and {@link TelegramBotInitializer} registers no
 * bots — the app starts fine but never long-polls Telegram, and incoming messages are
 * silently ignored.
 * <p>
 * Defining the same beans here with direct {@code List<SpringLongPollingBot>} injection
 * (fully supported by AOT) makes the starter's {@code @ConditionalOnMissingBean}
 * definitions back off on both JVM and native.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "telegrambots", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TelegramBotsConfiguration {

	/**
	 * The long-polling runtime; closed on context shutdown.
	 * @return the Telegram bots long-polling application
	 */
	@Bean(destroyMethod = "close")
	public TelegramBotsLongPollingApplication telegramBotsApplication() {
		return new TelegramBotsLongPollingApplication();
	}

	/**
	 * Registers all {@link SpringLongPollingBot} beans with the long-polling runtime on
	 * startup (same contract as the starter's initializer, but with the bot list injected
	 * directly so it works in the native image).
	 * @param telegramBotsApplication the long-polling runtime
	 * @param bots all long-polling bot beans in the context
	 * @return the initializer that registers the bots
	 */
	@Bean
	public TelegramBotInitializer telegramBotInitializer(TelegramBotsLongPollingApplication telegramBotsApplication,
			List<SpringLongPollingBot> bots) {
		return new TelegramBotInitializer(telegramBotsApplication, bots);
	}

}
