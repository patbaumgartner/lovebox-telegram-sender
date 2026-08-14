package com.patbaumgartner.lovebox.telegram.sender.telegram;

import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

class LoveboxBotPropertiesTests {

	@Test
	void rejectsAnEmptyAllowlistWhileTheBotIsEnabled() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new LoveboxBotProperties(true, "bot", "token", Set.of(), EchoMode.SENDER))
			.withMessageContaining("bot.allowed-chat-ids");
	}

	@Test
	void rejectsAMissingAllowlistWhileTheBotIsEnabled() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new LoveboxBotProperties(true, "bot", "token", null, EchoMode.SENDER))
			.withMessageContaining("bot.allowed-chat-ids");
	}

	@Test
	void rejectsAMissingTokenWhileTheBotIsEnabled() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new LoveboxBotProperties(true, "bot", "  ", Set.of(7L), EchoMode.SENDER))
			.withMessageContaining("bot.token");
	}

	@Test
	void allowsAnUnconfiguredBotWhenItIsDisabled() {
		assertThatNoException().isThrownBy(() -> new LoveboxBotProperties(false, null, null, null, null));
	}

	@Test
	void defaultsToAnEmptyAllowlistSoNothingIsAccidentallyAuthorised() {
		LoveboxBotProperties properties = new LoveboxBotProperties(false, null, null, null, null);

		assertThat(properties.allowedChatIds()).isEmpty();
		assertThat(properties.isAllowed(7L)).isFalse();
	}

	@Test
	void authorisesOnlyListedChats() {
		LoveboxBotProperties properties = new LoveboxBotProperties(true, "bot", "token", Set.of(7L, 8L),
				EchoMode.SENDER);

		assertThat(properties.isAllowed(7L)).isTrue();
		assertThat(properties.isAllowed(8L)).isTrue();
		assertThat(properties.isAllowed(9L)).isFalse();
	}

	@Test
	void echoesToTheSenderOnly() {
		LoveboxBotProperties properties = new LoveboxBotProperties(true, "bot", "token", Set.of(7L, 8L),
				EchoMode.SENDER);

		assertThat(properties.echoRecipients(7L)).containsExactly(7L);
	}

	@Test
	void echoesToEveryAllowedChat() {
		LoveboxBotProperties properties = new LoveboxBotProperties(true, "bot", "token", Set.of(7L, 8L),
				EchoMode.ALL_ALLOWED);

		assertThat(properties.echoRecipients(7L)).containsExactlyInAnyOrder(7L, 8L);
	}

	@Test
	void copiesTheAllowlistSoCallersCannotWidenItLater() {
		Set<Long> mutable = new java.util.HashSet<>(Set.of(7L));
		LoveboxBotProperties properties = new LoveboxBotProperties(true, "bot", "token", mutable, EchoMode.SENDER);

		mutable.add(999L);

		assertThat(properties.isAllowed(999L)).isFalse();
	}

}
