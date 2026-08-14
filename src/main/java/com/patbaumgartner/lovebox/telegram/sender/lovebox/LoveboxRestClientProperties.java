package com.patbaumgartner.lovebox.telegram.sender.lovebox;

import java.net.URI;
import java.net.URISyntaxException;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration of the Lovebox account and target device.
 * <p>
 * Everything required to talk to the API is validated while binding, so a missing or
 * malformed setting fails the application at startup instead of surfacing as a confusing
 * API error on the first scheduled poll. Whether the API is actually reachable is
 * deliberately not checked: an unreachable Lovebox must not crash-loop the container.
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

		@DefaultValue("true") boolean enabled,

		String email,

		String password,

		String deviceId,

		String boxId,

		String signature,

		@DefaultValue("https://app-api.loveboxlove.com") String apiUrl

) {

	public LoveboxRestClientProperties {
		requireHttpUrl(apiUrl);
		if (enabled) {
			requireText(email, "lovebox.email");
			requireText(password, "lovebox.password");
			requireText(deviceId, "lovebox.device-id");
			requireText(boxId, "lovebox.box-id");
		}
	}

	private static void requireText(String value, String property) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(
					"%s must be set when lovebox.enabled is true; set lovebox.enabled=false for a dry run"
						.formatted(property));
		}
	}

	private static void requireHttpUrl(String value) {
		try {
			URI uri = new URI(value);
			if (!uri.isAbsolute() || (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme()))) {
				throw new URISyntaxException(value, "not an absolute http(s) URL");
			}
		}
		catch (URISyntaxException ex) {
			throw new IllegalArgumentException("lovebox.api-url must be an absolute http(s) URL, but was: " + value);
		}
	}

}
