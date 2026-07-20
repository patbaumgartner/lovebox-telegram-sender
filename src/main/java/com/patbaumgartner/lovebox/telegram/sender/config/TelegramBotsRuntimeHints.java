package com.patbaumgartner.lovebox.telegram.sender.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.util.ClassUtils;

/**
 * Registers GraalVM native-image reflection hints for the Telegram Bot API
 * types.
 * <p>
 * {@code org.telegram:telegrambots-*:10.0.0} does not ship GraalVM reachability
 * metadata.
 * The Bot API request and response objects under
 * {@code org.telegram.telegrambots.meta.api} are (de)serialised with Jackson,
 * which needs
 * reflective access to their constructors, fields and accessors at runtime.
 * Without these
 * hints the native image starts but cannot send or receive messages.
 */
public class TelegramBotsRuntimeHints implements RuntimeHintsRegistrar {

	private static final Logger log = LoggerFactory.getLogger(TelegramBotsRuntimeHints.class);

	private static final String TELEGRAM_API_BASE_PACKAGE = "org.telegram.telegrambots.meta.api";

	private static final String JPEG_IMAGE_READER = "com.sun.imageio.plugins.jpeg.JPEGImageReader";

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		var scanner = new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter((TypeFilter) (metadataReader, metadataReaderFactory) -> true);

		int count = 0;
		for (var candidate : scanner.findCandidateComponents(TELEGRAM_API_BASE_PACKAGE)) {
			String className = candidate.getBeanClassName();
			if (className == null) {
				continue;
			}
			try {
				Class<?> type = ClassUtils.forName(className, classLoader);
				hints.reflection().registerType(type, MemberCategory.values());
				count++;
			} catch (Throwable ex) {
				// Skip classes that cannot be loaded; they are not needed at runtime.
				log.trace("Skipping Telegram Bot API type for native hints: {} ({})", className, ex.getMessage());
			}
		}
		hints.jni().registerType(TypeReference.of(JPEG_IMAGE_READER), MemberCategory.INVOKE_DECLARED_METHODS);
		log.debug("Registered native reflection hints for {} Telegram Bot API types", count);
	}

}
