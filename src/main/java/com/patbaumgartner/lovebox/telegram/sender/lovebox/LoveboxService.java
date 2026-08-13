package com.patbaumgartner.lovebox.telegram.sender.lovebox;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

/**
 * High-level operations against the Lovebox API: sending rendered images, polling message
 * delivery statuses and acknowledging received "waterfalls of hearts".
 * <p>
 * The bearer token obtained from {@code loginWithPassword} is cached for
 * {@link #TOKEN_TTL} and refreshed transparently, including a single retry when the API
 * answers with {@code 401 Unauthorized}. Without the cache every scheduled poll (2 tasks,
 * every 20 seconds) would perform a fresh login.
 * <p>
 * When {@code lovebox.enabled=false} all operations are no-ops (or return simulated
 * results), which keeps the GraalVM AOT training run and local development offline.
 */
@Service
public class LoveboxService {

	private static final Logger log = LoggerFactory.getLogger(LoveboxService.class);

	private static final Duration TOKEN_TTL = Duration.ofMinutes(30);

	private static final String ME_QUERY = "{\n  me {\n    _id\n    firstName\n    email\n    boxes {\n      _id\n      color\n      signature\n      lovePercentage\n      nickname\n      notifications {\n        disableUntil\n        messageRead\n        heartReceived\n        __typename\n      }\n      admin {\n        _id\n        firstName\n        email\n        __typename\n      }\n      privacyPolicy\n      pairingCode\n      isConnected\n      isAdmin\n      hardware\n      hasColor\n      hasColorBackup\n      connectionDate\n      __typename\n    }\n    relations {\n      _id\n      name\n      relationType\n      picture\n      color\n      streak\n      boxId\n      loveGoal\n      streakDeadline\n      reminders {\n        day\n        meridiem\n        number\n        weekday\n        time\n        __typename\n      }\n      specialDates {\n        _id\n        name\n        date\n        dateType\n        __typename\n      }\n      addresses {\n        firstname\n        lastname\n        streetAddress\n        zipCode\n        city\n        country\n        state\n        __typename\n      }\n      __typename\n    }\n    roles\n    device {\n      _id\n      appVersion\n      os\n      __typename\n    }\n    profile\n    reminder\n    premium\n    beta\n    fcmToken\n    language\n    loveCoins\n    __typename\n  }\n}\n";

	private static final String SET_DEVICE_QUERY = "mutation setDevice($deviceId: String!, $deviceParams: JSON) {\n  setDevice(deviceId: $deviceId, deviceParams: $deviceParams) {\n    _id\n    __typename\n  }\n}\n";

	private static final String SET_BOX_SIGNATURE_QUERY = "mutation setBoxSignature($boxId: String, $signature: String) {\n  setBoxSignature(boxId: $boxId, signature: $signature)\n}\n";

	private static final String SEND_PIX_NOTE_QUERY = "mutation sendPixNote($channel: ChannelsTypes, $appVersion: String, $postcardStripePaymentId: String, $postcardAddress: JSON, $postcardSettings: JSON, $postcardScheduledDate: Date, $postcardText: String, $base64: String, $recipient: String, $date: Date, $options: JSON, $contentType: [String], $timezone: Int, $promotionCode: String) {\n  sendPixNote(channel: $channel, appVersion: $appVersion, postcardStripePaymentId: $postcardStripePaymentId, postcardAddress: $postcardAddress, postcardSettings: $postcardSettings, postcardScheduledDate: $postcardScheduledDate, postcardText: $postcardText, base64: $base64, recipient: $recipient, date: $date, contentType: $contentType, timezone: $timezone, options: $options, promotionCode: $promotionCode) {\n    _id\n    channel\n    type\n    recipient\n    postcardStripePayment\n    postcardAddress {\n      firstname\n      lastname\n      country\n      state\n      streetAddress\n      city\n      zipCode\n      __typename\n    }\n    postcardSettings {\n      color\n      fontFamily\n      fontSize\n      __typename\n    }\n    recipientRelation\n    postcardText\n    url\n    date\n    status {\n      label\n      __typename\n    }\n    statusList {\n      label\n      date\n      __typename\n    }\n    senderUser {\n      _id\n      firstName\n      email\n      __typename\n    }\n    privacyPolicy\n    addedLoveCoins\n    __typename\n  }\n}\n";

