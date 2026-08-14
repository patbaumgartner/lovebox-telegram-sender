package com.patbaumgartner.lovebox.telegram.sender.telegram;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;

import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/**
 * Starts Telegram long-polling for every {@link SpringLongPollingBot} in the context.
 * <p>
 * Deliberately a named {@link ApplicationRunner} rather than a lambda
 * {@code ApplicationListener<ApplicationReadyEvent>}: in the GraalVM native image such a
 * lambda listener is never invoked, so the bots were silently never registered — the
 * application started and kept running its scheduled Lovebox tasks while ignoring every
 * incoming Telegram message. {@code ApplicationRunner} beans are looked up by concrete
 * type and invoked directly by {@code SpringApplication}, which behaves identically on
 * the JVM and in the native image.
 * <p>
 * Runs last so that polling only starts once the Lovebox account has been verified.
 */
public class TelegramBotsRegistrar implements ApplicationRunner, Ordered {

	private static final Logger log = LoggerFactory.getLogger(TelegramBotsRegistrar.class);

	private final TelegramBotsLongPollingApplication telegramBotsApplication;

	private final List<SpringLongPollingBot> bots;

	private final boolean enabled;

	public TelegramBotsRegistrar(TelegramBotsLongPollingApplication telegramBotsApplication,
			List<SpringLongPollingBot> bots, boolean enabled) {
		this.telegramBotsApplication = telegramBotsApplication;
		this.bots = bots;
		this.enabled = enabled;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!this.enabled) {
			log.info("Telegram long-polling is disabled (bot.enabled=false); no bots registered");
			return;
		}
		if (this.bots.isEmpty()) {
			throw new IllegalStateException(
					"No SpringLongPollingBot beans available; Telegram updates would be silently ignored");
		}
		for (SpringLongPollingBot bot : this.bots) {
			try {
				this.telegramBotsApplication.registerBot(bot.getBotToken(), bot.getUpdatesConsumer());
			}
			catch (TelegramApiException ex) {
				throw new IllegalStateException("Could not start Telegram long-polling", ex);
			}
			log.info("Telegram long-polling started for bot {}", bot.getClass().getSimpleName());
		}
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

}
