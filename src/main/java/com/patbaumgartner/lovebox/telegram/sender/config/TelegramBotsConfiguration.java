package com.patbaumgartner.lovebox.telegram.sender.config;

import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.starter.TelegramBotInitializer;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

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
	 * Prevents the starter's initializer from registering bots during AOT training.
	 * Registration is deferred until the application is ready.
	 * @param telegramBotsApplication the long-polling runtime
	 * @param bots all long-polling bot beans in the context
	 * @return an initializer that suppresses the starter's eager registration
	 */
	@Bean
	public TelegramBotInitializer telegramBotInitializer(TelegramBotsLongPollingApplication telegramBotsApplication,
			List<SpringLongPollingBot> bots) {
		return new TelegramBotInitializer(telegramBotsApplication, bots) {
			@Override
			public void afterPropertiesSet() {
			}
		};
	}

	@Bean
	public ApplicationListener<ApplicationReadyEvent> telegramBotsRegistrar(
			TelegramBotsLongPollingApplication telegramBotsApplication, List<SpringLongPollingBot> bots,
			Environment environment) {
		return event -> {
			if (!environment.getProperty("lovebox.enabled", Boolean.class, true)) {
				return;
			}
			for (SpringLongPollingBot bot : bots) {
				try {
					telegramBotsApplication.registerBot(bot.getBotToken(), bot.getUpdatesConsumer());
				}
				catch (TelegramApiException e) {
					throw new IllegalStateException("Could not start Telegram long-polling", e);
				}
			}
		};
	}

}
