package com.patbaumgartner.lovebox.telegram.sender.lovebox;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoveboxServiceTests {

	@Mock
	private LoveboxRestClient restClient;

	private LoveboxService service(LoveboxRestClientProperties properties) {
		return new LoveboxService(properties, this.restClient, JsonMapper.builder().build());
	}

	private LoveboxService enabledService() {
		return service(enabledProperties());
	}

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

	private GraphqlRequestBody captureRequest() {
		ArgumentCaptor<GraphqlRequestBody> captor = ArgumentCaptor.forClass(GraphqlRequestBody.class);
		verify(this.restClient).graphql(any(), captor.capture());
		return captor.getValue();
	}

	@Test
	void sendImageMessageReturnsSimulatedResultWhenDisabled() {
		SendResult result = service(disabledProperties()).sendImageMessage("data:image/png;base64,AAAA");

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

		SendResult result = enabledService().sendImageMessage("data:image/png;base64,AAAA");

		assertThat(result.messageId()).isEqualTo("message-1");
		assertThat(result.status()).isEqualTo("sending");
		assertThat(result.sentAt()).isEqualTo(Instant.parse("2023-01-01T17:55:34.890Z"));
	}

	@Test
	void sendImageMessageFailsLoudlyOnMalformedResponse() {
		stubLogin();
		when(this.restClient.graphql(any(), any())).thenReturn(ResponseEntity.ok("{\"data\":{}}"));

		assertThatIllegalStateException()
			.isThrownBy(() -> enabledService().sendImageMessage("data:image/png;base64,AAAA"))
			.withMessageContaining("sendPixNote");
	}

	@Test
	void sendImageMessageFailsWhenTheStatusListIsEmpty() {
		stubLogin();
		when(this.restClient.graphql(any(), any()))
			.thenReturn(ResponseEntity.ok("{\"data\":{\"sendPixNote\":{\"_id\":\"m1\",\"statusList\":[]}}}"));

		assertThatIllegalStateException()
			.isThrownBy(() -> enabledService().sendImageMessage("data:image/png;base64,AAAA"))
			.withMessageContaining("statusList[0]");
	}

	@Test
	void neverEchoesTheResponseBodyIntoAnErrorMessage() {
		stubLogin();
		when(this.restClient.graphql(any(), any()))
			.thenReturn(ResponseEntity.ok("{\"data\":{\"other\":{\"base64\":\"SECRET-IMAGE-PAYLOAD\"}}}"));

		assertThatIllegalStateException()
			.isThrownBy(() -> enabledService().sendImageMessage("data:image/png;base64,AAAA"))
			.withMessageNotContaining("SECRET-IMAGE-PAYLOAD");
	}

	@Test
	void graphqlErrorsAreReportedEvenWithHttp200() {
		stubLogin();
		String json = """
				{"errors":[{"message":"Box not found"},{"message":"Not authorised"}],"data":null}
				""";
		when(this.restClient.graphql(any(), any())).thenReturn(ResponseEntity.ok(json));

		assertThatIllegalStateException().isThrownBy(() -> enabledService().getMessages())
			.withMessageContaining("Box not found")
			.withMessageContaining("Not authorised");
	}

	@Test
	void tokenIsCachedAcrossCalls() {
		stubLogin();
		when(this.restClient.graphql(any(), any())).thenReturn(ResponseEntity.ok("{\"data\":{\"getMessages\":[]}}"));
		LoveboxService service = enabledService();

		service.getMessages();
		service.getMessages();

		verify(this.restClient, times(1)).loginWithPassword(any());
	}

	@Test
	void unauthorizedResponseRefreshesTokenAndRetriesOnce() {
		stubLogin();
		HttpClientErrorException unauthorized = HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized",
				HttpHeaders.EMPTY, new byte[0], null);
		when(this.restClient.graphql(any(), any())).thenThrow(unauthorized)
			.thenReturn(ResponseEntity.ok("{\"data\":{\"getMessages\":[]}}"));

		assertThat(enabledService().getMessages()).isEmpty();

		verify(this.restClient, times(2)).loginWithPassword(any());
		verify(this.restClient, times(2)).graphql(any(), any());
	}

	@Test
	void otherClientErrorsAreNotRetried() {
		stubLogin();
		HttpClientErrorException badRequest = HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request",
				HttpHeaders.EMPTY, new byte[0], null);
		when(this.restClient.graphql(any(), any())).thenThrow(badRequest);
		LoveboxService service = enabledService();

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

		assertThat(enabledService().getMessages()).containsExactly(new MessageStatus("message-1", "read"),
				new MessageStatus("message-2", "sending"));
	}

	@Test
	void getMessagesSkipsEntriesWithoutAnIdOrStatus() {
		stubLogin();
		String json = """
				{"data":{"getMessages":[
				{"_id":"message-1","status":{"label":"read"}},
				{"_id":"message-2"},
				{"status":{"label":"sending"}},
				{"_id":"message-4","status":{"label":null}}]}}
				""";
		when(this.restClient.graphql(any(), any())).thenReturn(ResponseEntity.ok(json));

		assertThat(enabledService().getMessages()).containsExactly(new MessageStatus("message-1", "read"));
	}

	@Test
	void getMessagesReturnsEmptyListWhenDataIsNull() {
		stubLogin();
		when(this.restClient.graphql(any(), any())).thenReturn(ResponseEntity.ok("{\"data\":{\"getMessages\":null}}"));

		assertThat(enabledService().getMessages()).isEmpty();
	}

	@Test
	void getMessagesReturnsEmptyListWhenDisabled() {
		assertThat(service(disabledProperties()).getMessages()).isEmpty();
	}

	@Test
	void getMessagesRequestsOnlyTheIdAndStatusLabel() {
		stubLogin();
		when(this.restClient.graphql(any(), any())).thenReturn(ResponseEntity.ok("{\"data\":{\"getMessages\":[]}}"));

		enabledService().getMessages();

		assertThat(captureRequest().query()).contains("_id")
			.contains("status")
			.contains("label")
			.doesNotContain("base64")
			.doesNotContain("bytes")
			.doesNotContain("frames")
			.doesNotContain("drawing")
			.doesNotContain("postcardAddress")
			.doesNotContain("senderUser");
	}

	@Test
	void pendingHeartDoesNotAcknowledgeTheHeart() {
		stubLogin();
		when(this.restClient.graphql(any(), any()))
			.thenReturn(ResponseEntity.ok("{\"data\":{\"getHeartsRain\":{\"_id\":\"heart-1\"}}}"));

		assertThat(enabledService().pendingHeart()).isEqualTo("heart-1");

		verify(this.restClient, times(1)).graphql(any(), any());
	}

	@Test
	void pendingHeartReturnsNullWhenNoneArePending() {
		stubLogin();
		when(this.restClient.graphql(any(), any()))
			.thenReturn(ResponseEntity.ok("{\"data\":{\"getHeartsRain\":null}}"));

		assertThat(enabledService().pendingHeart()).isNull();
	}

	@Test
	void pendingHeartReturnsNullWhenDisabled() {
		assertThat(service(disabledProperties()).pendingHeart()).isNull();

		verify(this.restClient, never()).graphql(any(), any());
	}

	@Test
	void acknowledgeHeartSendsTheHeartId() {
		stubLogin();
		when(this.restClient.graphql(any(), any()))
			.thenReturn(ResponseEntity.ok("{\"data\":{\"setHeartsRain\":true}}"));

		enabledService().acknowledgeHeart("heart-1");

		GraphqlRequestBody request = captureRequest();
		assertThat(request.operationName()).isEqualTo("setHeartsRain");
		assertThat(request.variables()).isEqualTo(Map.of("heartId", "heart-1"));
	}

	@Test
	void initializeVerifiesTheAccountAndRegistersTheDevice() {
		stubLogin();
		when(this.restClient.checkEmail(any()))
			.thenReturn(ResponseEntity.ok(new CheckEmailResponseBody(true, "First")));
		when(this.restClient.graphql(any(), any()))
			.thenReturn(ResponseEntity.ok("{\"data\":{\"me\":{\"_id\":\"u\"}}}"));

		assertThat(enabledService().initializeIfNeeded()).isTrue();

		verify(this.restClient, times(3)).graphql(any(), any());
	}

	@Test
	void initializeRunsOnlyOnceAfterItSucceeded() {
		stubLogin();
		when(this.restClient.checkEmail(any()))
			.thenReturn(ResponseEntity.ok(new CheckEmailResponseBody(true, "First")));
		when(this.restClient.graphql(any(), any()))
			.thenReturn(ResponseEntity.ok("{\"data\":{\"me\":{\"_id\":\"u\"}}}"));
		LoveboxService service = enabledService();

		service.initializeIfNeeded();
		service.initializeIfNeeded();

		verify(this.restClient, times(1)).checkEmail(any());
	}

	@Test
	void initializeRunsOnlyOnceWhenTheStartupRunnerAndThePollRaceEachOther() throws Exception {
		stubLogin();
		when(this.restClient.checkEmail(any()))
			.thenReturn(ResponseEntity.ok(new CheckEmailResponseBody(true, "First")));
		when(this.restClient.graphql(any(), any()))
			.thenReturn(ResponseEntity.ok("{\"data\":{\"me\":{\"_id\":\"u\"}}}"));
		LoveboxService service = enabledService();
		int threads = 8;
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(threads);

		try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
			for (int i = 0; i < threads; i++) {
				executor.execute(() -> {
					try {
						start.await();
						service.initializeIfNeeded();
					}
					catch (InterruptedException ex) {
						Thread.currentThread().interrupt();
					}
					finally {
						done.countDown();
					}
				});
			}
			start.countDown();
			assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
		}

		verify(this.restClient, times(1)).checkEmail(any());
		verify(this.restClient, times(3)).graphql(any(), any());
	}

	@Test
	void initializeIsRetriedAfterAFailure() {
		stubLogin();
		when(this.restClient.checkEmail(any())).thenThrow(new IllegalStateException("API down"))
			.thenReturn(ResponseEntity.ok(new CheckEmailResponseBody(true, "First")));
		when(this.restClient.graphql(any(), any()))
			.thenReturn(ResponseEntity.ok("{\"data\":{\"me\":{\"_id\":\"u\"}}}"));
		LoveboxService service = enabledService();

		assertThatThrownBy(service::initializeIfNeeded).isInstanceOf(IllegalStateException.class);

		assertThat(service.initializeIfNeeded()).isTrue();
	}

	@Test
	void initializeReportsAnUnknownAccount() {
		when(this.restClient.checkEmail(any())).thenReturn(ResponseEntity.ok(new CheckEmailResponseBody(false, null)));

		assertThat(enabledService().initializeIfNeeded()).isFalse();

		verify(this.restClient, never()).graphql(any(), any());
	}

	@Test
	void initializeReportsAnEmptyCheckEmailBody() {
		when(this.restClient.checkEmail(any())).thenReturn(ResponseEntity.ok().build());

		assertThat(enabledService().initializeIfNeeded()).isFalse();
	}

	@Test
	void initializeDoesNothingWhenDisabled() {
		assertThat(service(disabledProperties()).initializeIfNeeded()).isTrue();

		verify(this.restClient, never()).checkEmail(any());
	}

	@Test
	void loginFailureWithEmptyBodyThrows() {
		when(this.restClient.loginWithPassword(any())).thenReturn(ResponseEntity.ok().build());
		LoveboxService service = enabledService();

		assertThatIllegalStateException().isThrownBy(service::getMessages).withMessageContaining("login");
	}

}
