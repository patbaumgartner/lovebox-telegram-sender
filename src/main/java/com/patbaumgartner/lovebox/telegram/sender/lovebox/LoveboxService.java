package com.patbaumgartner.lovebox.telegram.sender.lovebox;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * High-level operations against the Lovebox API: sending rendered images, polling message
 * delivery statuses and acknowledging received "waterfalls of hearts".
 * <p>
 * The GraphQL documents below are modelled on the ones the mobile app sends, but request
 * only the fields this application actually reads. That matters: the app's own
 * {@code getMessages} selection includes {@code base64}, {@code bytes}, {@code frames}
 * and {@code drawing { base64 }}, so polling it every 20 seconds would download the full
 * image payload of the ten most recent messages over and over.
 * <p>
 * The bearer token obtained from {@code loginWithPassword} is cached for
 * {@link #TOKEN_TTL} and refreshed transparently, including a single retry when the API
 * answers with {@code 401 Unauthorized}. Without the cache every scheduled poll would
 * perform a fresh login.
 * <p>
 * When {@code lovebox.enabled=false} all operations are no-ops (or return simulated
 * results), which keeps the GraalVM AOT training run and local development offline.
 */
@Service
public class LoveboxService {

	private static final Logger log = LoggerFactory.getLogger(LoveboxService.class);

	private static final Duration TOKEN_TTL = Duration.ofMinutes(30);

	/** The mobile app version this client identifies itself as. */
	private static final String APP_VERSION = "5.4.9";

	/** How many recent messages are polled for delivery status updates. */
	private static final int MESSAGE_PAGE_SIZE = 10;

	private static final String ME_QUERY = """
			{
			  me {
			    _id
			  }
			}
			""";

	private static final String SET_DEVICE_QUERY = """
			mutation setDevice($deviceId: String!, $deviceParams: JSON) {
			  setDevice(deviceId: $deviceId, deviceParams: $deviceParams) {
			    _id
			  }
			}
			""";

	private static final String SET_BOX_SIGNATURE_QUERY = """
			mutation setBoxSignature($boxId: String, $signature: String) {
			  setBoxSignature(boxId: $boxId, signature: $signature)
			}
			""";

	private static final String SEND_PIX_NOTE_QUERY = """
			mutation sendPixNote($channel: ChannelsTypes, $appVersion: String, $base64: String, \
			$recipient: String, $options: JSON, $contentType: [String], $timezone: Int) {
			  sendPixNote(channel: $channel, appVersion: $appVersion, base64: $base64, \
			recipient: $recipient, options: $options, contentType: $contentType, timezone: $timezone) {
			    _id
			    statusList {
			      label
			      date
			    }
			  }
			}
			""";

	private static final String GET_HEARTS_RAIN_QUERY = """
			query getHeartsRain {
			  getHeartsRain {
			    _id
			  }
			}
			""";

	private static final String SET_HEARTS_RAIN_QUERY = """
			mutation setHeartsRain($heartId: String!) {
			  setHeartsRain(heartId: $heartId)
			}
			""";

	private static final String GET_MESSAGES_QUERY = """
			query getMessages($getMessagesInput: GetMessagesInput) {
			  getMessages(getMessagesInput: $getMessagesInput) {
			    _id
			    status {
			      label
			    }
			  }
			}
			""";

	private final LoveboxRestClientProperties properties;

	private final LoveboxRestClient restClient;

	private final ObjectMapper objectMapper;

	private final Object tokenLock = new Object();

	private volatile CachedToken cachedToken;

	private volatile boolean initialized;

	public LoveboxService(LoveboxRestClientProperties properties, LoveboxRestClient restClient,
			ObjectMapper objectMapper) {
		this.properties = properties;
		this.restClient = restClient;
		this.objectMapper = objectMapper;
	}

	/**
	 * Verifies the configured account and mirrors what the mobile app does after login:
	 * register the device and apply the configured box signature.
	 * <p>
	 * Performs the calls at most once per successful run, so it can be invoked from the
	 * scheduled poll as well. That makes the registration self-healing: when the Lovebox
	 * API is unreachable while the container starts (a common ordering on a NAS), the
	 * next poll picks it up instead of leaving the device unregistered until the next
	 * restart.
	 * @return {@code true} once the account is verified and the device registered (or the
	 * integration is disabled), {@code false} if no account exists for the configured
	 * e-mail address
	 */
	public boolean initializeIfNeeded() {
		if (!this.properties.enabled() || this.initialized) {
			return true;
		}
		if (!accountExists()) {
			return false;
		}
		registerDeviceAndSignature();
		this.initialized = true;
		return true;
	}

	private boolean accountExists() {
		ResponseEntity<CheckEmailResponseBody> response = this.restClient
			.checkEmail(new CheckEmailRequestBody(this.properties.email()));
		log.debug("CheckEmail response status: {}", response.getStatusCode());
		CheckEmailResponseBody body = response.getBody();
		return body != null && Boolean.TRUE.equals(body.existingUser());
	}

	private void registerDeviceAndSignature() {
		graphql(null, Map.of(), ME_QUERY);

		Map<String, Object> deviceParams = new HashMap<>();
		deviceParams.put("os", "android");
		deviceParams.put("appVersion", APP_VERSION);
		deviceParams.put("model", "Nokia 7.2");
		deviceParams.put("osVersion", "10");
		deviceParams.put("hasNotch", false);
		deviceParams.put("deviceType", "Handset");
		graphql("setDevice", Map.of("deviceId", this.properties.deviceId(), "deviceParams", deviceParams),
				SET_DEVICE_QUERY);

		Map<String, Object> signature = new HashMap<>();
		signature.put("boxId", this.properties.boxId());
		signature.put("signature", this.properties.signature());
		graphql("setBoxSignature", signature, SET_BOX_SIGNATURE_QUERY);
	}

	/**
	 * Sends a rendered image to the configured Lovebox.
	 * @param imageAsBase64 the image as {@code data:image/png;base64,...} URI
	 * @return the send result; simulated when the integration is disabled
	 */
	public SendResult sendImageMessage(String imageAsBase64) {
		if (!this.properties.enabled()) {
			// When not calling the Lovebox API, we need to fake the message.
			return new SendResult(UUID.randomUUID().toString(), Instant.now(), "sending disabled");
		}

		Map<String, Object> options = new HashMap<>();
		options.put("deviceId", this.properties.deviceId());
		options.put("privacyPolicy", "ADMIN_AND_ME");

		Map<String, Object> variables = new HashMap<>();
		variables.put("channel", "LOVEBOX");
		variables.put("appVersion", APP_VERSION);
		variables.put("base64", imageAsBase64);
		variables.put("recipient", this.properties.boxId());
		variables.put("contentType", List.of());
		variables.put("options", options);
		variables.put("timezone", currentUtcOffsetMinutes());

		JsonNode sendPixNote = requireField(graphql("sendPixNote", variables, SEND_PIX_NOTE_QUERY), "sendPixNote");
		JsonNode status = sendPixNote.path("statusList").path(0);

		return new SendResult(requireString(sendPixNote, "_id", "sendPixNote"),
				Instant.parse(requireString(status, "date", "sendPixNote.statusList[0]")),
				requireString(status, "label", "sendPixNote.statusList[0]"));
	}

	/**
	 * Fetches the delivery status of the most recent messages sent to the box.
	 * @return the message statuses, newest first; empty when the integration is disabled
	 */
	public List<MessageStatus> getMessages() {
		if (!this.properties.enabled()) {
			return List.of();
		}

		Map<String, Object> input = Map.of("recipient", this.properties.boxId(), "limit", MESSAGE_PAGE_SIZE, "skip", 0);
		JsonNode messages = graphql("getMessages", Map.of("getMessagesInput", input), GET_MESSAGES_QUERY)
			.path("getMessages");
		if (!messages.isArray()) {
			return List.of();
		}

		List<MessageStatus> statuses = new ArrayList<>(messages.size());
		for (JsonNode message : messages) {
			String id = message.path("_id").stringValue(null);
			String label = message.path("status").path("label").stringValue(null);
			if (id != null && label != null) {
				statuses.add(new MessageStatus(id, label));
			}
		}
		return statuses;
	}

	/**
	 * Fetches a pending "waterfall of hearts" sent from the Lovebox, without
	 * acknowledging it.
	 * @return the heart identifier, or {@code null} if no hearts are pending
	 * @see #acknowledgeHeart(String)
	 */
	public String pendingHeart() {
		if (!this.properties.enabled()) {
			return null;
		}
		return graphql("getHeartsRain", Map.of(), GET_HEARTS_RAIN_QUERY).path("getHeartsRain")
			.path("_id")
			.stringValue(null);
	}

	/**
	 * Acknowledges a "waterfall of hearts" so the API stops reporting it.
	 * <p>
	 * Deliberately separate from {@link #pendingHeart()}: acknowledging before the
	 * notification reached a Telegram chat would silently swallow the event.
	 * @param heartId the identifier returned by {@link #pendingHeart()}
	 */
	public void acknowledgeHeart(String heartId) {
		if (!this.properties.enabled()) {
			return;
		}
		graphql("setHeartsRain", Map.of("heartId", heartId), SET_HEARTS_RAIN_QUERY);
	}

	/**
	 * Executes a GraphQL operation and returns its {@code data} object, refreshing the
	 * bearer token and retrying once when the API answers {@code 401 Unauthorized}.
	 */
	private JsonNode graphql(String operationName, Map<String, Object> variables, String query) {
		GraphqlRequestBody request = new GraphqlRequestBody(operationName, variables, query);
		String body;
		try {
			body = post(request);
		}
		catch (RestClientResponseException ex) {
			if (ex.getStatusCode().value() != HttpStatus.UNAUTHORIZED.value()) {
				throw ex;
			}
			log.debug("Lovebox API replied 401 for {}; refreshing token and retrying once", operationName);
			invalidateToken();
			body = post(request);
		}
		return parseData(body, operationName);
	}

	private String post(GraphqlRequestBody request) {
		ResponseEntity<String> response = this.restClient.graphql(authorization(), request);
		log.debug("GraphQL {} response status: {}", request.operationName(), response.getStatusCode());
		return response.getBody();
	}

	/**
	 * Extracts the {@code data} object of a GraphQL response.
	 * <p>
	 * GraphQL reports failures with HTTP 200 and an {@code errors} array, so the status
	 * code alone proves nothing. Response bodies are never included in the exception
	 * message: they carry the base64 image payload and account details.
	 */
	private JsonNode parseData(String responseBody, String operationName) {
		String operation = operationName != null ? operationName : "query";
		if (responseBody == null || responseBody.isBlank()) {
			throw new IllegalStateException("Empty response from the Lovebox API for " + operation);
		}
		JsonNode root = this.objectMapper.readTree(responseBody);
		JsonNode errors = root.path("errors");
		if (errors.isArray() && !errors.isEmpty()) {
			throw new IllegalStateException(
					"Lovebox API reported an error for %s: %s".formatted(operation, describe(errors)));
		}
		JsonNode data = root.path("data");
		if (!data.isObject()) {
			throw new IllegalStateException(
					"Unexpected Lovebox API response for %s: no data object".formatted(operation));
		}
		return data;
	}

	private static JsonNode requireField(JsonNode data, String field) {
		JsonNode value = data.path(field);
		if (!value.isObject()) {
			throw new IllegalStateException("Unexpected Lovebox API response: data.%s is missing".formatted(field));
		}
		return value;
	}

	private static String requireString(JsonNode node, String field, String path) {
		String value = node.path(field).stringValue(null);
		if (value == null) {
			throw new IllegalStateException(
					"Unexpected Lovebox API response: %s.%s is missing or not a string".formatted(path, field));
		}
		return value;
	}

	private static String describe(JsonNode errors) {
		List<String> messages = new ArrayList<>(errors.size());
		for (JsonNode error : errors) {
			messages.add(error.path("message").stringValue("unknown error"));
		}
		return String.join("; ", messages);
	}

	private String authorization() {
		CachedToken current = this.cachedToken;
		if (current == null || current.isExpired()) {
			synchronized (this.tokenLock) {
				current = this.cachedToken;
				if (current == null || current.isExpired()) {
					current = new CachedToken(login(), Instant.now());
					this.cachedToken = current;
				}
			}
		}
		return "Bearer " + current.token();
	}

	private void invalidateToken() {
		synchronized (this.tokenLock) {
			this.cachedToken = null;
		}
	}

	private String login() {
		ResponseEntity<LoginWithPasswordResponseBody> response = this.restClient
			.loginWithPassword(new LoginWithPasswordRequestBody(this.properties.email(), this.properties.password()));
		log.debug("Login with password response status: {}", response.getStatusCode());
		LoginWithPasswordResponseBody body = response.getBody();
		if (body == null || body.token() == null) {
			throw new IllegalStateException("Lovebox login failed: response contained no token");
		}
		return body.token();
	}

	private static int currentUtcOffsetMinutes() {
		return ZoneId.systemDefault().getRules().getOffset(Instant.now()).getTotalSeconds() / 60;
	}

	private record CachedToken(String token, Instant obtainedAt) {

		boolean isExpired() {
			return this.obtainedAt.plus(TOKEN_TTL).isBefore(Instant.now());
		}

	}

}
