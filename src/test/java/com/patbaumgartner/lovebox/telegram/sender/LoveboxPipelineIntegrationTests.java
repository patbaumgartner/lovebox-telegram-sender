package com.patbaumgartner.lovebox.telegram.sender;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.patbaumgartner.lovebox.telegram.sender.telegram.LoveboxBot;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageCaption;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Drives a real Telegram update through the whole application: property binding, the
 * declarative HTTP client, Jackson (de)serialisation, image rendering, caption rendering
 * and delivery tracking. Only the two external endpoints are substituted - the Lovebox
 * API by a local HTTP server speaking the real protocol, and the Telegram client by a
 * mock.
 */
@SpringBootTest(properties = { "bot.enabled=false", "bot.token=test-token", "bot.allowed-chat-ids=7",
		"lovebox.enabled=true", "lovebox.email=me@example.com", "lovebox.password=secret", "lovebox.device-id=device-1",
		"lovebox.box-id=box-1", "lovebox.signature=Signature", "lovebox.poll-interval=1h",
		"lovebox.api-url=http://localhost:${lovebox.test.port}" })
class LoveboxPipelineIntegrationTests {

	private static final List<String> GRAPHQL_REQUESTS = new CopyOnWriteArrayList<>();

	private static volatile String deliveryStatus = "sending";

	private static final HttpServer SERVER = startStubApi();

	@Autowired
	private LoveboxBot bot;

	@MockitoBean
	private TelegramClient telegramClient;

	private static HttpServer startStubApi() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
			server.createContext("/v1/auth/checkEmail",
					exchange -> respond(exchange, "{\"existingUser\":true,\"firstName\":\"First\"}"));
			server.createContext("/v1/auth/loginWithPassword", exchange -> respond(exchange,
					"{\"_id\":\"u1\",\"firstName\":\"First\",\"email\":\"me@example.com\",\"token\":\"jwt\"}"));
			server.createContext("/v1/graphql", LoveboxPipelineIntegrationTests::handleGraphql);
			server.start();
			System.setProperty("lovebox.test.port", String.valueOf(server.getAddress().getPort()));
			return server;
		}
		catch (IOException ex) {
			throw new IllegalStateException("Could not start the stub Lovebox API", ex);
		}
	}

	private static void handleGraphql(HttpExchange exchange) throws IOException {
		String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		GRAPHQL_REQUESTS.add(request);
		if (request.contains("sendPixNote")) {
			respond(exchange, """
					{"data":{"sendPixNote":{"_id":"lovebox-1",
					"statusList":[{"label":"sending","date":"2024-01-01T10:00:00Z"}]}}}""");
		}
		else if (request.contains("getMessages")) {
			respond(exchange, "{\"data\":{\"getMessages\":[{\"_id\":\"lovebox-1\",\"status\":{\"label\":\"%s\"}}]}}"
				.formatted(deliveryStatus));
		}
		else if (request.contains("getHeartsRain")) {
			respond(exchange, "{\"data\":{\"getHeartsRain\":null}}");
		}
		else {
			respond(exchange, "{\"data\":{\"ok\":true}}");
		}
	}

	private static void respond(HttpExchange exchange, String body) throws IOException {
		byte[] payload = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(200, payload.length);
		try (OutputStream output = exchange.getResponseBody()) {
			output.write(payload);
		}
	}

	@AfterAll
	static void stopStubApi() {
		SERVER.stop(0);
		System.clearProperty("lovebox.test.port");
	}

	@BeforeEach
	void resetStub() {
		GRAPHQL_REQUESTS.clear();
		deliveryStatus = "sending";
	}

	private static Update textUpdate(long chatId, String text) {
		Message message = mock(Message.class);
		when(message.getChatId()).thenReturn(chatId);
		when(message.getText()).thenReturn(text);
		when(message.hasText()).thenReturn(true);
		when(message.hasPhoto()).thenReturn(false);
		Update update = mock(Update.class);
		when(update.hasMessage()).thenReturn(true);
		when(update.getMessage()).thenReturn(message);
		return update;
	}

	@Test
	void putsAMessageOnTheLoveboxAndFollowsItsDeliveryStatus() throws TelegramApiException {
		Message echo = mock(Message.class);
		when(echo.getMessageId()).thenReturn(4711);
		when(this.telegramClient.execute(any(SendPhoto.class))).thenReturn(echo);

		this.bot.consume(textUpdate(7L, "I love you 🚀\nto the moon and back"));

		String sendRequest = GRAPHQL_REQUESTS.stream()
			.filter(request -> request.contains("sendPixNote"))
			.findFirst()
			.orElseThrow();
		assertThat(sendRequest).contains("data:image/png;base64,").contains("box-1").doesNotContain("postcardText");

		ArgumentCaptor<SendPhoto> photoCaptor = ArgumentCaptor.forClass(SendPhoto.class);
		verify(this.telegramClient).execute(photoCaptor.capture());
		assertThat(photoCaptor.getValue().getChatId()).isEqualTo("7");
		assertThat(photoCaptor.getValue().getCaption()).contains("I love you 🚀 to the moon and back")
			.contains("[sending]");

		deliveryStatus = "read";
		this.bot.updateDeliveryStatuses();

		ArgumentCaptor<EditMessageCaption> editCaptor = ArgumentCaptor.forClass(EditMessageCaption.class);
		verify(this.telegramClient).execute(editCaptor.capture());
		assertThat(editCaptor.getValue().getCaption()).contains("[read]");
		assertThat(editCaptor.getValue().getMessageId()).isEqualTo(4711);
	}

	@Test
	void refusesAChatThatIsNotOnTheAllowlist() {
		this.bot.consume(textUpdate(999L, "let me in"));

		assertThat(GRAPHQL_REQUESTS).noneMatch(request -> request.contains("sendPixNote"));
	}

}
