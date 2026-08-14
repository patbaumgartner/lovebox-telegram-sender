package com.patbaumgartner.lovebox.telegram.sender.lovebox;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

class LoveboxRestClientPropertiesTests {

	private static final String API_URL = "https://api.example.invalid";

	private static LoveboxRestClientProperties enabled(String email, String password, String deviceId, String boxId) {
		return new LoveboxRestClientProperties(true, email, password, deviceId, boxId, "Signature", API_URL);
	}

	@Test
	void acceptsAFullyConfiguredAccount() {
		assertThatNoException().isThrownBy(() -> enabled("me@example.com", "secret", "device-1", "box-1"));
	}

	@Test
	void rejectsAMissingEmail() {
		assertThatIllegalArgumentException().isThrownBy(() -> enabled(null, "secret", "device-1", "box-1"))
			.withMessageContaining("lovebox.email");
	}

	@Test
	void rejectsABlankPassword() {
		assertThatIllegalArgumentException().isThrownBy(() -> enabled("me@example.com", "  ", "device-1", "box-1"))
			.withMessageContaining("lovebox.password");
	}

	@Test
	void rejectsAMissingDeviceId() {
		assertThatIllegalArgumentException().isThrownBy(() -> enabled("me@example.com", "secret", null, "box-1"))
			.withMessageContaining("lovebox.device-id");
	}

	@Test
	void rejectsAMissingBoxId() {
		assertThatIllegalArgumentException().isThrownBy(() -> enabled("me@example.com", "secret", "device-1", null))
			.withMessageContaining("lovebox.box-id");
	}

	@Test
	void allowsAnUnconfiguredAccountWhenDisabled() {
		assertThatNoException()
			.isThrownBy(() -> new LoveboxRestClientProperties(false, null, null, null, null, null, API_URL));
	}

	@Test
	void rejectsARelativeApiUrl() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new LoveboxRestClientProperties(false, null, null, null, null, null, "/v1/graphql"))
			.withMessageContaining("lovebox.api-url");
	}

	@Test
	void rejectsANonHttpApiUrl() {
		assertThatIllegalArgumentException()
			.isThrownBy(
					() -> new LoveboxRestClientProperties(false, null, null, null, null, null, "ftp://example.invalid"))
			.withMessageContaining("lovebox.api-url");
	}

	@Test
	void keepsTheConfiguredApiUrl() {
		LoveboxRestClientProperties properties = new LoveboxRestClientProperties(false, null, null, null, null, null,
				"http://localhost:8080");

		assertThat(properties.apiUrl()).isEqualTo("http://localhost:8080");
	}

}
