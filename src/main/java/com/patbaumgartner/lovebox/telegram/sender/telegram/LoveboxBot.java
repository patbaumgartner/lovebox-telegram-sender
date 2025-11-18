package com.patbaumgartner.lovebox.telegram.sender.telegram;

import com.patbaumgartner.lovebox.telegram.sender.services.ImageService;
import com.patbaumgartner.lovebox.telegram.sender.services.LoveboxService;
import com.patbaumgartner.lovebox.telegram.sender.utils.Pair;
import com.patbaumgartner.lovebox.telegram.sender.utils.Tripple;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.BotSession;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.AfterBotRegistration;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoveboxBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

	private final LoveboxBotProperties botProperties;

	private final ImageService imageService;

	private final LoveboxService loveboxService;

	private final Set<Long> chatIds = new TreeSet<>();

	private final ConcurrentHashMap<String, String> loveboxMessageStore = new ConcurrentHashMap<>();

	private final ConcurrentHashMap<String, Collection<Pair<Long, Message>>> telegramMessageStore = new ConcurrentHashMap<>();

	private TelegramClient telegramClient;

	@PostConstruct
	public void init() {
		telegramClient = new OkHttpTelegramClient(getBotToken());
	}

	@Scheduled(fixedRate = 20_000)
	public void readMessageBox() {
		List<Pair<String, String>> messages = loveboxService.getMessages();
		messages.forEach(p -> {
			loveboxMessageStore.computeIfPresent(p.left(), (key, value) -> {
				if (!value.equals(p.right())) {
					Collection<Pair<Long, Message>> pairs = telegramMessageStore.get(p.left());
					if (pairs != null) {
						for (Pair<Long, Message> pair : pairs) {
							Message message = pair.right();
							if (message != null) {
								updatePhotoMessageCaption(message, p.right());
							}
						}
					}
				}
				return value;
			});
			loveboxMessageStore.put(p.left(), p.right());
		});
	}

	@Scheduled(fixedRate = 20_000)
	public void receiveWaterfallOfHearts() {
		String heartsRainId = loveboxService.receiveWaterfallOfHearts();
		if (heartsRainId != null) {
			chatIds.forEach(chatId -> sendTextMessage(chatId, "You received a waterfall of hearts! ❤❤❤"));
		}
	}

	@Override
	public void consume(Update update) {
		if (update.hasMessage()) {

			// Retrieve Message
			Message message = update.getMessage();
			chatIds.add(message.getChat().getId());

			// Suppress Telegrams "/start" command
			String text = message.getText();
			if (text != null && text.startsWith("/start")) {
				return;
			}

			Pair<String, byte[]> imagePair = null;

			// Create Lovebox Image
			try {
				if (message.hasPhoto()) {
					File file = downloadImageFromPhotoMessage(message);
					text = message.getCaption();
					imagePair = imageService.resizeImageToPair(file, text);
				}

				if (message.hasText()) {
					imagePair = imageService.createTextImageToPair(text);
				}

				// Set default message
				if (imagePair == null) {
					imagePair = imageService.createFixedImageToPair();
				}
			}
			catch (RuntimeException e) {
				// Suppress exception
				log.error("Exception occurred: {}", e.getMessage(), e);
			}

			Tripple<String, LocalDateTime, String> statusTripple = loveboxService.sendImageMessage(imagePair.left());
			loveboxMessageStore.put(statusTripple.left(), statusTripple.right());

			// Send/respond Message
			for (long chatId : chatIds) {
				Message sentMessage = sendPhotoMessage(chatId, text, imagePair, statusTripple);
				telegramMessageStore
					.compute(statusTripple.left(), (key, value) -> (value == null) ? new ArrayList<>() : value)
					.add(new Pair<>(chatId, sentMessage));
			}
		}
	}

	protected File downloadImageFromPhotoMessage(Message message) {
		List<PhotoSize> photoSizes = message.getPhoto();
		PhotoSize photoSize = photoSizes.get(photoSizes.size() - 1);

		GetFile getFile = new GetFile(photoSize.getFileId());
		try {
			String filePath = telegramClient.execute(getFile).getFilePath();
			File file = telegramClient.downloadFile(filePath);
			log.debug("Download photo \"{}\" from {}", photoSize.getFileId(), filePath);
			return file;
		}
		catch (TelegramApiException | RuntimeException e) {
			log.error("Failed to download photo \"{}\" due to error: {}", photoSize.getFileId(), e.getMessage(), e);
		}
		return null;
	}

	protected void sendTextMessage(long chatId, String text) {
		String textMessage = text != null ? text : "";
		SendMessage message = new SendMessage(String.valueOf(chatId), textMessage);
		try {
			telegramClient.execute(message);
			log.atDebug()
				.addArgument(() -> textMessage.replaceAll("\n", " "))
				.addArgument(chatId)
				.log("Sent message \"{}\" to {}");
		}
		catch (TelegramApiException | RuntimeException e) {
			log.error("Failed to send message \"{}\" to {} due to error: {}", textMessage, chatId, e.getMessage(), e);
		}
	}

	protected Message sendPhotoMessage(long chatId, String text, Pair<String, byte[]> imagePair,
			Tripple<String, LocalDateTime, String> statusTripple) {
		String textMessage = text != null ? text : "";
		SendPhoto message = new SendPhoto(String.valueOf(chatId),
				new InputFile(new ByteArrayInputStream(imagePair.right()), "image.png"));

		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
		String formattedDateTime = ZonedDateTime.of(statusTripple.middle(), ZoneId.of("Europe/London"))
			.format(formatter);
		String caption = "Message: \"%s\" \nStatus: [%s].\nExecuted: %s".formatted(
				textMessage != null ? textMessage.replaceAll("\n", " ") : "", statusTripple.right(), formattedDateTime);
		message.setCaption(caption);

		Message sentMessage = null;
		try {
			sentMessage = telegramClient.execute(message);
			log.atDebug()
				.addArgument(() -> textMessage.replaceAll("\n", " "))
				.addArgument(chatId)
				.log("Sent message \"{}\" to {}");
		}
		catch (TelegramApiException | RuntimeException e) {
			log.error("Failed to send message \"{}\" to {} due to error: {}", textMessage, chatId, e.getMessage(), e);
		}
		return sentMessage;
	}

	protected void updatePhotoMessageCaption(Message message, String status) {
		String text = message.getCaption().replaceAll("\\[.*\\]\\.", "[" + status + "].");
		String chatId = String.valueOf(message.getChatId());
		EditMessageCaption editMessage = EditMessageCaption.builder()
			.messageId(message.getMessageId())
			.chatId(chatId)
			.caption(text)
			.build();
		try {
			telegramClient.execute(editMessage);
			log.atDebug()
				.addArgument(() -> text.replaceAll("\n", " "))
				.addArgument(chatId)
				.log("Sent message \"{}\" to {}");
		}
		catch (TelegramApiException | RuntimeException e) {
			log.error("Failed to send message \"{}\" to {} due to error: {}", text, chatId, e.getMessage(), e);
		}
	}

	@AfterBotRegistration
	public void afterRegistration(BotSession botSession) {
		log.info("Registered TelegramBot with Username: {} running state is: {}", botProperties.getUsername(),
				botSession.isRunning());
	}

	@Override
	public String getBotToken() {
		return botProperties.getToken();
	}

	@Override
	public LongPollingUpdateConsumer getUpdatesConsumer() {
		return this;
	}

}