	private static final String GET_HEARTS_RAIN_QUERY = "query getHeartsRain {\n  getHeartsRain {\n  _id\n  sender\n  __typename  \n}\n}\n";

	private static final String SET_HEARTS_RAIN_QUERY = "mutation setHeartsRain($heartId: String!) {\n  setHeartsRain(heartId:  $heartId)\n}\n";

	private static final String GET_MESSAGES_QUERY = "query getMessages($getMessagesInput: GetMessagesInput) {\n  getMessages(getMessagesInput: $getMessagesInput) {\n    _id\n    channel\n    content\n    type\n    recipient\n    date\n    status {\n      label\n      __typename\n    }\n    statusList {\n      label\n      date\n      __typename\n    }\n    drawing {\n      base64\n      rotate\n      __typename\n    }\n    base64\n    bytes\n    premium\n    textOnly\n    textCentered\n    gifId\n    url\n    urlId\n    frames\n    senderUser {\n      _id\n      firstName\n      email\n      __typename\n    }\n    privacyPolicy\n    postcardText\n    postcardAddress {\n      firstname\n      lastname\n      streetAddress\n      zipCode\n      city\n      country\n      state\n      __typename\n    }\n    postcardSettings {\n      color\n      fontFamily\n      fontSize\n      __typename\n    }\n    postcardScheduledDate\n    estimatedArrivalDate\n    __typename\n  }\n}\n";

	private final LoveboxRestClientProperties properties;

	private final LoveboxRestClient restClient;

	private final Object tokenLock = new Object();

	private volatile CachedToken cachedToken;

	public LoveboxService(LoveboxRestClientProperties properties, LoveboxRestClient restClient) {
		this.properties = properties;
		this.restClient = restClient;
	}

	/**
	 * Checks whether the configured account exists.
	 * @return {@code true} if the account exists (or the integration is disabled)
	 */
	public boolean accountExists() {
		if (!this.properties.enabled()) {
			return true;
		}
		ResponseEntity<CheckEmailResponseBody> response = this.restClient
			.checkEmail(new CheckEmailRequestBody(this.properties.email()));
		log.debug("CheckEmail response status: {}", response.getStatusCode());
		CheckEmailResponseBody body = response.getBody();
		return body != null && Boolean.TRUE.equals(body.existingUser());
	}

	/**
	 * Mimics the mobile app's device registration and applies the configured box
	 * signature. Safe to call repeatedly; all mutations are idempotent.
	 */
	public void registerDeviceAndSignature() {
		if (!this.properties.enabled()) {
			return;
		}
		graphql(null, null, ME_QUERY);

		Map<String, Object> deviceParams = new HashMap<>();
		deviceParams.put("os", "android");
		deviceParams.put("appVersion", "5.4.9");
		deviceParams.put("model", "Nokia 7.2");
		deviceParams.put("osVersion", "10");
		deviceParams.put("hasNotch", false);
		deviceParams.put("deviceType", "Handset");
		Map<String, Object> setDeviceVariables = new HashMap<>();
		setDeviceVariables.put("deviceId", this.properties.deviceId());
		setDeviceVariables.put("deviceParams", deviceParams);
		graphql("setDevice", setDeviceVariables, SET_DEVICE_QUERY);

		Map<String, Object> setBoxSignatureVariables = new HashMap<>();
		setBoxSignatureVariables.put("boxId", this.properties.boxId());
		setBoxSignatureVariables.put("signature", this.properties.signature());
		graphql("setBoxSignature", setBoxSignatureVariables, SET_BOX_SIGNATURE_QUERY);
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
		options.put("framesBase64", null);
		options.put("deviceId", this.properties.deviceId());
		options.put("privacyPolicy", "ADMIN_AND_ME");
		options.put("templateId", null);

		Map<String, Object> variables = new HashMap<>();
		variables.put("channel", "LOVEBOX");
		variables.put("base64", imageAsBase64);
		variables.put("recipient", this.properties.boxId());
		variables.put("contentType", new Object[] {});
		variables.put("options", options);
		variables.put("timezone", currentUtcOffsetMinutes());
		variables.put("appVersion", "5.4.9");

		String body = graphql("sendPixNote", variables, SEND_PIX_NOTE_QUERY);

		JsonElement element = dataField(body, "sendPixNote");
		if (!element.isJsonObject()) {
			throw new IllegalStateException("Unexpected sendPixNote response: " + body);
		}
		JsonObject sendPixNote = element.getAsJsonObject();
		JsonObject status = sendPixNote.getAsJsonArray("statusList").get(0).getAsJsonObject();

		String id = sendPixNote.get("_id").getAsString();
		String label = status.get("label").getAsString();
		Instant sentAt = Instant.parse(status.get("date").getAsString());

		return new SendResult(id, sentAt, label);
	}

