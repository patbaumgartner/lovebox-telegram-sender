package com.patbaumgartner.lovebox.telegram.sender.telegram;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import com.patbaumgartner.lovebox.telegram.sender.image.ImageService;
import com.patbaumgartner.lovebox.telegram.sender.image.LoveboxImage;
import com.patbaumgartner.lovebox.telegram.sender.lovebox.LoveboxService;
import com.patbaumgartner.lovebox.telegram.sender.lovebox.MessageStatus;
import com.patbaumgartner.lovebox.telegram.sender.lovebox.SendResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import org.telegram.telegrambots.longpolling.BotSession;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.AfterBotRegistration;
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
 * The Telegram bot bridging chats to the Lovebox: every text or photo message is rendered
 * as an image and sent to the box, the delivery status is polled and reflected back into
 * the Telegram message captions, and received "waterfalls of hearts" are announced to all
 * known chats.
 * <p>
 * Updates are processed one at a time on the dedicated background thread provided by
 * {@link DefaultLongPollingUpdateConsumer}; Spring closes the consumer (and its executor)
 * on context shutdown.
 */
@Component
public class LoveboxBot extends DefaultLongPollingUpdateConsumer implements SpringLongPollingBot {

	private static final Logger log = LoggerFactory.getLogger(LoveboxBot.class);

	private static final DateTimeFormatter CAPTION_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

	/** Above this size, entries no longer reported by the Lovebox API are evicted. */
	private static final int MESSAGE_STORE_EVICTION_THRESHOLD = 100;

	private final LoveboxBotProperties botProperties;

	private final ImageService imageService;

	private final LoveboxService loveboxService;

	private final TelegramClient telegramClient;

	private final Set<Long> chatIds = new ConcurrentSkipListSet<>();

	private final ConcurrentHashMap<String, String> loveboxMessageStore = new ConcurrentHashMap<>();

	private final ConcurrentHashMap<String, Collection<ChatMessage>> telegramMessageStore = new ConcurrentHashMap<>();

	public LoveboxBot(LoveboxBotProperties botProperties, ImageService imageService, LoveboxService loveboxService,
			TelegramClient telegramClient) {
		this.botProperties = botProperties;
		this.imageService = imageService;
		this.loveboxService = loveboxService;
		this.telegramClient = telegramClient;
	}

	/**
	 * Polls the delivery status of recent Lovebox messages and updates the captions of
	 * the corresponding Telegram messages on status changes (e.g. sending → read).
	 */
	@Scheduled(fixedRate = 20_000)
	public void readMessageBox() {
		List<MessageStatus> messages = this.loveboxService.getMessages();
		for (MessageStatus message : messages) {
			String previousStatus = this.loveboxMessageStore.put(message.messageId(), message.status());
			if (previousStatus != null && !previousStatus.equals(message.status())) {
				Collection<ChatMessage> recipients = this.telegramMessageStore.getOrDefault(message.messageId(),
						List.of());
				for (ChatMessage recipient : recipients) {
					updatePhotoMessageCaption(recipient.message(), message.status());
				}
			}
		}
		evictStaleEntries(messages);
	}

	/**
	 * Bounds the in-memory stores: entries for messages the Lovebox API no longer reports
	 * cannot receive further status updates and are dropped once the store grows beyond
	 * {@link #MESSAGE_STORE_EVICTION_THRESHOLD}.
	 */
	private void evictStaleEntries(List<MessageStatus> latestMessages) {
		if (latestMessages.isEmpty() || this.loveboxMessageStore.size() <= MESSAGE_STORE_EVICTION_THRESHOLD) {
			return;
		}
		Set<String> activeIds = latestMessages.stream().map(MessageStatus::messageId).collect(Collectors.toSet());
		this.loveboxMessageStore.keySet().removeIf(id -> !activeIds.contains(id));
		this.telegramMessageStore.keySet().removeIf(id -> !activeIds.contains(id));
	}

	@Scheduled(fixedRate = 20_000)
	public void receiveWaterfallOfHearts() {
		String heartId = this.loveboxService.pendingHeart();
		if (heartId == null) {
			return;
		}
		boolean delivered = false;
		for (long chatId : this.chatIds) {
			delivered |= sendTextMessage(chatId, "You received a waterfall of hearts! ❤❤❤");
		}
		// Acknowledge only after the news reached a chat, otherwise a Telegram
		// outage would swallow the event: the API reports each heart just once.
		if (delivered) {
			this.loveboxService.acknowledgeHeart(heartId);
		}
	}

	@Override
	public void consume(Update update) {
		if (!update.hasMessage()) {
			return;
		}

		Message message = update.getMessage();
		try {
			handleMessage(message);
		}
		catch (RuntimeException | LinkageError ex) {
			// LinkageError too: a missing native-image JNI/reflection registration
			// surfaces as an Error, which would otherwise kill this consumer thread
			// without a trace.
			log.error("Failed to process message from chat {}: {}", message.getChatId(), ex.getMessage(), ex);
			sendTextMessage(message.getChatId(), "Sorry, I could not process that message.");
		}
	}

