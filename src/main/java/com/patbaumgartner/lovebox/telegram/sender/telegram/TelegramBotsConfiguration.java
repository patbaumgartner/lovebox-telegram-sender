package com.patbaumgartner.lovebox.telegram.sender.telegram;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.starter.TelegramBotInitializer;
import org.telegram.telegrambots.meta.generics.TelegramClient;

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
 * <p>
 * Registration itself is performed by {@link TelegramBotsRegistrar}, a named
 * {@code ApplicationRunner}. An earlier version used a lambda
 * {@code ApplicationListener<ApplicationReadyEvent>} here, which is never invoked in the
 * native image and left the bot unregistered without any error.
 */
@Configuration(proxyBeanMethods = false)
public class TelegramBotsConfiguration {

	/**
	 * The client used to call the Telegram Bot API. Defined as a bean (rather than
	 * instantiated inside {@link LoveboxBot}) so tests can replace it with a mock.
	 * @param botProperties the bot credentials
	 * @return the Telegram client
	 */
	@Bean
	public TelegramClient telegramClient(LoveboxBotProperties botProperties) {
		return new OkHttpTelegramClient(botProperties.token());
	}

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
	public TelegramBotsRegistrar telegramBotsRegistrar(TelegramBotsLongPollingApplication telegramBotsApplication,
			List<SpringLongPollingBot> bots, Environment environment) {
		return new TelegramBotsRegistrar(telegramBotsApplication, bots,
				environment.getProperty("lovebox.enabled", Boolean.class, true));
	}

}
