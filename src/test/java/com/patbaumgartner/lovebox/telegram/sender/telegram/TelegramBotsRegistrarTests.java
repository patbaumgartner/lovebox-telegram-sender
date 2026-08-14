package com.patbaumgartner.lovebox.telegram.sender.telegram;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.telegram.telegrambots.longpolling.BotSession;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramBotsRegistrarTests {

	@Mock
	private TelegramBotsLongPollingApplication telegramBotsApplication;

	@Mock
	private SpringLongPollingBot bot;

	@Mock
	private LongPollingUpdateConsumer updatesConsumer;

	@Mock
	private BotSession botSession;

	private static LoveboxBotProperties properties(boolean enabled) {
		return enabled ? new LoveboxBotProperties(true, "lovebox_bot", "token", Set.of(7L), EchoMode.SENDER)
				: new LoveboxBotProperties(false, "lovebox_bot", null, null, null);
	}

	@Test
	void registersAllBots() throws TelegramApiException {
		when(this.bot.getBotToken()).thenReturn("token");
		when(this.bot.getUpdatesConsumer()).thenReturn(this.updatesConsumer);
		when(this.telegramBotsApplication.registerBot("token", this.updatesConsumer)).thenReturn(this.botSession);
		TelegramBotsRegistrar registrar = new TelegramBotsRegistrar(this.telegramBotsApplication, List.of(this.bot),
				properties(true));

		registrar.run(null);

		verify(this.telegramBotsApplication).registerBot("token", this.updatesConsumer);
	}

	@Test
	void skipsRegistrationWhenDisabled() {
		TelegramBotsRegistrar registrar = new TelegramBotsRegistrar(this.telegramBotsApplication, List.of(this.bot),
				properties(false));

		registrar.run(null);

		verifyNoInteractions(this.telegramBotsApplication);
	}

	@Test
	void failsWhenNoBotsAreAvailable() {
		TelegramBotsRegistrar registrar = new TelegramBotsRegistrar(this.telegramBotsApplication, List.of(),
				properties(true));

		assertThatIllegalStateException().isThrownBy(() -> registrar.run(null))
			.withMessageContaining("No SpringLongPollingBot beans");
	}

	@Test
	void wrapsRegistrationFailures() throws TelegramApiException {
		when(this.bot.getBotToken()).thenReturn("token");
		when(this.bot.getUpdatesConsumer()).thenReturn(this.updatesConsumer);
		when(this.telegramBotsApplication.registerBot(anyString(), any(LongPollingUpdateConsumer.class)))
			.thenThrow(new TelegramApiException("boom"));
		TelegramBotsRegistrar registrar = new TelegramBotsRegistrar(this.telegramBotsApplication, List.of(this.bot),
				properties(true));

		assertThatIllegalStateException().isThrownBy(() -> registrar.run(null))
			.withMessageContaining("Could not start Telegram long-polling")
			.withCauseInstanceOf(TelegramApiException.class);
	}

}