	private void handleMessage(Message message) {
		this.chatIds.add(message.getChatId());

		// Suppress Telegram's "/start" command
		String text = message.getText();
		if (text != null && text.startsWith("/start")) {
			return;
		}

		String caption = message.hasPhoto() ? message.getCaption() : text;
		LoveboxImage image = renderImage(message, caption);

		SendResult result = this.loveboxService.sendImageMessage(image.dataUri());
		this.loveboxMessageStore.put(result.messageId(), result.status());

		// Echo the rendered image with its delivery status to every known chat
		for (long chatId : this.chatIds) {
			Message sentMessage = sendPhotoMessage(chatId, caption, image, result);
			if (sentMessage != null) {
				this.telegramMessageStore.computeIfAbsent(result.messageId(), key -> new CopyOnWriteArrayList<>())
					.add(new ChatMessage(chatId, sentMessage));
			}
		}
	}

	/**
	 * Renders the Lovebox image for the given message, falling back to the bundled
	 * default image for unsupported message types or failed photo downloads.
	 */
	private LoveboxImage renderImage(Message message, String caption) {
		try {
			if (message.hasPhoto()) {
				File photo = downloadImageFromPhotoMessage(message);
				if (photo != null) {
					return this.imageService.renderPhoto(photo, caption);
				}
			}
			else if (message.hasText()) {
				return this.imageService.renderText(caption);
			}
		}
		catch (RuntimeException ex) {
			log.error("Could not render image, using the fallback image: {}", ex.getMessage(), ex);
		}
		return this.imageService.renderFallback();
	}

	protected File downloadImageFromPhotoMessage(Message message) {
		List<PhotoSize> photoSizes = message.getPhoto();
		PhotoSize photoSize = photoSizes.get(photoSizes.size() - 1);

		GetFile getFile = new GetFile(photoSize.getFileId());
		try {
			String filePath = this.telegramClient.execute(getFile).getFilePath();
			File file = this.telegramClient.downloadFile(filePath);
			log.debug("Downloaded photo \"{}\" from {}", photoSize.getFileId(), filePath);
			return file;
		}
		catch (TelegramApiException | RuntimeException ex) {
			log.error("Failed to download photo \"{}\" due to error: {}", photoSize.getFileId(), ex.getMessage(), ex);
		}
		return null;
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
			log.error("Failed to send message \"{}\" to {} due to error: {}", textMessage, chatId, ex.getMessage(), ex);
			return false;
		}
	}

	protected Message sendPhotoMessage(long chatId, String text, LoveboxImage image, SendResult result) {
		String textMessage = text != null ? text : "";
		SendPhoto message = new SendPhoto(String.valueOf(chatId),
				new InputFile(new ByteArrayInputStream(image.png()), "image.png"));

		String formattedDateTime = result.sentAt().atZone(ZoneId.systemDefault()).format(CAPTION_TIME_FORMAT);
		String caption = "Message: \"%s\" \nStatus: [%s].\nExecuted: %s".formatted(textMessage.replace("\n", " "),
				result.status(), formattedDateTime);
		message.setCaption(caption);

		Message sentMessage = null;
		try {
			sentMessage = this.telegramClient.execute(message);
			log.atDebug()
				.addArgument(() -> textMessage.replace("\n", " "))
				.addArgument(chatId)
				.log("Sent photo message \"{}\" to {}");
		}
		catch (TelegramApiException | RuntimeException ex) {
			log.error("Failed to send photo message \"{}\" to {} due to error: {}", textMessage, chatId,
					ex.getMessage(), ex);
		}
		return sentMessage;
	}

	protected void updatePhotoMessageCaption(Message message, String status) {
		if (message == null || message.getCaption() == null) {
			return;
		}
		String text = message.getCaption().replaceAll("\\[.*]\\.", "[" + status + "].");
		String chatId = String.valueOf(message.getChatId());
		EditMessageCaption editMessage = EditMessageCaption.builder()
			.messageId(message.getMessageId())
			.chatId(chatId)
			.caption(text)
			.build();
		try {
			this.telegramClient.execute(editMessage);
			log.atDebug()
				.addArgument(() -> text.replace("\n", " "))
				.addArgument(chatId)
				.log("Updated caption to \"{}\" in chat {}");
		}
		catch (TelegramApiException | RuntimeException ex) {
			log.error("Failed to update caption \"{}\" in chat {} due to error: {}", text, chatId, ex.getMessage(), ex);
		}
	}

	@AfterBotRegistration
	public void afterRegistration(BotSession botSession) {
		log.info("Registered TelegramBot with Username: {} running state is: {}", this.botProperties.username(),
				botSession.isRunning());
	}

	@Override
	public String getBotToken() {
		return this.botProperties.token();
	}

	@Override
	public LongPollingUpdateConsumer getUpdatesConsumer() {
		return this;
	}

	/**
	 * A message echoed to a Telegram chat, remembered for later caption updates.
	 */
	private record ChatMessage(long chatId, Message message) {

	}

}
