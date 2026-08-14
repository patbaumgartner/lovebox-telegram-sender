package com.patbaumgartner.lovebox.telegram.sender.telegram;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.patbaumgartner.lovebox.telegram.sender.image.ImageService;
import com.patbaumgartner.lovebox.telegram.sender.image.LoveboxImage;
import com.patbaumgartner.lovebox.telegram.sender.image.UnsupportedMessageException;
import com.patbaumgartner.lovebox.telegram.sender.lovebox.LoveboxService;
import com.patbaumgartner.lovebox.telegram.sender.lovebox.SendResult;
import com.patbaumgartner.lovebox.telegram.sender.telegram.PendingDeliveries.CaptionEdit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.DefaultLongPollingUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageCaption;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.photo.PhotoSize;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * The Telegram bot bridging chats to the Lovebox: every text or photo message from an
 * authorised chat is rendered as an image and sent to the box, the delivery status is
 * polled and reflected back into the Telegram message captions, and received "waterfalls
 * of hearts" are announced.
 * <p>
 * Only chats listed in {@code bot.allowed-chat-ids} are served. A Telegram bot accepts
 * messages from anyone who knows its username, so without that check any stranger could
 * print whatever they liked on a device standing in someone's home.
 * <p>
 * Updates are processed one at a time on the dedicated background thread provided by
 * {@link DefaultLongPollingUpdateConsumer}; Spring closes the consumer (and its executor)
 * on context shutdown.
 */
@Component
public class LoveboxBot extends DefaultLongPollingUpdateConsumer implements SpringLongPollingBot {

	private static final Logger log = LoggerFactory.getLogger(LoveboxBot.class);

	private static final String HEARTS_MESSAGE = "You received a waterfall of hearts! ❤❤❤";

	private final LoveboxBotProperties botProperties;

	private final ImageService imageService;

	private final LoveboxService loveboxService;

	private final TelegramClient telegramClient;

	private final PendingDeliveries pendingDeliveries = new PendingDeliveries();

	private final Set<Long> heartRecipients = new HashSet<>();

	private String announcedHeartId;

	public LoveboxBot(LoveboxBotProperties botProperties, ImageService imageService, LoveboxService loveboxService,
			TelegramClient telegramClient) {
		this.botProperties = botProperties;
		this.imageService = imageService;
		this.loveboxService = loveboxService;
		this.telegramClient = telegramClient;
	}

	/**
	 * The single scheduled conversation with the Lovebox API.
	 * <p>
	 * {@code fixedDelay} rather than {@code fixedRate} so that a slow or timing-out API
	 * cannot stack up overlapping runs, and each step is isolated so that one failing
	 * call does not skip the others.
	 */
	@Scheduled(fixedDelayString = "${lovebox.poll-interval:20s}")
	public void pollLovebox() {
		poll("initialisation", this.loveboxService::initializeIfNeeded);
		poll("delivery status", this::updateDeliveryStatuses);
		poll("hearts", this::announceWaterfallOfHearts);
	}

	private static void poll(String step, Runnable action) {
		try {
			action.run();
		}
		catch (RuntimeException ex) {
			log.warn("Lovebox {} poll failed: {}", step, ex.getMessage());
		}
	}

	/**
	 * Applies the delivery status of recent Lovebox messages to the captions of the
	 * Telegram messages that echoed them.
	 */
	public void updateDeliveryStatuses() {
		for (CaptionEdit edit : this.pendingDeliveries.apply(this.loveboxService.getMessages())) {
			applyCaptionEdit(edit);
		}
	}

	/**
	 * Announces a pending "waterfall of hearts" to the authorised chats and acknowledges
	 * it only once every one of them has been notified - the API reports each heart
	 * exactly once, so acknowledging earlier would silently discard the event for the
	 * chats that were not reached.
	 * <p>
	 * Which chats already saw the current heart is remembered, so a poll that has to
	 * repeat the announcement (because another chat or the acknowledgement itself failed)
	 * does not notify anyone twice.
	 */
	public void announceWaterfallOfHearts() {
		String heartId = this.loveboxService.pendingHeart();
		if (heartId == null) {
			return;
		}
		if (!heartId.equals(this.announcedHeartId)) {
			this.announcedHeartId = heartId;
			this.heartRecipients.clear();
		}
		for (long chatId : this.botProperties.allowedChatIds()) {
			if (!this.heartRecipients.contains(chatId) && sendTextMessage(chatId, HEARTS_MESSAGE)) {
				this.heartRecipients.add(chatId);
			}
		}
		if (this.heartRecipients.containsAll(this.botProperties.allowedChatIds())) {
			this.loveboxService.acknowledgeHeart(heartId);
		}
	}

	@Override
	public void consume(Update update) {
		if (!update.hasMessage()) {
			return;
		}

		Message message = update.getMessage();
		long chatId = message.getChatId();
		if (!this.botProperties.isAllowed(chatId)) {
			log.warn("Refused a message from unauthorised chat {}", chatId);
			sendTextMessage(chatId, "This bot is private. If it is yours, add this chat id to bot.allowed-chat-ids: %d"
				.formatted(chatId));
			return;
		}

		try {
			handleMessage(message);
		}
		catch (UnsupportedMessageException ex) {
			log.info("Rejected a message from chat {}: {}", chatId, ex.getMessage());
			sendTextMessage(chatId, ex.getMessage());
		}
		catch (RuntimeException | LinkageError ex) {
			// LinkageError too: a missing native-image JNI/reflection registration
			// surfaces as an Error, which would otherwise kill this consumer thread
			// without a trace.
			log.error("Failed to process message from chat {}: {}", chatId, ex.getMessage(), ex);
			sendTextMessage(chatId, "Sorry, I could not process that message.");
		}
	}

