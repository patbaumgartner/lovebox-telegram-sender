package com.patbaumgartner.lovebox.telegram.sender.lovebox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoveboxStartupRunnerTests {

	@Mock
	private LoveboxService loveboxService;

	private static LoveboxRestClientProperties properties(boolean enabled) {
		return new LoveboxRestClientProperties(enabled, "me@example.com", "secret", "device-1", "box-1", "Signature",
				"https://api.example.invalid");
	}

	@Test
	void initializesTheAccountWhenEnabled() {
		when(this.loveboxService.initializeIfNeeded()).thenReturn(true);
		LoveboxStartupRunner runner = new LoveboxStartupRunner(properties(true), this.loveboxService);

		runner.run(null);

		verify(this.loveboxService).initializeIfNeeded();
	}

	@Test
	void survivesAnUnknownAccount() {
		when(this.loveboxService.initializeIfNeeded()).thenReturn(false);
		LoveboxStartupRunner runner = new LoveboxStartupRunner(properties(true), this.loveboxService);

		assertThatNoException().isThrownBy(() -> runner.run(null));
	}

	@Test
	void doesNothingWhenDisabled() {
		LoveboxStartupRunner runner = new LoveboxStartupRunner(properties(false), this.loveboxService);

		runner.run(null);

		verifyNoInteractions(this.loveboxService);
	}

	@Test
	void neverPropagatesApiFailures() {
		when(this.loveboxService.initializeIfNeeded()).thenThrow(new IllegalStateException("API down"));
		LoveboxStartupRunner runner = new LoveboxStartupRunner(properties(true), this.loveboxService);

		assertThatNoException().isThrownBy(() -> runner.run(null));
	}

}
