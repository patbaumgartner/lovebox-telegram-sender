package com.patbaumgartner.lovebox.telegram.sender.telegram;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

	@Test
	void registersAllBots() throws TelegramApiException {
		when(this.bot.getBotToken()).thenReturn("token");
		when(this.bot.getUpdatesConsumer()).thenReturn(this.updatesConsumer);
		TelegramBotsRegistrar registrar = new TelegramBotsRegistrar(this.telegramBotsApplication, List.of(this.bot),
				true);

		registrar.run(null);

		verify(this.telegramBotsApplication).registerBot("token", this.updatesConsumer);
	}

	@Test
	void skipsRegistrationWhenDisabled() {
		TelegramBotsRegistrar registrar = new TelegramBotsRegistrar(this.telegramBotsApplication, List.of(this.bot),
				false);

		registrar.run(null);

		verifyNoInteractions(this.telegramBotsApplication);
	}

	@Test
	void failsWhenNoBotsAreAvailable() {
		TelegramBotsRegistrar registrar = new TelegramBotsRegistrar(this.telegramBotsApplication, List.of(), true);

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
				true);

		assertThatIllegalStateException().isThrownBy(() -> registrar.run(null))
			.withMessageContaining("Could not start Telegram long-polling")
			.withCauseInstanceOf(TelegramApiException.class);
	}

}
