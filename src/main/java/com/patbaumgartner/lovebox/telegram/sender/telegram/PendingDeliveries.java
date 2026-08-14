package com.patbaumgartner.lovebox.telegram.sender.telegram;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.patbaumgartner.lovebox.telegram.sender.lovebox.MessageStatus;

/**
 * Tracks the messages this process put on the Lovebox, so their Telegram echoes can
 * follow the delivery status (e.g. sending → read).
 * <p>
 * Bounded by insertion order: the Lovebox API only reports the most recent messages, so
 * an entry that falls out of that window can never receive another status change. State
 * is intentionally in-memory only - a caption that stops updating after a restart is a
 * cosmetic loss, while persisting it correctly would need an outbox and idempotency the
 * undocumented vendor API does not offer.
 * <p>
 * Every method is synchronized: the Telegram update thread registers echoes while the
 * scheduled poll observes statuses, and {@link #register} has to see a status change that
 * arrived between sending an echo and recording it.
 * <p>
 * The status is remembered per echo rather than only per Lovebox message, so an edit that
 * Telegram rejected is retried on the next poll. Tracking it once per message meant the
 * status advanced as soon as the edit was <em>handed to</em> Telegram, and a single
 * failed call left that caption stale for the rest of the process' life.
 */
class PendingDeliveries {

	private static final int MAX_TRACKED = 64;

	/**
	 * How often a single caption edit is retried before the echo is given up on. Bounded
	 * because some failures never recover - the recipient deleted the echoed message, for
	 * instance - and the poll would otherwise retry them every 20 seconds for as long as
	 * the message stays in the window the Lovebox API reports.
	 */
	private static final int MAX_EDIT_ATTEMPTS = 5;

	private final Map<String, Delivery> deliveries = new LinkedHashMap<>() {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Delivery> eldest) {
			return size() > MAX_TRACKED;
		}
	};

	/**
	 * Starts tracking a message the Lovebox API has accepted.
	 * @param messageId the Lovebox message identifier
	 * @param caption the unchanging parts of the Telegram caption
	 * @param status the delivery status the API reported on acceptance
	 */
	synchronized void track(String messageId, CaptionContent caption, String status) {
		this.deliveries.put(messageId, new Delivery(caption, status));
	}

	/**
	 * Records a Telegram message that echoes a tracked Lovebox message.
	 * @param messageId the Lovebox message identifier
	 * @param echo the Telegram message that was sent
	 * @param displayedStatus the status the echo was rendered with
	 * @return the edit that corrects the echo, when the delivery status already moved on
	 * between sending the echo and registering it, otherwise empty
	 */
	synchronized Optional<CaptionEdit> register(String messageId, TelegramEcho echo, String displayedStatus) {
		Delivery delivery = this.deliveries.get(messageId);
		if (delivery == null) {
			return Optional.empty();
		}
		delivery.echoes.add(new TrackedEcho(echo, displayedStatus));
		return delivery.status.equals(displayedStatus) ? Optional.empty()
				: Optional.of(edit(messageId, delivery, echo));
	}

	/**
	 * Applies the statuses reported by the Lovebox API.
	 * @param latest the statuses of the most recent messages
	 * @return the caption edits needed to bring every echo up to date, including edits
	 * that a previous poll handed to Telegram but that Telegram did not accept
	 */
	synchronized List<CaptionEdit> apply(List<MessageStatus> latest) {
		List<CaptionEdit> edits = new ArrayList<>();
		for (MessageStatus message : latest) {
			Delivery delivery = this.deliveries.get(message.messageId());
			if (delivery == null) {
				continue;
			}
			delivery.status = message.status();
			for (TrackedEcho tracked : delivery.echoes) {
				if (tracked.displayedStatus.equals(message.status()) || tracked.remainingAttempts <= 0) {
					continue;
				}
				tracked.remainingAttempts--;
				edits.add(edit(message.messageId(), delivery, tracked.echo));
			}
		}
		return edits;
	}

	/**
	 * Confirms that Telegram accepted an edit. Until this is called the echo stays out of
	 * date and {@link #apply} keeps offering the edit again.
	 * @param edit the edit Telegram accepted
	 */
	synchronized void markDisplayed(CaptionEdit edit) {
		Delivery delivery = this.deliveries.get(edit.messageId());
		if (delivery == null) {
			return;
		}
		for (TrackedEcho tracked : delivery.echoes) {
			if (tracked.echo.equals(edit.echo())) {
				tracked.displayedStatus = edit.status();
				tracked.remainingAttempts = MAX_EDIT_ATTEMPTS;
			}
		}
	}

	private static CaptionEdit edit(String messageId, Delivery delivery, TelegramEcho echo) {
		return new CaptionEdit(messageId, echo, delivery.caption.render(delivery.status), delivery.status);
	}

	synchronized int size() {
		return this.deliveries.size();
	}

	/**
	 * @param messageId the Lovebox message the echo belongs to
	 * @param echo the Telegram message whose caption is out of date
	 * @param caption the caption it should show
	 * @param status the status that caption renders
	 */
	record CaptionEdit(String messageId, TelegramEcho echo, String caption, String status) {

	}

	private static final class Delivery {

		private final CaptionContent caption;

		private final List<TrackedEcho> echoes = new ArrayList<>();

		private String status;

		private Delivery(CaptionContent caption, String status) {
			this.caption = caption;
			this.status = status;
		}

	}

	private static final class TrackedEcho {

		private final TelegramEcho echo;

		private String displayedStatus;

		private int remainingAttempts = MAX_EDIT_ATTEMPTS;

		private TrackedEcho(TelegramEcho echo, String displayedStatus) {
			this.echo = echo;
			this.displayedStatus = displayedStatus;
		}

	}

}
