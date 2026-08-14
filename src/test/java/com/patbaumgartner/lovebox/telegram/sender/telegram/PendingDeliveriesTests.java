package com.patbaumgartner.lovebox.telegram.sender.telegram;

import java.time.Instant;
import java.util.List;

import com.patbaumgartner.lovebox.telegram.sender.lovebox.MessageStatus;
import com.patbaumgartner.lovebox.telegram.sender.telegram.PendingDeliveries.CaptionEdit;
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
	void appliesAStatusChangeOnlyOnceTelegramAcceptedIt() {
		this.deliveries.track("m1", CAPTION, "sending");
		this.deliveries.register("m1", new TelegramEcho(7L, 42), "sending");
		List<MessageStatus> latest = List.of(new MessageStatus("m1", "read"));

		List<CaptionEdit> edits = this.deliveries.apply(latest);
		assertThat(edits).hasSize(1);
		this.deliveries.markDisplayed(edits.get(0));

		assertThat(this.deliveries.apply(latest)).isEmpty();
	}

	@Test
	void offersTheEditAgainUntilTelegramAcceptsIt() {
		this.deliveries.track("m1", CAPTION, "sending");
		this.deliveries.register("m1", new TelegramEcho(7L, 42), "sending");
		List<MessageStatus> latest = List.of(new MessageStatus("m1", "read"));

		assertThat(this.deliveries.apply(latest)).hasSize(1);
		List<CaptionEdit> retry = this.deliveries.apply(latest);
		assertThat(retry).hasSize(1);
		this.deliveries.markDisplayed(retry.get(0));

		assertThat(this.deliveries.apply(latest)).isEmpty();
	}

	@Test
	void givesUpOnAnEchoTelegramKeepsRejecting() {
		this.deliveries.track("m1", CAPTION, "sending");
		this.deliveries.register("m1", new TelegramEcho(7L, 42), "sending");
		List<MessageStatus> latest = List.of(new MessageStatus("m1", "read"));

		for (int attempt = 0; attempt < 5; attempt++) {
			assertThat(this.deliveries.apply(latest)).as("attempt %d", attempt).hasSize(1);
		}

		assertThat(this.deliveries.apply(latest)).isEmpty();
	}

	@Test
	void retriesAgainAfterTheNextAcceptedEdit() {
		this.deliveries.track("m1", CAPTION, "sending");
		this.deliveries.register("m1", new TelegramEcho(7L, 42), "sending");
		List<CaptionEdit> delivered = this.deliveries.apply(List.of(new MessageStatus("m1", "delivered")));
		this.deliveries.markDisplayed(delivered.get(0));

		List<MessageStatus> read = List.of(new MessageStatus("m1", "read"));
		for (int attempt = 0; attempt < 5; attempt++) {
			assertThat(this.deliveries.apply(read)).as("attempt %d", attempt).hasSize(1);
		}
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
			.hasValueSatisfying(edit -> assertThat(edit.caption()).contains("[read]"));
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