	/**
	 * Fetches a pending "waterfall of hearts" sent from the Lovebox and acknowledges it.
	 * @return the heart identifier, or {@code null} if no hearts are pending
	 */
	public String receiveWaterfallOfHearts() {
		if (!this.properties.enabled()) {
			return null;
		}
		String body = graphql("getHeartsRain", new HashMap<>(), GET_HEARTS_RAIN_QUERY);

		JsonElement element = dataField(body, "getHeartsRain");
		if (!element.isJsonObject()) {
			return null;
		}
		String heartId = element.getAsJsonObject().get("_id").getAsString();

		// (Re)Set hearts rain to false, so it is reported only once.
		Map<String, Object> variables = new HashMap<>();
		variables.put("heartId", heartId);
		graphql("setHeartsRain", variables, SET_HEARTS_RAIN_QUERY);

		return heartId;
	}

	/**
	 * Fetches the delivery status of the most recent messages sent to the box.
	 * @return the message statuses, newest first; empty when the integration is disabled
	 */
	public List<MessageStatus> getMessages() {
		if (!this.properties.enabled()) {
			return List.of();
		}

		Map<String, Object> getMessagesInput = new HashMap<>();
		getMessagesInput.put("recipient", this.properties.boxId());
		getMessagesInput.put("limit", 10);
		getMessagesInput.put("skip", 0);
		Map<String, Object> variables = new HashMap<>();
		variables.put("getMessagesInput", getMessagesInput);

		String body = graphql("getMessages", variables, GET_MESSAGES_QUERY);

		JsonElement element = dataField(body, "getMessages");
		if (!element.isJsonArray()) {
			return List.of();
		}
		List<MessageStatus> messageStatus = new ArrayList<>();
		for (JsonElement item : element.getAsJsonArray()) {
			JsonObject message = item.getAsJsonObject();
			String id = message.get("_id").getAsString();
			String status = message.getAsJsonObject("status").get("label").getAsString();
			messageStatus.add(new MessageStatus(id, status));
		}
		return messageStatus;
	}

	private String graphql(String operationName, Object variables, String query) {
		GraphqlRequestBody request = new GraphqlRequestBody(operationName, variables, query);
		try {
			return executeGraphql(request);
		}
		catch (RestClientResponseException ex) {
			if (ex.getStatusCode().value() != HttpStatus.UNAUTHORIZED.value()) {
				throw ex;
			}
			log.debug("Lovebox API replied 401 for {}; refreshing token and retrying once", operationName);
			invalidateToken();
			return executeGraphql(request);
		}
	}

	private String executeGraphql(GraphqlRequestBody request) {
		ResponseEntity<String> response = this.restClient.graphql(authorization(), request);
		log.debug("GraphQL {} response status: {}", request.operationName(), response.getStatusCode());
		return response.getBody();
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

	/**
	 * Extracts {@code data.<field>} from a GraphQL response body, failing loudly on
	 * malformed responses instead of throwing an unspecific {@link NullPointerException}.
	 */
	private static JsonElement dataField(String responseBody, String field) {
		if (responseBody == null || responseBody.isBlank()) {
			throw new IllegalStateException("Empty response from Lovebox API for field: " + field);
		}
		JsonElement root = JsonParser.parseString(responseBody);
		JsonElement data = root.isJsonObject() ? root.getAsJsonObject().get("data") : null;
		JsonElement value = (data != null && data.isJsonObject()) ? data.getAsJsonObject().get(field) : null;
		if (value == null) {
			throw new IllegalStateException(
					"Unexpected Lovebox API response, missing data.%s: %s".formatted(field, responseBody));
		}
		return value;
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
