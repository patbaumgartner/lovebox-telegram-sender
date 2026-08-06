package com.patbaumgartner.lovebox.telegram.sender.image;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class ImageServiceTests {

	private final ImageService imageService = new ImageService(new DefaultResourceLoader());

	@Test
	void renderTextProducesDisplaySizedPng() throws IOException {
		LoveboxImage image = this.imageService.renderText("Hello Lovebox");

		assertThat(image.dataUri()).startsWith("data:image/png;base64,");
		BufferedImage decoded = decode(image);
		assertThat(decoded.getWidth()).isEqualTo(ImageService.DISPLAY_WIDTH);
		assertThat(decoded.getHeight()).isEqualTo(ImageService.DISPLAY_HEIGHT);
	}

	@Test
	void renderTextSupportsMultilineAndEmojiMessages() throws IOException {
		LoveboxImage image = this.imageService.renderText("I love you 🚀\nto the moon\nand back");

		assertThat(decode(image)).isNotNull();
	}

	@Test
	void renderTextAcceptsNullMessage() throws IOException {
		LoveboxImage image = this.imageService.renderText(null);

		assertThat(decode(image)).isNotNull();
	}

	@Test
	void renderTextEncodesPngBytesInDataUri() {
		LoveboxImage image = this.imageService.renderText("consistency");

		String base64 = image.dataUri().substring("data:image/png;base64,".length());
		assertThat(Base64.getDecoder().decode(base64)).isEqualTo(image.png());
	}

	@Test
	void renderPhotoScalesPhotoToDisplaySize(@TempDir Path tempDir) throws IOException {
		File photo = createSamplePhoto(tempDir, 400, 300);

		LoveboxImage image = this.imageService.renderPhoto(photo, "A caption 📷");

		BufferedImage decoded = decode(image);
		assertThat(decoded.getWidth()).isEqualTo(ImageService.DISPLAY_WIDTH);
		assertThat(decoded.getHeight()).isEqualTo(ImageService.DISPLAY_HEIGHT);
	}

	@Test
	void renderPhotoAcceptsMissingCaption(@TempDir Path tempDir) throws IOException {
		File photo = createSamplePhoto(tempDir, 100, 100);

		LoveboxImage image = this.imageService.renderPhoto(photo, null);

		assertThat(decode(image)).isNotNull();
	}

	@Test
	void renderPhotoRejectsUnsupportedFileContent(@TempDir Path tempDir) throws IOException {
		Path notAnImage = tempDir.resolve("not-an-image.png");
		Files.writeString(notAnImage, "definitely not a png");

		assertThatIllegalStateException().isThrownBy(() -> this.imageService.renderPhoto(notAnImage.toFile(), null))
			.withMessageContaining("Unsupported image format");
	}

	@Test
	void renderFallbackLoadsBundledImage() throws IOException {
		LoveboxImage image = this.imageService.renderFallback();

		BufferedImage decoded = decode(image);
		assertThat(decoded.getWidth()).isEqualTo(ImageService.DISPLAY_WIDTH);
		assertThat(decoded.getHeight()).isEqualTo(ImageService.DISPLAY_HEIGHT);
	}

	private static BufferedImage decode(LoveboxImage image) throws IOException {
		return ImageIO.read(new ByteArrayInputStream(image.png()));
	}

	private static File createSamplePhoto(Path tempDir, int width, int height) throws IOException {
		BufferedImage source = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = source.createGraphics();
		graphics.setColor(Color.pink);
		graphics.fillRect(0, 0, width, height);
		graphics.dispose();
		File file = tempDir.resolve("photo.png").toFile();
		ImageIO.write(source, "png", file);
		return file;
	}

}
