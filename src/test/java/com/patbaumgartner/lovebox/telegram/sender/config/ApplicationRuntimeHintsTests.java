package com.patbaumgartner.lovebox.telegram.sender.config;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationRuntimeHintsTests {

	@Test
	void registersFallbackImageResource() {
		RuntimeHints hints = new RuntimeHints();
		new ApplicationRuntimeHints().registerHints(hints, getClass().getClassLoader());

		assertThat(RuntimeHintsPredicates.resource().forResource("lovebox.jpeg")).accepts(hints);
	}

}
