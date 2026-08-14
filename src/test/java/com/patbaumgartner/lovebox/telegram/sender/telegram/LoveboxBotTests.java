package com.patbaumgartner.lovebox.telegram.sender.telegram;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.patbaumgartner.lovebox.telegram.sender.image.ImageService;
import com.patbaumgartner.lovebox.telegram.sender.image.LoveboxImage;
import com.patbaumgartner.lovebox.telegram.sender.image.UnsupportedMessageException;
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
import static org.mockito.Mockito.times;
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

	private static LoveboxBotProperties properties(EchoMode echoMode, Long... allowedChatIds) {
		return new LoveboxBotProperties(true, "lovebox_bot", "token", Set.of(allowedChatIds), echoMode);
	}

	@BeforeEach
	void createBot() {
		this.bot = botWith(properties(EchoMode.SENDER, 7L));
	}

	private LoveboxBot botWith(LoveboxBotProperties properties) {
		return new LoveboxBot(properties, this.imageService, this.loveboxService, this.telegramClient);
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
		lenient().when(message.getText()).thenReturn(text);
		lenient().when(message.hasText()).thenReturn(text != null);
		lenient().when(message.hasPhoto()).thenReturn(false);
		return message;
	}

	private Message stubEcho(int messageId) throws TelegramApiException {
		Message sentMessage = mock(Message.class);
		lenient().when(sentMessage.getMessageId()).thenReturn(messageId);
		when(this.telegramClient.execute(any(SendPhoto.class))).thenReturn(sentMessage);
		return sentMessage;
	}

	private SendPhoto capturePhoto() throws TelegramApiException {
		ArgumentCaptor<SendPhoto> captor = ArgumentCaptor.forClass(SendPhoto.class);
		verify(this.telegramClient).execute(captor.capture());
		return captor.getValue();
	}

	private SendMessage captureText() throws TelegramApiException {
		ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
		verify(this.telegramClient).execute(captor.capture());
		return captor.getValue();
	}

	private EditMessageCaption captureEdit() throws TelegramApiException {
		ArgumentCaptor<EditMessageCaption> captor = ArgumentCaptor.forClass(EditMessageCaption.class);
		verify(this.telegramClient).execute(captor.capture());
		return captor.getValue();
	}

	@Test
	void consumeIgnoresUpdatesWithoutMessage() {
		Update update = mock(Update.class);
		when(update.hasMessage()).thenReturn(false);

		this.bot.consume(update);

		verifyNoInteractions(this.imageService, this.loveboxService, this.telegramClient);
	}

	@Test
	void refusesChatsThatAreNotOnTheAllowlist() throws TelegramApiException {
		this.bot.consume(updateWithMessage(textMessage(999L, "let me in")));

		SendMessage reply = captureText();
		assertThat(reply.getChatId()).isEqualTo("999");
		assertThat(reply.getText()).contains("private").contains("999");
		verifyNoInteractions(this.imageService, this.loveboxService);
	}

	@Test
	void neverPutsAnUnauthorisedMessageOnTheLovebox() {
		this.bot.consume(updateWithMessage(textMessage(999L, "print this on your box")));

		verifyNoInteractions(this.loveboxService);
	}

	@Test
	void neverEchoesToAChatThatOnlyTriedToTalkToTheBot() throws TelegramApiException {
		when(this.imageService.renderText("hello")).thenReturn(IMAGE);
		when(this.loveboxService.sendImageMessage(IMAGE.dataUri())).thenReturn(SEND_RESULT);
		stubEcho(42);

		this.bot.consume(updateWithMessage(textMessage(999L, "hi")));
		this.bot.consume(updateWithMessage(textMessage(7L, "hello")));

		assertThat(capturePhoto().getChatId()).isEqualTo("7");
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
		stubEcho(42);

		this.bot.consume(updateWithMessage(textMessage(7L, "hello")));

		SendPhoto photo = capturePhoto();
		assertThat(photo.getChatId()).isEqualTo("7");
		assertThat(photo.getCaption()).contains("hello").contains("[sending]");
	}

	@Test
	void echoesOnlyToTheSenderByDefault() throws TelegramApiException {
		this.bot = botWith(properties(EchoMode.SENDER, 7L, 8L));
		when(this.imageService.renderText("hello")).thenReturn(IMAGE);
		when(this.loveboxService.sendImageMessage(IMAGE.dataUri())).thenReturn(SEND_RESULT);
		stubEcho(42);

		this.bot.consume(updateWithMessage(textMessage(7L, "hello")));

		assertThat(capturePhoto().getChatId()).isEqualTo("7");
	}

	@Test
	void echoesToEveryAllowedChatInAllAllowedMode() throws TelegramApiException {
		this.bot = botWith(properties(EchoMode.ALL_ALLOWED, 7L, 8L));
		when(this.imageService.renderText("hello")).thenReturn(IMAGE);
		when(this.loveboxService.sendImageMessage(IMAGE.dataUri())).thenReturn(SEND_RESULT);
		stubEcho(42);

		this.bot.consume(updateWithMessage(textMessage(7L, "hello")));

		ArgumentCaptor<SendPhoto> captor = ArgumentCaptor.forClass(SendPhoto.class);
		verify(this.telegramClient, times(2)).execute(captor.capture());
		assertThat(captor.getAllValues()).extracting(SendPhoto::getChatId).containsExactlyInAnyOrder("7", "8");
	}

	@Test
	void consumeRejectsUnsupportedMessageTypesWithoutTouchingTheLovebox() throws TelegramApiException {
		Message message = mock(Message.class);
		when(message.getChatId()).thenReturn(7L);
		when(message.getText()).thenReturn(null);
		when(message.hasText()).thenReturn(false);
		when(message.hasPhoto()).thenReturn(false);

		this.bot.consume(updateWithMessage(message));

		assertThat(captureText().getText()).contains("text messages and photos");
		verifyNoInteractions(this.loveboxService);
	}

	@Test
	void consumeForwardsARejectionReasonToTheSender() throws TelegramApiException {
		when(this.imageService.renderText("boom"))
			.thenThrow(new UnsupportedMessageException("That message is too long to show on the Lovebox."));

		this.bot.consume(updateWithMessage(textMessage(7L, "boom")));

		assertThat(captureText().getText()).isEqualTo("That message is too long to show on the Lovebox.");
		verifyNoInteractions(this.loveboxService);
	}

	@Test
	void consumeApologizesForUnexpectedRenderingFailures() throws TelegramApiException {
		when(this.imageService.renderText("boom")).thenThrow(new IllegalStateException("render failed"));

		this.bot.consume(updateWithMessage(textMessage(7L, "boom")));

		assertThat(captureText().getText()).contains("could not process");
	}

	@Test
	void consumeApologizesWhenSendingToLoveboxFails() throws TelegramApiException {
		when(this.imageService.renderText("hello")).thenReturn(IMAGE);
		when(this.loveboxService.sendImageMessage(IMAGE.dataUri())).thenThrow(new IllegalStateException("API down"));

		this.bot.consume(updateWithMessage(textMessage(7L, "hello")));

		assertThat(captureText().getText()).contains("could not process");
	}

	@Test
	void updateDeliveryStatusesRewritesTheCaptionOnStatusChange() throws TelegramApiException {
		when(this.imageService.renderText("hello")).thenReturn(IMAGE);
		when(this.loveboxService.sendImageMessage(IMAGE.dataUri())).thenReturn(SEND_RESULT);
		stubEcho(42);
		this.bot.consume(updateWithMessage(textMessage(7L, "hello")));

		when(this.loveboxService.getMessages()).thenReturn(List.of(new MessageStatus("message-1", "read")));
		this.bot.updateDeliveryStatuses();
		this.bot.updateDeliveryStatuses();

		EditMessageCaption edit = captureEdit();
		assertThat(edit.getCaption()).contains("[read]").contains("hello");
		assertThat(edit.getMessageId()).isEqualTo(42);
		assertThat(edit.getChatId()).isEqualTo("7");
	}

	@Test
	void neverRewritesSquareBracketsInsideTheSendersOwnText() throws TelegramApiException {
		when(this.imageService.renderText("meeting [today]. bring cake")).thenReturn(IMAGE);
		when(this.loveboxService.sendImageMessage(IMAGE.dataUri())).thenReturn(SEND_RESULT);
		stubEcho(42);
		this.bot.consume(updateWithMessage(textMessage(7L, "meeting [today]. bring cake")));

		when(this.loveboxService.getMessages()).thenReturn(List.of(new MessageStatus("message-1", "read")));
		this.bot.updateDeliveryStatuses();

		assertThat(captureEdit().getCaption()).contains("meeting [today]. bring cake").contains("[read]");
	}

	@Test
	void survivesRegularExpressionMetacharactersInTheSendersText() throws TelegramApiException {
		String tricky = "that costs $1 \\ [x].";
		when(this.imageService.renderText(tricky)).thenReturn(IMAGE);
		when(this.loveboxService.sendImageMessage(IMAGE.dataUri())).thenReturn(SEND_RESULT);
		stubEcho(42);
		this.bot.consume(updateWithMessage(textMessage(7L, tricky)));

		when(this.loveboxService.getMessages()).thenReturn(List.of(new MessageStatus("message-1", "read")));
		this.bot.updateDeliveryStatuses();

		assertThat(captureEdit().getCaption()).contains(tricky);
	}

	@Test
	void keepsCaptionsWithinTheTelegramLimitForVeryLongMessages() throws TelegramApiException {
		String longText = "love ".repeat(800).strip();
		when(this.imageService.renderText(longText)).thenReturn(IMAGE);
		when(this.loveboxService.sendImageMessage(IMAGE.dataUri())).thenReturn(SEND_RESULT);
		stubEcho(42);

		this.bot.consume(updateWithMessage(textMessage(7L, longText)));

		assertThat(capturePhoto().getCaption()).hasSizeLessThanOrEqualTo(CaptionContent.TELEGRAM_CAPTION_LIMIT);
	}

	@Test
	void correctsTheCaptionWhenTheStatusMovedOnBeforeTheEchoWasRegistered() throws TelegramApiException {
		when(this.imageService.renderText("hello")).thenReturn(IMAGE);
		when(this.loveboxService.sendImageMessage(IMAGE.dataUri())).thenReturn(SEND_RESULT);
		Message sentMessage = mock(Message.class);
		lenient().when(sentMessage.getMessageId()).thenReturn(42);
		// The poll observes "read" while the echo is still in flight, which used to
		// consume the only transition and leave the caption stuck at "sending".
		when(this.telegramClient.execute(any(SendPhoto.class))).thenAnswer(invocation -> {
			when(this.loveboxService.getMessages()).thenReturn(List.of(new MessageStatus("message-1", "read")));
			this.bot.updateDeliveryStatuses();
			return sentMessage;
		});

		this.bot.consume(updateWithMessage(textMessage(7L, "hello")));

		assertThat(captureEdit().getCaption()).contains("[read]");
	}

	@Test
	void announceWaterfallOfHeartsNotifiesAllowedChatsAndAcknowledges() throws TelegramApiException {
		when(this.loveboxService.pendingHeart()).thenReturn("heart-1");

		this.bot.announceWaterfallOfHearts();

		SendMessage notification = captureText();
		assertThat(notification.getChatId()).isEqualTo("7");
		assertThat(notification.getText()).contains("waterfall of hearts");
		verify(this.loveboxService).acknowledgeHeart("heart-1");
	}

	@Test
	void announceWaterfallOfHeartsKeepsTheHeartPendingWhenTelegramFails() throws TelegramApiException {
		when(this.loveboxService.pendingHeart()).thenReturn("heart-1");
		when(this.telegramClient.execute(any(SendMessage.class))).thenThrow(new TelegramApiException("offline"));

		this.bot.announceWaterfallOfHearts();

		verify(this.loveboxService, never()).acknowledgeHeart(any());
	}

	@Test
	void announceWaterfallOfHeartsDoesNothingWithoutPendingHearts() {
		when(this.loveboxService.pendingHeart()).thenReturn(null);

		this.bot.announceWaterfallOfHearts();

		verifyNoInteractions(this.telegramClient);
	}

	@Test
	void pollKeepsGoingWhenAStepFails() {
		when(this.loveboxService.initializeIfNeeded()).thenThrow(new IllegalStateException("API down"));
		when(this.loveboxService.getMessages()).thenThrow(new IllegalStateException("API down"));
		when(this.loveboxService.pendingHeart()).thenThrow(new IllegalStateException("API down"));

		this.bot.pollLovebox();

		verify(this.loveboxService).initializeIfNeeded();
		verify(this.loveboxService).getMessages();
		verify(this.loveboxService).pendingHeart();
	}

}
