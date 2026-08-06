package com.patbaumgartner.lovebox.telegram.sender.config;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * Native-image hints for application resources: the bundled fallback image is loaded via
 * {@code ResourceLoader} at runtime and must be explicitly included, otherwise
 * {@code renderFallback()} fails with a {@code FileNotFoundException} in the native image
 * only.
 */
public class ApplicationRuntimeHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		hints.resources().registerPattern("lovebox.jpeg");
	}

}
