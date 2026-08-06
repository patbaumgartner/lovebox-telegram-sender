package com.patbaumgartner.lovebox.telegram.sender.telegram;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.Update;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramBotsRuntimeHintsTests {

	@Test
	void registersReflectionHintsForTelegramBotApiTypes() {
		RuntimeHints hints = new RuntimeHints();
		new TelegramBotsRuntimeHints().registerHints(hints, getClass().getClassLoader());

		assertThat(RuntimeHintsPredicates.reflection().onType(SendMessage.class)).accepts(hints);
		assertThat(RuntimeHintsPredicates.reflection().onType(SendPhoto.class)).accepts(hints);
		assertThat(RuntimeHintsPredicates.reflection().onType(Update.class)).accepts(hints);
	}

}
