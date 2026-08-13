package com.patbaumgartner.lovebox.telegram.sender.image;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Smoke test for the full render pipeline, enabled with {@code render.smoke.enabled=true}
 * (env {@code RENDER_SMOKE_ENABLED=true}).
 * <p>
 * Exists to validate GraalVM native images before deployment: JPEG coding (JNI) and emoji
 * glyph shaping (HarfBuzz FFM downcalls) only fail at runtime on first use, so unit tests
 * and a successful build prove nothing about the native binary. Renders emoji text, then
 * encodes a JPEG, decodes it again, scales it and draws a caption over it; logs
 * {@code Render smoke test passed} and exits with code 0 on success, and fails startup
 * (non-zero exit) on error. This makes
 * {@code docker run --rm -e RENDER_SMOKE_ENABLED=true <image>} usable as a CI gate.
 * <p>
 * Deliberately gated by a runtime property instead of {@code @Profile}: profile
 * conditions on beans are evaluated at build time under AOT, so a profile-gated bean
 * would not exist in the native image. Ordered first so that shutting the context down
 * cannot race the Lovebox and Telegram startup runners.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RenderSmokeRunner implements ApplicationRunner {

	static final String ENABLED_PROPERTY = "render.smoke.enabled";

	private static final Logger log = LoggerFactory.getLogger(RenderSmokeRunner.class);

	private static final int SAMPLE_WIDTH = 640;

	private static final int SAMPLE_HEIGHT = 480;

	private final Environment environment;

	private final ImageService imageService;

	private final ConfigurableApplicationContext applicationContext;

	public RenderSmokeRunner(Environment environment, ImageService imageService,
			ConfigurableApplicationContext applicationContext) {
		this.environment = environment;
		this.imageService = imageService;
		this.applicationContext = applicationContext;
	}

	@Override
	public void run(ApplicationArguments args) throws IOException {
		if (!this.environment.getProperty(ENABLED_PROPERTY, Boolean.class, false)) {
			return;
		}
		log.info("Render smoke test: text with emoji (exercises HarfBuzz FFM glyph shaping)");
		LoveboxImage emojiText = this.imageService.renderText("I love you 🚀❤️\nto the moon 🌙\nand back");

		log.info("Render smoke test: photo with caption (exercises the ImageIO JPEG codec and Scalr)");
		Path photo = Files.createTempFile("render-smoke", ".jpeg");
		try {
			writeSampleJpeg(photo);
			LoveboxImage captionedPhoto = this.imageService.renderPhoto(photo.toFile(), "Smoke test 📷✨");
			if (emojiText.png().length == 0 || captionedPhoto.png().length == 0) {
				throw new IllegalStateException("Render smoke test produced an empty image");
			}
		}
		finally {
			Files.deleteIfExists(photo);
		}
		log.info("Render smoke test passed");
		shutdown();
	}

	private static void writeSampleJpeg(Path target) throws IOException {
		BufferedImage sample = new BufferedImage(SAMPLE_WIDTH, SAMPLE_HEIGHT, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = sample.createGraphics();
		try {
			graphics.setPaint(new GradientPaint(0, 0, Color.pink, SAMPLE_WIDTH, SAMPLE_HEIGHT, Color.blue));
			graphics.fillRect(0, 0, SAMPLE_WIDTH, SAMPLE_HEIGHT);
		}
		finally {
			graphics.dispose();
		}
		try (OutputStream output = Files.newOutputStream(target)) {
			if (!ImageIO.write(sample, "jpeg", output)) {
				throw new IllegalStateException("No JPEG writer is available in this runtime");
			}
		}
	}

	/** Overridable for tests; terminating the JVM is the desired production behavior. */
	protected void shutdown() {
		System.exit(SpringApplication.exit(this.applicationContext, () -> 0));
	}

}
