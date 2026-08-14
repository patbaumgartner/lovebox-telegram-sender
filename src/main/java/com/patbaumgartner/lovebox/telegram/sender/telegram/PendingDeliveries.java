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
 */
class PendingDeliveries {

	private static final int MAX_TRACKED = 64;

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
	 * @return the caption to correct the echo with, when the delivery status already
	 * moved on between sending the echo and registering it, otherwise empty
	 */
	synchronized Optional<String> register(String messageId, TelegramEcho echo, String displayedStatus) {
		Delivery delivery = this.deliveries.get(messageId);
		if (delivery == null) {
			return Optional.empty();
		}
		delivery.echoes.add(echo);
		return delivery.status.equals(displayedStatus) ? Optional.empty()
				: Optional.of(delivery.caption.render(delivery.status));
	}

	/**
	 * Applies the statuses reported by the Lovebox API.
	 * @param latest the statuses of the most recent messages
	 * @return the caption edits the changed statuses require
	 */
	synchronized List<CaptionEdit> apply(List<MessageStatus> latest) {
		List<CaptionEdit> edits = new ArrayList<>();
		for (MessageStatus message : latest) {
			Delivery delivery = this.deliveries.get(message.messageId());
			if (delivery == null || delivery.status.equals(message.status())) {
				continue;
			}
			delivery.status = message.status();
			String caption = delivery.caption.render(message.status());
			for (TelegramEcho echo : delivery.echoes) {
				edits.add(new CaptionEdit(echo, caption));
			}
		}
		return edits;
	}

	synchronized int size() {
		return this.deliveries.size();
	}

	/**
	 * @param echo the Telegram message whose caption is out of date
	 * @param caption the caption it should show
	 */
	record CaptionEdit(TelegramEcho echo, String caption) {

	}

	private static final class Delivery {

		private final CaptionContent caption;

		private final List<TelegramEcho> echoes = new ArrayList<>();

		private String status;

		private Delivery(CaptionContent caption, String status) {
			this.caption = caption;
			this.status = status;
		}

	}

}
