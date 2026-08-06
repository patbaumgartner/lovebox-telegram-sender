package com.patbaumgartner.lovebox.telegram.sender.lovebox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Verifies the configured Lovebox account on startup and applies the device registration
 * and box signature, mirroring what the mobile app does after login.
 * <p>
 * Failures are only logged: a temporarily unreachable Lovebox API must not crash-loop the
 * container on the NAS.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoveboxStartupRunner implements ApplicationRunner {

	private final LoveboxRestClientProperties properties;

	private final LoveboxService loveboxService;

	@Override
	public void run(ApplicationArguments args) {
		if (!this.properties.enabled()) {
			log.info("Lovebox integration is disabled (lovebox.enabled=false); skipping account verification");
			return;
		}
		try {
			if (!this.loveboxService.accountExists()) {
				log.error("Lovebox account {} does not exist - check lovebox.email and lovebox.password",
						this.properties.email());
				return;
			}
			this.loveboxService.registerDeviceAndSignature();
			log.info("Lovebox account {} verified and device registered", this.properties.email());
		}
		catch (RuntimeException ex) {
			log.warn("Could not verify Lovebox account (API unreachable?): {}", ex.getMessage());
		}
	}

}
