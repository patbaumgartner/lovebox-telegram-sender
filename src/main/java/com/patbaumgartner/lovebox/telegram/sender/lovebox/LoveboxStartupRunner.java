package com.patbaumgartner.lovebox.telegram.sender.lovebox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Verifies the configured Lovebox account on startup and applies the device registration
 * and box signature, mirroring what the mobile app does after login.
 * <p>
 * Failures are only logged: a temporarily unreachable Lovebox API must not crash-loop the
 * container on the NAS. Registration is retried by the scheduled poll, so a Lovebox API
 * that only becomes reachable after startup still ends up registered.
 */
@Component
@Order(LoveboxStartupRunner.ORDER)
public class LoveboxStartupRunner implements ApplicationRunner {

	static final int ORDER = Ordered.LOWEST_PRECEDENCE - 100;

	private static final Logger log = LoggerFactory.getLogger(LoveboxStartupRunner.class);

	private final LoveboxRestClientProperties properties;

	private final LoveboxService loveboxService;

	public LoveboxStartupRunner(LoveboxRestClientProperties properties, LoveboxService loveboxService) {
		this.properties = properties;
		this.loveboxService = loveboxService;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!this.properties.enabled()) {
			log.info("Lovebox integration is disabled (lovebox.enabled=false); skipping account verification");
			return;
		}
		try {
			if (this.loveboxService.initializeIfNeeded()) {
				log.info("Lovebox account {} verified and device registered", this.properties.email());
			}
			else {
				log.error("Lovebox account {} does not exist - check lovebox.email and lovebox.password",
						this.properties.email());
			}
		}
		catch (RuntimeException ex) {
			log.warn("Could not verify the Lovebox account, retrying on the next poll: {}", ex.getMessage());
		}
	}

}