	private void handleMessage(Message message) {
		// Suppress Telegram's "/start" command
		String text = message.getText();
		if (text != null && text.startsWith("/start")) {
			return;
		}

		String caption = message.hasPhoto() ? message.getCaption() : text;
		LoveboxImage image = renderImage(message, caption);

		SendResult result = this.loveboxService.sendImageMessage(image.dataUri());
		CaptionContent content = new CaptionContent(caption, result.sentAt());
		this.pendingDeliveries.track(result.messageId(), content, result.status());

		for (long chatId : this.botProperties.echoRecipients(message.getChatId())) {
			echo(chatId, image, content, result);
		}
	}

	private void echo(long chatId, LoveboxImage image, CaptionContent content, SendResult result) {
		Message sentMessage = sendPhotoMessage(chatId, image, content.render(result.status()));
		if (sentMessage == null) {
			return;
		}
		TelegramEcho echo = new TelegramEcho(chatId, sentMessage.getMessageId());
		this.pendingDeliveries.register(result.messageId(), echo, result.status()).ifPresent(this::applyCaptionEdit);
	}

	private void applyCaptionEdit(CaptionEdit edit) {
		if (editCaption(edit.echo(), edit.caption())) {
			this.pendingDeliveries.markDisplayed(edit);
		}
	}

	/**
	 * Renders the Lovebox image for the given message.
	 * @throws UnsupportedMessageException if the message carries nothing this bot can put
	 * on the box; the exception message is sent back to the chat
	 */
	private LoveboxImage renderImage(Message message, String caption) {
		if (message.hasPhoto()) {
			Path photo = downloadPhoto(message);
			try {
				return this.imageService.renderPhoto(photo.toFile(), caption);
			}
			finally {
				delete(photo);
			}
		}
		if (message.hasText()) {
			return this.imageService.renderText(caption);
		}
		throw new UnsupportedMessageException("I can only put text messages and photos on the Lovebox.");
	}

	/**
	 * Downloads the largest available size of a photo into a temporary file this class
	 * owns.
	 * <p>
	 * Deliberately not {@code TelegramClient.downloadFile}: that creates the temporary
	 * file itself and, when the copy fails halfway, throws without deleting it and
	 * without handing the caller a reference - one orphaned file per failed download,
	 * slowly filling the container disk. Streaming into a file created here keeps cleanup
	 * possible on every path.
	 */
	private Path downloadPhoto(Message message) {
		List<PhotoSize> photoSizes = message.getPhoto();
		PhotoSize photoSize = photoSizes.get(photoSizes.size() - 1);

		Path target = createTempFile();
		try {
			String filePath = this.telegramClient.execute(new GetFile(photoSize.getFileId())).getFilePath();
			try (InputStream photo = this.telegramClient.downloadFileAsStream(filePath)) {
				Files.copy(photo, target, StandardCopyOption.REPLACE_EXISTING);
			}
			log.debug("Downloaded photo \"{}\" from {}", photoSize.getFileId(), filePath);
			return target;
		}
		catch (IOException | TelegramApiException | RuntimeException ex) {
			delete(target);
			throw new IllegalStateException(
					"Could not download photo \"%s\" from Telegram".formatted(photoSize.getFileId()), ex);
		}
	}

	private static Path createTempFile() {
		try {
			return Files.createTempFile("lovebox-photo", null);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static void delete(Path file) {
		try {
			Files.deleteIfExists(file);
		}
		catch (IOException ex) {
			log.warn("Could not delete the downloaded photo {}: {}", file, ex.getMessage());
		}
	}

	protected boolean sendTextMessage(long chatId, String text) {
		String textMessage = text != null ? text : "";
		SendMessage message = new SendMessage(String.valueOf(chatId), textMessage);
		try {
			this.telegramClient.execute(message);
			log.atDebug()
				.addArgument(() -> textMessage.replace("\n", " "))
				.addArgument(chatId)
				.log("Sent message \"{}\" to {}");
			return true;
		}
		catch (TelegramApiException | RuntimeException ex) {
			log.error("Failed to send message to {} due to error: {}", chatId, ex.getMessage(), ex);
			return false;
		}
	}

	protected Message sendPhotoMessage(long chatId, LoveboxImage image, String caption) {
		SendPhoto message = new SendPhoto(String.valueOf(chatId),
				new InputFile(new ByteArrayInputStream(image.png()), "image.png"));
		message.setCaption(caption);
		try {
			Message sentMessage = this.telegramClient.execute(message);
			log.debug("Sent photo message to {}", chatId);
			return sentMessage;
		}
		catch (TelegramApiException | RuntimeException ex) {
			log.error("Failed to send photo message to {} due to error: {}", chatId, ex.getMessage(), ex);
			return null;
		}
	}

	protected boolean editCaption(TelegramEcho echo, String caption) {
		EditMessageCaption editMessage = EditMessageCaption.builder()
			.messageId(echo.messageId())
			.chatId(String.valueOf(echo.chatId()))
			.caption(caption)
			.build();
		try {
			this.telegramClient.execute(editMessage);
			log.debug("Updated the caption of message {} in chat {}", echo.messageId(), echo.chatId());
			return true;
		}
		catch (TelegramApiException | RuntimeException ex) {
			log.error("Failed to update the caption of message {} in chat {} due to error: {}", echo.messageId(),
					echo.chatId(), ex.getMessage(), ex);
			return false;
		}
	}

	@Override
	public String getBotToken() {
		return this.botProperties.token();
	}

	@Override
	public LongPollingUpdateConsumer getUpdatesConsumer() {
		return this;
	}

}
