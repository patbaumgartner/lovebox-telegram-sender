package com.patbaumgartner.lovebox.telegram.sender.telegram;

import com.patbaumgartner.lovebox.telegram.sender.services.ImageService;
import com.patbaumgartner.lovebox.telegram.sender.services.LoveboxService;
import com.patbaumgartner.lovebox.telegram.sender.utils.Pair;
import com.patbaumgartner.lovebox.telegram.sender.utils.Tripple;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageCaption;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoveboxBot extends TelegramLongPollingBot {

    private final LoveboxBotProperties botProperties;
    private final ImageService imageService;
    private final LoveboxService loveboxService;

    private final Set<Long> chatIds = new TreeSet<>();
    private final ConcurrentHashMap<String, String> loveboxMessageStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Message> telegramMessageStore = new ConcurrentHashMap<>();

    @Scheduled(fixedRate = 10_000)
    public void readMessageBox() {
        List<Pair<String, String>> messages = loveboxService.getMessagesByBox();
        messages.forEach(p -> {
            loveboxMessageStore.computeIfPresent(p.left(), (key, value) -> {
                if (!value.equals(p.right())) {
                    Message message = telegramMessageStore.get(p.left());
                    if (message != null) {
                        updatePhotoMessageCaption(message, p.right());
                    }
                }
                return value;
            });
            loveboxMessageStore.put(p.left(), p.right());
        });
    }

    @Scheduled(fixedRate = 10_000)
    public void receiveWaterfallOfHearts() {
        if (loveboxService.receiveWaterfallOfHearts()) {
            chatIds.forEach(chatId -> sendTextMessage(chatId, "You received a waterfall of hearts! ❤❤❤"));
        }
    }

    @Override
    public void onUpdateReceived(Update update) {
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
            } catch (RuntimeException e) {
                // Suppress exception
                log.error("Exception occurred: {}", e.getMessage(), e);
            }

            Tripple<String, LocalDateTime, String> statusTripple = loveboxService.sendImageMessage(imagePair.left());
            loveboxMessageStore.put(statusTripple.left(), statusTripple.right());

            // Send/respond Message
            for (long chatId : chatIds) {
                Message sentMessage = sendPhotoMessage(chatId, text, imagePair, statusTripple);
                telegramMessageStore.putIfAbsent(statusTripple.left(), sentMessage);
            }
        }
    }

    protected File downloadImageFromPhotoMessage(Message message) {
        List<PhotoSize> photoSizes = message.getPhoto();
        PhotoSize photoSize = photoSizes.get(photoSizes.size() - 1);

        GetFile getFile = new GetFile();
        getFile.setFileId(photoSize.getFileId());
        try {
            String filePath = execute(getFile).getFilePath();
            File file = downloadFile(filePath);
            log.debug("Download photo \"{}\" from {}", photoSize.getFileId(), filePath);
            return file;
        } catch (TelegramApiException | RuntimeException e) {
            log.error("Failed to download photo \"{}\" due to error: {}", photoSize.getFileId(), e.getMessage());
        }
        return null;
    }

    protected void sendTextMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        try {
            execute(message);
            log.debug("Sent message \"{}\" to {}", text.replaceAll("\n", " "), chatId);
        } catch (TelegramApiException | RuntimeException e) {
            log.error("Failed to send message \"{}\" to {} due to error: {}", text, chatId, e.getMessage());
        }
    }

    protected Message sendPhotoMessage(long chatId, String text, Pair<String, byte[]> imagePair, Tripple<String, LocalDateTime, String> statusTripple) {
        SendPhoto message = new SendPhoto();
        message.setChatId(String.valueOf(chatId));
        message.setPhoto(new InputFile(new ByteArrayInputStream(imagePair.right()), "image.png"));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
        String formattedDateTime = ZonedDateTime.of(statusTripple.middle(), ZoneId.of("Europe/London"))
            .format(formatter);
        String caption = String.format("Message: \"%s\" \nStatus: [%s].\nExecuted: %s",
            text != null ? text.replaceAll("\n", " ") : "",
            statusTripple.right(),
            formattedDateTime);
        message.setCaption(caption);

        Message sentMessage = null;
        try {
            sentMessage = execute(message);
            log.debug("Sent message \"{}\" to {}", text.replaceAll("\n", " "), chatId);
        } catch (TelegramApiException | RuntimeException e) {
            log.error("Failed to send message \"{}\" to {} due to error: {}", text, chatId, e.getMessage());
        }
        return sentMessage;
    }

    protected void updatePhotoMessageCaption(Message message, String status) {
        String text = message.getCaption().replaceAll("\\[.*\\]\\.", "[" + status + "].");
        String chatId = String.valueOf(message.getChatId());
        EditMessageCaption editMessage = EditMessageCaption.builder()
            .messageId(message.getMessageId())
            .chatId(chatId)
            .caption(text).build();
        try {
            execute(editMessage);
            log.debug("Sent message \"{}\" to {}", text.replaceAll("\n", " "), chatId);
        } catch (TelegramApiException | RuntimeException e) {
            log.error("Failed to send message \"{}\" to {} due to error: {}", text, chatId, e.getMessage());
        }
    }

    @Override
    public void onRegister() {
        log.info("Registering TelegramBot with Username: {}", getBotUsername());
    }

    @Override
    public String getBotUsername() {
        return botProperties.getUsername();
    }

    @Override
    public String getBotToken() {
        return botProperties.getToken();
    }
}