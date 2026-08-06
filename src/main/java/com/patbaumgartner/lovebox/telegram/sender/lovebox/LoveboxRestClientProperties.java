package com.patbaumgartner.lovebox.telegram.sender.lovebox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration of the Lovebox account and target device.
 *
 * @param enabled whether the Lovebox API integration is active; when {@code false} all
 * API calls are skipped and message sending is simulated
 * @param email the e-mail address of the Lovebox account
 * @param password the password of the Lovebox account
 * @param deviceId the identifier of the (simulated) mobile device, see the README on how
 * to obtain it
 * @param boxId the identifier of the Lovebox to send messages to
 * @param signature the sender signature shown on the Lovebox display
 * @param apiUrl the base URL of the Lovebox API
 */
@ConfigurationProperties(prefix = "lovebox")
public record LoveboxRestClientProperties(

		boolean enabled,

		String email,

		String password,

		String deviceId,

		String boxId,

		String signature,

		@DefaultValue("https://app-api.loveboxlove.com") String apiUrl

) {

}
