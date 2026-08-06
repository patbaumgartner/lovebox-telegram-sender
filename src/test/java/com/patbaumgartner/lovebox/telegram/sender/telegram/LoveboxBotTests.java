package com.patbaumgartner.lovebox.telegram.sender.telegram;

import java.time.Instant;
import java.util.List;

import com.patbaumgartner.lovebox.telegram.sender.image.ImageService;
import com.patbaumgartner.lovebox.telegram.sender.image.LoveboxImage;
import com.patbaumgartner.lovebox.telegram.sender.lovebox.LoveboxService;
import com.patbaumgartner.lovebox.telegram.sender.lovebox.MessageStatus;
import com.patbaumgartner.lovebox.telegram.sender.lovebox.SendResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageCaption;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoveboxBotTests {

	private static final LoveboxImage IMAGE = new LoveboxImage("data:image/png;base64,QUJD", new byte[] { 1, 2, 3 });

	private static final SendResult SEND_RESULT = new SendResult("message-1", Instant.parse("2024-01-01T10:00:00Z"),
			"sending");

	@Mock
	private ImageService imageService;

	@Mock
	private LoveboxService loveboxService;

	@Mock
	private TelegramClient telegramClient;

	private LoveboxBot bot;

	@BeforeEach
	void createBot() {
		this.bot = new LoveboxBot(new LoveboxBotProperties("lovebox_bot", "token"), this.imageService,
				this.loveboxService, this.telegramClient);
	}

	private static Update updateWithMessage(Message message) {
		Update update = mock(Update.class);
		when(update.hasMessage()).thenReturn(true);
		when(update.getMessage()).thenReturn(message);
		return update;
	}

	private static Message textMessage(long chatId, String text) {
		Message message = mock(Message.class);
		when(message.getChatId()).thenReturn(chatId);
		when(message.getText()).thenReturn(text);
		lenient().when(message.hasText()).thenReturn(text != null);
		lenient().when(message.hasPhoto()).thenReturn(false);
		return message;
	}

	@Test
	void consumeIgnoresUpdatesWithoutMessage() {
		Update update = mock(Update.class);
		when(update.hasMessage()).thenReturn(false);

		this.bot.consume(update);

		verifyNoInteractions(this.imageService, this.loveboxService, this.telegramClient);
	}

	@Test
	void consumeIgnoresStartCommand() {
		this.bot.consume(updateWithMessage(textMessage(7L, "/start")));

		verifyNoInteractions(this.imageService, this.loveboxService, this.telegramClient);
	}

	@Test
	void consumeSendsTextMessageAsImageAndEchoesIt() throws TelegramApiException {
		when(this.imageService.renderText("hello")).thenReturn(IMAGE);
		when(this.loveboxService.sendImageMessage(IMAGE.dataUri())).thenReturn(SEND_RESULT);
		Message sentMessage = mock(Message.class);
		when(this.telegramClient.execute(any(SendPhoto.class))).thenReturn(sentMessage);

		this.bot.consume(updateWithMessage(textMessage(7L, "hello")));

		ArgumentCaptor<SendPhoto> photoCaptor = ArgumentCaptor.forClass(SendPhoto.class);
		verify(this.telegramClient).execute(photoCaptor.capture());
		assertThat(photoCaptor.getValue().getChatId()).isEqualTo("7");
		assertThat(photoCaptor.getValue().getCaption()).contains("hello").contains("[sending]");
	}

	@Test
	void consumeUsesFallbackImageForUnsupportedMessageTypes() throws TelegramApiException {
		Message message = mock(Message.class);
		when(message.getChatId()).thenReturn(7L);
		when(message.getText()).thenReturn(null);
		when(message.hasText()).thenReturn(false);
		when(message.hasPhoto()).thenReturn(false);
		when(this.imageService.renderFallback()).thenReturn(IMAGE);
		when(this.loveboxService.sendImageMessage(IMAGE.dataUri())).thenReturn(SEND_RESULT);
		when(this.telegramClient.execute(any(SendPhoto.class))).thenReturn(mock(Message.class));

		this.bot.consume(updateWithMessage(message));

		verify(this.imageService).renderFallback();
		verify(this.loveboxService).sendImageMessage(IMAGE.dataUri());
	}

	@Test
	void consumeUsesFallbackImageWhenRenderingFails() throws TelegramApiException {
		when(this.imageService.renderText("boom")).thenThrow(new IllegalStateException("render failed"));
		when(this.imageService.renderFallback()).thenReturn(IMAGE);
		when(this.loveboxService.sendImageMessage(IMAGE.dataUri())).thenReturn(SEND_RESULT);
		when(this.telegramClient.execute(any(SendPhoto.class))).thenReturn(mock(Message.class));

		this.bot.consume(updateWithMessage(textMessage(7L, "boom")));

		verify(this.imageService).renderFallback();
	}

	@Test
	void consumeApologizesWhenSendingToLoveboxFails() throws TelegramApiException {
		when(this.imageService.renderText("hello")).thenReturn(IMAGE);
		when(this.loveboxService.sendImageMessage(IMAGE.dataUri())).thenThrow(new IllegalStateException("API down"));

		this.bot.consume(updateWithMessage(textMessage(7L, "hello")));

		ArgumentCaptor<SendMessage> messageCaptor = ArgumentCaptor.forClass(SendMessage.class);
		verify(this.telegramClient).execute(messageCaptor.capture());
		assertThat(messageCaptor.getValue().getText()).contains("could not process");
	}

	@Test
	void readMessageBoxUpdatesCaptionOnStatusChange() throws TelegramApiException {
		when(this.imageService.renderText("hello")).thenReturn(IMAGE);
		when(this.loveboxService.sendImageMessage(IMAGE.dataUri())).thenReturn(SEND_RESULT);
		Message sentMessage = mock(Message.class);
		when(sentMessage.getCaption()).thenReturn("Message: \"hello\" \nStatus: [sending].\nExecuted: now");
		when(sentMessage.getChatId()).thenReturn(7L);
		when(sentMessage.getMessageId()).thenReturn(42);
		when(this.telegramClient.execute(any(SendPhoto.class))).thenReturn(sentMessage);
		this.bot.consume(updateWithMessage(textMessage(7L, "hello")));

		when(this.loveboxService.getMessages()).thenReturn(List.of(new MessageStatus("message-1", "read")));
		this.bot.readMessageBox();
		this.bot.readMessageBox();

		ArgumentCaptor<EditMessageCaption> editCaptor = ArgumentCaptor.forClass(EditMessageCaption.class);
		// Only the first poll sees a status transition; the second is a no-op.
		verify(this.telegramClient).execute(editCaptor.capture());
		assertThat(editCaptor.getValue().getCaption()).contains("[read]");
		assertThat(editCaptor.getValue().getMessageId()).isEqualTo(42);
	}

	@Test
	void readMessageBoxSkipsCaptionUpdateWhenCaptionIsMissing() throws TelegramApiException {
		when(this.imageService.renderText("hello")).thenReturn(IMAGE);
		when(this.loveboxService.sendImageMessage(IMAGE.dataUri())).thenReturn(SEND_RESULT);
		Message sentMessage = mock(Message.class);
		when(sentMessage.getCaption()).thenReturn(null);
		when(this.telegramClient.execute(any(SendPhoto.class))).thenReturn(sentMessage);
		this.bot.consume(updateWithMessage(textMessage(7L, "hello")));

		when(this.loveboxService.getMessages()).thenReturn(List.of(new MessageStatus("message-1", "read")));
		this.bot.readMessageBox();

		verify(this.telegramClient, never()).execute(any(EditMessageCaption.class));
	}

	@Test
	void receiveWaterfallOfHeartsNotifiesKnownChats() throws TelegramApiException {
		when(this.imageService.renderText("hello")).thenReturn(IMAGE);
		when(this.loveboxService.sendImageMessage(IMAGE.dataUri())).thenReturn(SEND_RESULT);
		when(this.telegramClient.execute(any(SendPhoto.class))).thenReturn(mock(Message.class));
		this.bot.consume(updateWithMessage(textMessage(7L, "hello")));

		when(this.loveboxService.receiveWaterfallOfHearts()).thenReturn("heart-1");
		this.bot.receiveWaterfallOfHearts();

		ArgumentCaptor<SendMessage> messageCaptor = ArgumentCaptor.forClass(SendMessage.class);
		verify(this.telegramClient).execute(messageCaptor.capture());
		assertThat(messageCaptor.getValue().getChatId()).isEqualTo("7");
		assertThat(messageCaptor.getValue().getText()).contains("waterfall of hearts");
	}

	@Test
	void receiveWaterfallOfHeartsDoesNothingWithoutPendingHearts() {
		when(this.loveboxService.receiveWaterfallOfHearts()).thenReturn(null);

		this.bot.receiveWaterfallOfHearts();

		verifyNoInteractions(this.telegramClient);
	}

}
