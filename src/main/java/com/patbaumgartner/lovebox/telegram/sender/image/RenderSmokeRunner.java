package com.patbaumgartner.lovebox.telegram.sender.image;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Smoke test for the full render pipeline, enabled with {@code render.smoke.enabled=true}
 * (env {@code RENDER_SMOKE_ENABLED=true}).
 * <p>
 * Exists to validate GraalVM native images before deployment: JPEG decoding (JNI) and
 * emoji glyph shaping (HarfBuzz FFM downcalls) only fail at runtime on first use, so unit
 * tests and a successful build prove nothing about the native binary. Runs emoji text
 * rendering, JPEG decode, photo scaling with caption, and the fallback image; logs
 * {@code Render smoke test passed} and exits with code 0 on success, fails startup
 * (non-zero exit) on error. This makes
 * {@code docker run --rm -e RENDER_SMOKE_ENABLED=true <image>} usable as a CI gate.
 * <p>
 * Deliberately gated by a runtime property instead of {@code @Profile}: profile
 * conditions on beans are evaluated at build time under AOT, so a profile-gated bean
 * would not exist in the native image.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RenderSmokeRunner implements ApplicationRunner {

	static final String ENABLED_PROPERTY = "render.smoke.enabled";

	private final Environment environment;

	private final ImageService imageService;

	private final ResourceLoader resourceLoader;

	private final ConfigurableApplicationContext applicationContext;

	@Override
	public void run(ApplicationArguments args) throws Exception {
		if (!this.environment.getProperty(ENABLED_PROPERTY, Boolean.class, false)) {
			return;
		}
		log.info("Render smoke test: text with emoji (exercises HarfBuzz FFM glyph shaping)");
		LoveboxImage emojiText = this.imageService.renderText("I love you 🚀❤️\nto the moon 🌙\nand back");

		log.info("Render smoke test: bundled JPEG fallback (exercises ImageIO JNI decode)");
		LoveboxImage fallback = this.imageService.renderFallback();

		log.info("Render smoke test: photo scaling with emoji caption (exercises Scalr + drawString)");
		Path photo = Files.createTempFile("render-smoke", ".jpeg");
		try (InputStream jpeg = this.resourceLoader.getResource(ImageService.FALLBACK_IMAGE).getInputStream()) {
			Files.copy(jpeg, photo, StandardCopyOption.REPLACE_EXISTING);
			LoveboxImage captionedPhoto = this.imageService.renderPhoto(photo.toFile(), "Smoke test 📷✨");
			if (emojiText.png().length == 0 || fallback.png().length == 0 || captionedPhoto.png().length == 0) {
				throw new IllegalStateException("Render smoke test produced an empty image");
			}
		}
		finally {
			Files.deleteIfExists(photo);
		}
		log.info("Render smoke test passed");
		shutdown();
	}

	/** Overridable for tests; terminating the JVM is the desired production behavior. */
	protected void shutdown() {
		System.exit(SpringApplication.exit(this.applicationContext, () -> 0));
	}

}
