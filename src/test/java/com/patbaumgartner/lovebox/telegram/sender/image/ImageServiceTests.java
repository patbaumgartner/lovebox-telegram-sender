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
import java.util.HashSet;
import java.util.Set;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class ImageServiceTests {

	private final ImageService imageService = new ImageService();

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
	void renderTextAcceptsBlankMessage() throws IOException {
		assertThat(decode(this.imageService.renderText("   \n  "))).isNotNull();
	}

	@Test
	void renderTextEncodesPngBytesInDataUri() {
		LoveboxImage image = this.imageService.renderText("consistency");

		String base64 = image.dataUri().substring("data:image/png;base64,".length());
		assertThat(Base64.getDecoder().decode(base64)).isEqualTo(image.png());
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, 40, 200, 300 })
	void longMessagesStayVisibleInsteadOfShrinkingToNothing(int words) throws IOException {
		BufferedImage rendered = decode(this.imageService.renderText("word ".repeat(words).strip()));

		assertThat(distinctColours(rendered)).as("a legible message uses more than the background colour")
			.isGreaterThan(1);
	}

	@Test
	void wrapsLongMessagesOntoSeveralLinesRatherThanOneUnreadableLine() throws IOException {
		BufferedImage rendered = decode(this.imageService.renderText("lovebox ".repeat(60).strip()));

		assertThat(paintedRows(rendered)).as("wrapped text covers many rows of the display").isGreaterThan(5);
	}

	@Test
	void rejectsMessagesThatCannotBeShownLegibly() {
		String tooLong = "a very long love letter ".repeat(400);

		assertThatExceptionOfType(UnsupportedMessageException.class)
			.isThrownBy(() -> this.imageService.renderText(tooLong))
			.withMessageContaining("too long");
	}

	@Test
	void rendersUnbrokenWordsThatAreWiderThanTheDisplay() throws IOException {
		assertThat(decode(this.imageService.renderText("x".repeat(300)))).isNotNull();
	}

	@Test
	void neverSplitsAnEmojiInTheMiddleOfASurrogatePair() throws IOException {
		assertThat(decode(this.imageService.renderText("🚀".repeat(200)))).isNotNull();
	}

	@Test
	void picksBlackTextOnLightBackgrounds() {
		assertThat(ImageService.contrastingTextColor(Color.white)).isEqualTo(Color.black);
		assertThat(ImageService.contrastingTextColor(new Color(250, 250, 250))).isEqualTo(Color.black);
		assertThat(ImageService.contrastingTextColor(Color.yellow)).isEqualTo(Color.black);
	}

	@Test
	void picksWhiteTextOnDarkBackgrounds() {
		assertThat(ImageService.contrastingTextColor(Color.black)).isEqualTo(Color.white);
		assertThat(ImageService.contrastingTextColor(new Color(20, 20, 20))).isEqualTo(Color.white);
		assertThat(ImageService.contrastingTextColor(new Color(0, 0, 139))).isEqualTo(Color.white);
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

		assertThat(decode(this.imageService.renderPhoto(photo, null))).isNotNull();
	}

	@Test
	void renderPhotoRejectsUnsupportedFileContent(@TempDir Path tempDir) throws IOException {
		Path notAnImage = tempDir.resolve("not-an-image.png");
		Files.writeString(notAnImage, "definitely not a png");

		assertThatExceptionOfType(UnsupportedMessageException.class)
			.isThrownBy(() -> this.imageService.renderPhoto(notAnImage.toFile(), null))
			.withMessageContaining("Unsupported image format");
	}

	@Test
	void renderPhotoRejectsOversizedFilesBeforeDecodingThem(@TempDir Path tempDir) throws IOException {
		Path bomb = tempDir.resolve("huge.png");
		Files.write(bomb, new byte[(int) ImageService.MAX_PHOTO_BYTES + 1]);

		assertThatExceptionOfType(UnsupportedMessageException.class)
			.isThrownBy(() -> this.imageService.renderPhoto(bomb.toFile(), null))
			.withMessageContaining("too large");
	}

	@Test
	void renderPhotoRejectsImagesWithTooManyPixels(@TempDir Path tempDir) throws IOException {
		// A single-colour 4000x4000 PNG is a few kB on disk but expands to 16
		// megapixels, i.e. 64 MB of raster inside a 256 MB container.
		File bomb = createSamplePhoto(tempDir, 4000, 4000);

		assertThatExceptionOfType(UnsupportedMessageException.class)
			.isThrownBy(() -> this.imageService.renderPhoto(bomb, null))
			.withMessageContaining("megapixels");
	}

	private static int distinctColours(BufferedImage image) {
		Set<Integer> colours = new HashSet<>();
		for (int x = 0; x < image.getWidth(); x += 2) {
			for (int y = 0; y < image.getHeight(); y += 2) {
				colours.add(image.getRGB(x, y));
			}
		}
		return colours.size();
	}

	private static int paintedRows(BufferedImage image) {
		int background = image.getRGB(0, 0);
		int rows = 0;
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				if (image.getRGB(x, y) != background) {
					rows++;
					break;
				}
			}
		}
		return rows;
	}

	private static BufferedImage decode(LoveboxImage image) throws IOException {
		return ImageIO.read(new ByteArrayInputStream(image.png()));
	}

	private static File createSamplePhoto(Path tempDir, int width, int height) throws IOException {
		BufferedImage source = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = source.createGraphics();
		try {
			graphics.setColor(Color.pink);
			graphics.fillRect(0, 0, width, height);
		}
		finally {
			graphics.dispose();
		}
		File file = tempDir.resolve("photo-%dx%d.png".formatted(width, height)).toFile();
		ImageIO.write(source, "png", file);
		return file;
	}

}
