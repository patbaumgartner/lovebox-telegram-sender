package com.patbaumgartner.lovebox.telegram.sender.services;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.patbaumgartner.lovebox.telegram.sender.rest.clients.*;
import com.patbaumgartner.lovebox.telegram.sender.utils.Pair;
import com.patbaumgartner.lovebox.telegram.sender.utils.Tripple;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoveboxService {

    private final LoveboxRestClientProperties restClientProperties;
    private final LoveboxRestClient restClient;

    public void checkIfUserExists() {
        // Check email user
        if (restClientProperties.isEnabled()) {
            ResponseEntity<CheckEmailResponseBody> checkEmailResponse = restClient.checkEmail(new CheckEmailRequestBody(restClientProperties.getEmail()));
            log.debug("CheckEmail response: {}", checkEmailResponse);
            CheckEmailResponseBody checkEmailResponseBody = checkEmailResponse.getBody();
            if (!checkEmailResponseBody.existingUser()) {
                throw new IllegalStateException(String.format("User %s does not exists!", restClientProperties.getEmail()));
            }
        }
    }

    public void simulateDeviceQueries() {
        if (restClientProperties.isEnabled()) {
            String token = loginAndResolveToken();

            // Me query
            String meQuery = "{\n  me {\n    _id\n    firstName\n    email\n    boxes {\n      _id\n      color\n      signature\n      lovePercentage\n      nickname\n      notifications {\n        disableUntil\n        messageRead\n        heartReceived\n        __typename\n      }\n      admin {\n        _id\n        firstName\n        email\n        __typename\n      }\n      privacyPolicy\n      pairingCode\n      isConnected\n      isAdmin\n      hardware\n      hasColor\n      hasColorBackup\n      connectionDate\n      __typename\n    }\n    relations {\n      _id\n      name\n      relationType\n      picture\n      color\n      streak\n      boxId\n      loveGoal\n      streakDeadline\n      reminders {\n        day\n        meridiem\n        number\n        weekday\n        time\n        __typename\n      }\n      specialDates {\n        _id\n        name\n        date\n        dateType\n        __typename\n      }\n      addresses {\n        firstname\n        lastname\n        streetAddress\n        zipCode\n        city\n        country\n        state\n        __typename\n      }\n      __typename\n    }\n    roles\n    device {\n      _id\n      appVersion\n      os\n      __typename\n    }\n    profile\n    reminder\n    premium\n    beta\n    fcmToken\n    language\n    loveCoins\n    __typename\n  }\n}\n";
            GraphqlRequestBody meGraphqlRequestBody = new GraphqlRequestBody(null, null, meQuery);
            ResponseEntity<String> meResponse = restClient.graphql("Bearer " + token, meGraphqlRequestBody);
            log.debug("Me response: {}", meResponse);

            // Set device query
            String setDeviceQuery = "mutation setDevice($deviceId: String!, $deviceParams: JSON) {\n  setDevice(deviceId: $deviceId, deviceParams: $deviceParams) {\n    _id\n    __typename\n  }\n}\n";
            Map<String, Object> setDeviceVariables = new HashMap<>();
            setDeviceVariables.put("deviceId", restClientProperties.getDeviceId());
            Map<String, Object> deviceParams = new HashMap<>();
            deviceParams.put("os", "android");
            deviceParams.put("appVersion", "5.4.9");
            deviceParams.put("model", "Nokia 7.2");
            deviceParams.put("Nokia", "Nokia");
            deviceParams.put("osVersion", "10");
            deviceParams.put("hasNotch", false);
            deviceParams.put("deviceType", "Handset");
            setDeviceVariables.put("deviceParams", deviceParams);

            GraphqlRequestBody setDeviceGraphqlRequestBody = new GraphqlRequestBody("setDevice", setDeviceVariables, setDeviceQuery);
            ResponseEntity<String> setDeviceResponse = restClient.graphql("Bearer " + token, setDeviceGraphqlRequestBody);
            log.debug("Set device response: {}", setDeviceResponse);

            // setBoxSignature
            String setBoxSignatureQuery = "mutation setBoxSignature($boxId: String, $signature: String) {\n  setBoxSignature(boxId: $boxId, signature: $signature)\n}\n";
            Map<String, Object> setBoxSignatureVariables = new HashMap<>();
            setBoxSignatureVariables.put("boxId", restClientProperties.getBoxId());
            setBoxSignatureVariables.put("signature", restClientProperties.getSignature());
            GraphqlRequestBody setBoxSignatureGraphqlRequestBody = new GraphqlRequestBody("setBoxSignature", setBoxSignatureVariables, setBoxSignatureQuery);
            ResponseEntity<String> setBoxSignatureResponse = restClient.graphql("Bearer " + token, setBoxSignatureGraphqlRequestBody);
            log.debug("Set box signature response: {}", setBoxSignatureResponse);
        }
    }

    public String loginAndResolveToken() {
        // Login with password and get token
        if (restClientProperties.isEnabled()) {
            ResponseEntity<LoginWithPasswordResponseBody> loginWithPasswordResponse = restClient.loginWithPassword(
                    new LoginWithPasswordlRequestBody(restClientProperties.getEmail(), restClientProperties.getPassword()));
            log.debug("Login with password response: {}", loginWithPasswordResponse);
            return loginWithPasswordResponse.getBody().token();
        }
        return null;
    }

    @SneakyThrows
    public Tripple<String, LocalDateTime, String> sendImageMessage(String imageAsBase64) {
        if (restClientProperties.isEnabled()) {
            String token = loginAndResolveToken();

            String sendPixNoteQuery = "mutation sendPixNote($channel: ChannelsTypes, $appVersion: String, $postcardStripePaymentId: String, $postcardAddress: JSON, $postcardSettings: JSON, $postcardScheduledDate: Date, $postcardText: String, $recipientRelationId: String, $base64: String, $recipient: String, $date: Date, $options: JSON, $contentType: [String], $timezone: Int, $promotionCode: String) {\n  sendPixNote(channel: $channel, appVersion: $appVersion, postcardStripePaymentId: $postcardStripePaymentId, postcardAddress: $postcardAddress, postcardSettings: $postcardSettings, postcardScheduledDate: $postcardScheduledDate, postcardText: $postcardText, recipientRelationId: $recipientRelationId, base64: $base64, recipient: $recipient, date: $date, contentType: $contentType, timezone: $timezone, options: $options, promotionCode: $promotionCode) {\n    _id\n    channel\n    type\n    recipient\n    postcardStripePayment\n    postcardAddress {\n      firstname\n      lastname\n      country\n      state\n      streetAddress\n      city\n      zipCode\n      __typename\n    }\n    postcardSettings {\n      color\n      fontFamily\n      fontSize\n      __typename\n    }\n    recipientRelation\n    postcardText\n    url\n    date\n    status {\n      label\n      __typename\n    }\n    statusList {\n      label\n      date\n      __typename\n    }\n    senderUser {\n      _id\n      firstName\n      email\n      __typename\n    }\n    privacyPolicy\n    addedLoveCoins\n    __typename\n  }\n}\n";
            Map<String, Object> sendPixNoteVariables = new HashMap<>();
            sendPixNoteVariables.put("channel", "LOVEBOX");
            sendPixNoteVariables.put("base64", imageAsBase64);
            sendPixNoteVariables.put("recipient", restClientProperties.getBoxId());
            sendPixNoteVariables.put("recipientRelationId", restClientProperties.getRelationId());
            sendPixNoteVariables.put("contentType", new Object[]{});
            Map<String, Object> options = new HashMap<>();
            options.put("framesBase64", null);
            options.put("deviceId", restClientProperties.getDeviceId());
            options.put("privacyPolicy", "ADMIN_AND_ME");
            options.put("templateId", null);
            sendPixNoteVariables.put("options", options);
            sendPixNoteVariables.put("timezone", 60);
            sendPixNoteVariables.put("appVersion", "5.4.9");
            GraphqlRequestBody sendPixNoteGraphqlRequestBody = new GraphqlRequestBody("sendPixNote", sendPixNoteVariables, sendPixNoteQuery);
            ResponseEntity<String> sendPixNoteResponse = restClient.graphql("Bearer " + token, sendPixNoteGraphqlRequestBody);
            log.debug("Send pix note response: {}", sendPixNoteResponse);

            JsonElement jsonRoot = JsonParser.parseString(sendPixNoteResponse.getBody());
            JsonObject sendPixNote = jsonRoot.getAsJsonObject()
                    .get("data").getAsJsonObject()
                    .get("sendPixNote").getAsJsonObject();
            JsonObject stati = sendPixNote
                    .get("statusList").getAsJsonArray().get(0).getAsJsonObject();

            String id = sendPixNote.get("_id").getAsString();
            String status = stati.get("label").getAsString();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX");
            LocalDateTime sentTime = LocalDateTime.parse(stati.get("date").getAsString(), formatter);

            return new Tripple<>(id, sentTime, status);
        }
        // When not calling the Lovebox API, we need to fake the message.
        return new Tripple<>(UUID.randomUUID().toString(), LocalDateTime.now(), "sending disabled");
    }

    @SneakyThrows
    public String receiveWaterfallOfHearts() {
        if (restClientProperties.isEnabled()) {
            String token = loginAndResolveToken();
            // Get hearts rain
            String getHeartsRainQuery = "query getHeartsRain {\n  getHeartsRain {\n  _id\n  sender\n  __typename  \n}\n}\n";
            Map<String, Object> getHeartsRainQueryVariables = new HashMap<>();

            GraphqlRequestBody getHeartsRainGraphqlRequestBody = new GraphqlRequestBody("getHeartsRain", getHeartsRainQueryVariables, getHeartsRainQuery);
            ResponseEntity<String> getHeartsRainResponse = restClient.graphql("Bearer " + token, getHeartsRainGraphqlRequestBody);
            log.debug("Get hearts rain by box response: {}", getHeartsRainResponse);

            JsonElement jsonRoot = JsonParser.parseString(getHeartsRainResponse.getBody());
            JsonObject data = jsonRoot.getAsJsonObject().get("data").getAsJsonObject();
            JsonElement getHeartsRainString = data.get("getHeartsRain");

            if (!getHeartsRainString.isJsonNull()) {
                String getHeartsRainId = getHeartsRainString.getAsJsonObject().get("_id").getAsString();

                // (Re)Set hearts rain to false
                String setHeartsRainQuery = "mutation setHeartsRain($heartId: String!) {\n  setHeartsRain(heartId:  $heartId)\n}\n";
                Map<String, Object> setHeartsRainQueryVariables = new HashMap<>();
                setHeartsRainQueryVariables.put("heartId", getHeartsRainId);

                GraphqlRequestBody setHeartsRainGraphqlRequestBody = new GraphqlRequestBody("setHeartsRain", setHeartsRainQueryVariables, setHeartsRainQuery);
                ResponseEntity<String> setHeartsRainResponse = restClient.graphql("Bearer " + token, setHeartsRainGraphqlRequestBody);
                log.debug("Set hearts rain response: {}", setHeartsRainResponse);

                return getHeartsRainId;
            }
        }
        return null;
    }

    @SneakyThrows
    public List<Pair<String, String>> getMessagesByBox() {
        List<Pair<String, String>> messageStatus = new ArrayList<>();

        if (restClientProperties.isEnabled()) {
            String token = loginAndResolveToken();

            // Get hearts rain
            String getMessagesByBoxQuery = "query getMessagesByBox($boxId: String, $relationId: String, $messagesShown: Int!) {\n  getMessagesByBox(boxId: $boxId, relationId: $relationId, messagesShown: $messagesShown) {\n    _id\n    channel\n    content\n    type\n    recipient\n    date\n    status {\n      label\n      __typename\n    }\n    statusList {\n      label\n      date\n      __typename\n    }\n    drawing {\n      base64\n      rotate\n      __typename\n    }\n    base64\n    bytes\n    premium\n    textOnly\n    textCentered\n    gifId\n    url\n    urlId\n    frames\n    senderUser {\n      _id\n      firstName\n      email\n      __typename\n    }\n    privacyPolicy\n    postcardAddress {\n      firstname\n      lastname\n      streetAddress\n      zipCode\n      city\n      country\n      state\n      __typename\n    }\n    postcardSettings {\n      color\n      fontFamily\n      fontSize\n      __typename\n    }\n    postcardScheduledDate\n    estimatedArrivalDate\n    __typename\n  }\n}\n";
            Map<String, Object> getMessagesByBoxQueryVariables = new HashMap<>();
            getMessagesByBoxQueryVariables.put("relationId", restClientProperties.getRelationId());
            getMessagesByBoxQueryVariables.put("messagesShown", 0);

            GraphqlRequestBody getMessagesByBoxGraphqlRequestBody = new GraphqlRequestBody("getMessagesByBox", getMessagesByBoxQueryVariables,
                    getMessagesByBoxQuery);
            ResponseEntity<String> getMessagesByBoxResponse = restClient.graphql("Bearer " + token, getMessagesByBoxGraphqlRequestBody);
            log.debug("Get messages by box response: {}", getMessagesByBoxResponse);

            JsonElement jsonRoot = JsonParser.parseString(getMessagesByBoxResponse.getBody());
            JsonElement getMessagesByBox = jsonRoot.getAsJsonObject()
                    .get("data").getAsJsonObject().get("getMessagesByBox");

            if (!getMessagesByBox.isJsonNull()) {
                JsonArray messages = getMessagesByBox
                        .getAsJsonArray();

                messages.forEach(m -> {
                    JsonObject message = m.getAsJsonObject();
                    String id = message.get("_id").getAsString();
                    String status = message.get("status").getAsJsonObject().get("label").getAsString();
                    messageStatus.add(new Pair(id, status));
                });
            }
        }
        return messageStatus;
    }
}
