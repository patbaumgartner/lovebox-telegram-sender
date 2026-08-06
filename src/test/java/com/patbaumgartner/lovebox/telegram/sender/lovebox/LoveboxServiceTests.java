package com.patbaumgartner.lovebox.telegram.sender.lovebox;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoveboxServiceTests {

	@Mock
	private LoveboxRestClient restClient;

	private static LoveboxRestClientProperties enabledProperties() {
		return new LoveboxRestClientProperties(true, "me@example.com", "secret", "device-1", "box-1", "Signature",
				"https://api.example.invalid");
	}

	private static LoveboxRestClientProperties disabledProperties() {
		return new LoveboxRestClientProperties(false, null, null, null, null, null, "https://api.example.invalid");
	}

	private void stubLogin() {
		when(this.restClient.loginWithPassword(any())).thenReturn(
				ResponseEntity.ok(new LoginWithPasswordResponseBody("user-1", "First", "me@example.com", "token-1")));
	}

	@Test
	void sendImageMessageReturnsSimulatedResultWhenDisabled() {
		LoveboxService service = new LoveboxService(disabledProperties(), this.restClient);

		SendResult result = service.sendImageMessage("data:image/png;base64,AAAA");

		assertThat(result.messageId()).isNotBlank();
		assertThat(result.status()).isEqualTo("sending disabled");
	}

	@Test
	void sendImageMessageParsesApiResponse() {
		stubLogin();
		String json = """
				{"data":{"sendPixNote":{"_id":"message-1",
				"statusList":[{"label":"sending","date":"2023-01-01T17:55:34.890Z"}]}}}
				""";
		when(this.restClient.graphql(eq("Bearer token-1"), any())).thenReturn(ResponseEntity.ok(json));
		LoveboxService service = new LoveboxService(enabledProperties(), this.restClient);

		SendResult result = service.sendImageMessage("data:image/png;base64,AAAA");

		assertThat(result.messageId()).isEqualTo("message-1");
		assertThat(result.status()).isEqualTo("sending");
		assertThat(result.sentAt()).isEqualTo(Instant.parse("2023-01-01T17:55:34.890Z"));
	}

	@Test
	void sendImageMessageFailsLoudlyOnMalformedResponse() {
		stubLogin();
		when(this.restClient.graphql(any(), any())).thenReturn(ResponseEntity.ok("{\"data\":{}}"));
		LoveboxService service = new LoveboxService(enabledProperties(), this.restClient);

		assertThatIllegalStateException().isThrownBy(() -> service.sendImageMessage("data:image/png;base64,AAAA"))
			.withMessageContaining("sendPixNote");
	}

	@Test
	void tokenIsCachedAcrossCalls() {
		stubLogin();
		String json = "{\"data\":{\"getMessages\":[]}}";
		when(this.restClient.graphql(any(), any())).thenReturn(ResponseEntity.ok(json));
		LoveboxService service = new LoveboxService(enabledProperties(), this.restClient);

		service.getMessages();
		service.getMessages();

		verify(this.restClient, times(1)).loginWithPassword(any());
	}

	@Test
	void unauthorizedResponseRefreshesTokenAndRetriesOnce() {
		stubLogin();
		String json = "{\"data\":{\"getMessages\":[]}}";
		HttpClientErrorException unauthorized = HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized",
				HttpHeaders.EMPTY, new byte[0], null);
		when(this.restClient.graphql(any(), any())).thenThrow(unauthorized).thenReturn(ResponseEntity.ok(json));
		LoveboxService service = new LoveboxService(enabledProperties(), this.restClient);

		assertThat(service.getMessages()).isEmpty();

		verify(this.restClient, times(2)).loginWithPassword(any());
		verify(this.restClient, times(2)).graphql(any(), any());
	}

	@Test
	void otherClientErrorsAreNotRetried() {
		stubLogin();
		HttpClientErrorException badRequest = HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request",
				HttpHeaders.EMPTY, new byte[0], null);
		when(this.restClient.graphql(any(), any())).thenThrow(badRequest);
		LoveboxService service = new LoveboxService(enabledProperties(), this.restClient);

		assertThatThrownBy(service::getMessages).isInstanceOf(HttpClientErrorException.class);

		verify(this.restClient, times(1)).graphql(any(), any());
	}

	@Test
	void getMessagesParsesStatuses() {
		stubLogin();
		String json = """
				{"data":{"getMessages":[
				{"_id":"message-1","status":{"label":"read"}},
				{"_id":"message-2","status":{"label":"sending"}}]}}
				""";
		when(this.restClient.graphql(any(), any())).thenReturn(ResponseEntity.ok(json));
		LoveboxService service = new LoveboxService(enabledProperties(), this.restClient);

		assertThat(service.getMessages()).containsExactly(new MessageStatus("message-1", "read"),
				new MessageStatus("message-2", "sending"));
	}

	@Test
	void getMessagesReturnsEmptyListWhenDataIsNull() {
		stubLogin();
		when(this.restClient.graphql(any(), any())).thenReturn(ResponseEntity.ok("{\"data\":{\"getMessages\":null}}"));
		LoveboxService service = new LoveboxService(enabledProperties(), this.restClient);

		assertThat(service.getMessages()).isEmpty();
	}

	@Test
	void getMessagesReturnsEmptyListWhenDisabled() {
		LoveboxService service = new LoveboxService(disabledProperties(), this.restClient);

		assertThat(service.getMessages()).isEmpty();
	}

	@Test
	void receiveWaterfallOfHeartsAcknowledgesPendingHearts() {
		stubLogin();
		String heartsJson = "{\"data\":{\"getHeartsRain\":{\"_id\":\"heart-1\",\"sender\":\"someone\"}}}";
		String ackJson = "{\"data\":{\"setHeartsRain\":true}}";
		when(this.restClient.graphql(any(), any())).thenReturn(ResponseEntity.ok(heartsJson))
			.thenReturn(ResponseEntity.ok(ackJson));
		LoveboxService service = new LoveboxService(enabledProperties(), this.restClient);

		assertThat(service.receiveWaterfallOfHearts()).isEqualTo("heart-1");

		verify(this.restClient, times(2)).graphql(any(), any());
	}

	@Test
	void receiveWaterfallOfHeartsReturnsNullWhenNoneArePending() {
		stubLogin();
		when(this.restClient.graphql(any(), any()))
			.thenReturn(ResponseEntity.ok("{\"data\":{\"getHeartsRain\":null}}"));
		LoveboxService service = new LoveboxService(enabledProperties(), this.restClient);

		assertThat(service.receiveWaterfallOfHearts()).isNull();

		verify(this.restClient, times(1)).graphql(any(), any());
	}

	@Test
	void accountExistsReturnsTrueForExistingUser() {
		when(this.restClient.checkEmail(any()))
			.thenReturn(ResponseEntity.ok(new CheckEmailResponseBody(true, "First")));
		LoveboxService service = new LoveboxService(enabledProperties(), this.restClient);

		assertThat(service.accountExists()).isTrue();
	}

	@Test
	void accountExistsReturnsFalseForUnknownUser() {
		when(this.restClient.checkEmail(any())).thenReturn(ResponseEntity.ok(new CheckEmailResponseBody(false, null)));
		LoveboxService service = new LoveboxService(enabledProperties(), this.restClient);

		assertThat(service.accountExists()).isFalse();
	}

	@Test
	void accountExistsReturnsFalseForEmptyBody() {
		when(this.restClient.checkEmail(any())).thenReturn(ResponseEntity.ok().build());
		LoveboxService service = new LoveboxService(enabledProperties(), this.restClient);

		assertThat(service.accountExists()).isFalse();
	}

	@Test
	void loginFailureWithEmptyBodyThrows() {
		when(this.restClient.loginWithPassword(any())).thenReturn(ResponseEntity.ok().build());
		LoveboxService service = new LoveboxService(enabledProperties(), this.restClient);

		assertThatIllegalStateException().isThrownBy(service::getMessages).withMessageContaining("login");
	}

}
