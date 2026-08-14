package com.patbaumgartner.lovebox.telegram.sender.telegram;

import java.time.Instant;
import java.util.List;

import com.patbaumgartner.lovebox.telegram.sender.lovebox.MessageStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PendingDeliveriesTests {

	private static final CaptionContent CAPTION = new CaptionContent("hello", Instant.parse("2024-01-01T10:00:00Z"));

	private final PendingDeliveries deliveries = new PendingDeliveries();

	@Test
	void reportsNoEditWhileTheStatusIsUnchanged() {
		this.deliveries.track("m1", CAPTION, "sending");
		this.deliveries.register("m1", new TelegramEcho(7L, 42), "sending");

		assertThat(this.deliveries.apply(List.of(new MessageStatus("m1", "sending")))).isEmpty();
	}

	@Test
	void reportsAnEditForEveryEchoOnAStatusChange() {
		this.deliveries.track("m1", CAPTION, "sending");
		this.deliveries.register("m1", new TelegramEcho(7L, 42), "sending");
		this.deliveries.register("m1", new TelegramEcho(8L, 43), "sending");

		assertThat(this.deliveries.apply(List.of(new MessageStatus("m1", "read"))))
			.extracting(edit -> edit.echo().chatId())
			.containsExactlyInAnyOrder(7L, 8L);
	}

	@Test
	void appliesAStatusChangeOnlyOnce() {
		this.deliveries.track("m1", CAPTION, "sending");
		this.deliveries.register("m1", new TelegramEcho(7L, 42), "sending");
		List<MessageStatus> latest = List.of(new MessageStatus("m1", "read"));

		assertThat(this.deliveries.apply(latest)).hasSize(1);
		assertThat(this.deliveries.apply(latest)).isEmpty();
	}

	@Test
	void ignoresMessagesThisProcessDidNotSend() {
		assertThat(this.deliveries.apply(List.of(new MessageStatus("someone-else", "read")))).isEmpty();
	}

	@Test
	void correctsAnEchoRegisteredAfterTheStatusAlreadyMovedOn() {
		this.deliveries.track("m1", CAPTION, "sending");
		this.deliveries.apply(List.of(new MessageStatus("m1", "read")));

		assertThat(this.deliveries.register("m1", new TelegramEcho(7L, 42), "sending"))
			.hasValueSatisfying(caption -> assertThat(caption).contains("[read]"));
	}

	@Test
	void registeringAnUntrackedMessageIsHarmless() {
		assertThat(this.deliveries.register("unknown", new TelegramEcho(7L, 42), "sending")).isEmpty();
	}

	@Test
	void staysBoundedWhenManyMessagesAreSent() {
		for (int index = 0; index < 5_000; index++) {
			this.deliveries.track("m" + index, CAPTION, "sending");
			this.deliveries.register("m" + index, new TelegramEcho(7L, index), "sending");
		}

		assertThat(this.deliveries.size()).isLessThanOrEqualTo(64);
	}

	@Test
	void keepsTheMostRecentMessagesWhenEvicting() {
		for (int index = 0; index < 200; index++) {
			this.deliveries.track("m" + index, CAPTION, "sending");
		}

		assertThat(this.deliveries.apply(List.of(new MessageStatus("m199", "read")))).isEmpty();
		this.deliveries.register("m199", new TelegramEcho(7L, 1), "read");
		assertThat(this.deliveries.apply(List.of(new MessageStatus("m199", "delivered")))).hasSize(1);
		assertThat(this.deliveries.apply(List.of(new MessageStatus("m0", "read")))).isEmpty();
	}

}
