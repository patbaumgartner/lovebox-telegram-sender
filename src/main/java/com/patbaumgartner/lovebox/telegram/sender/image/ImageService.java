package com.patbaumgartner.lovebox.telegram.sender.image;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.ThreadLocalRandom;

import javax.imageio.ImageIO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imgscalr.Scalr;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Renders 1280x960 PNG images for the Lovebox display: scaled photos with an optional
 * caption, text messages centered on a random background color, and a bundled fallback
 * image.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageService {

	public static final int DISPLAY_WIDTH = 1280;

	public static final int DISPLAY_HEIGHT = 960;

	public static final int BORDER_WIDTH = 20;

	public static final int INITIAL_FONT_SIZE = 18;

	// Emoji Font Limitations:
	// https://mail.openjdk.java.net/pipermail/2d-dev/2021-May/012975.html
	public static final int MAX_EMOJI_FONT_SIZE = 100;

	public static final String FONT_NAME = "Sans";

	static final String FALLBACK_IMAGE = "classpath:lovebox.jpeg";

	private final ResourceLoader resourceLoader;

	/**
	 * Scales a photo to the Lovebox display size, centered on a black canvas, and draws
	 * the given text on top of it.
	 * @param file the photo to render
	 * @param text optional text drawn centered over the photo, may be {@code null}
	 * @return the rendered image
	 */
	public LoveboxImage renderPhoto(File file, String text) {
		try {
			BufferedImage originalImage = ImageIO.read(file);
			if (originalImage == null) {
				throw new IllegalStateException("Unsupported image format: " + file);
			}
			BufferedImage resizedImage = Scalr.resize(originalImage, Scalr.Method.AUTOMATIC, Scalr.Mode.AUTOMATIC,
					DISPLAY_WIDTH, DISPLAY_HEIGHT, Scalr.OP_ANTIALIAS);

			BufferedImage image = new BufferedImage(DISPLAY_WIDTH, DISPLAY_HEIGHT, BufferedImage.TYPE_INT_ARGB);
			Graphics2D graphics = image.createGraphics();
			try {
				graphics.setColor(Color.black);
				graphics.fillRect(0, 0, DISPLAY_WIDTH, DISPLAY_HEIGHT);

				int x = (DISPLAY_WIDTH - resizedImage.getWidth()) / 2;
				int y = (DISPLAY_HEIGHT - resizedImage.getHeight()) / 2;
				graphics.drawImage(resizedImage, x, y, null);

				if (text != null) {
					drawCenteredMessage(graphics, text);
				}
			}
			finally {
				graphics.dispose();
			}

			return toLoveboxImage(image);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/**
	 * Renders a text message centered on a random background color.
	 * @param message the message text, may be {@code null}
	 * @return the rendered image
	 */
	public LoveboxImage renderText(String message) {
		try {
			BufferedImage image = new BufferedImage(DISPLAY_WIDTH, DISPLAY_HEIGHT, BufferedImage.TYPE_INT_ARGB);
			Graphics2D graphics = image.createGraphics();
			try {
				Color color = new Color(ThreadLocalRandom.current().nextInt(256),
						ThreadLocalRandom.current().nextInt(256), ThreadLocalRandom.current().nextInt(256));
				graphics.setColor(color);
				graphics.fillRect(0, 0, DISPLAY_WIDTH, DISPLAY_HEIGHT);

				if (message != null) {
					drawCenteredMessage(graphics, message);
				}
			}
			finally {
				graphics.dispose();
			}

			return toLoveboxImage(image);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/**
	 * Renders the bundled fallback image, used for unsupported message types.
	 * @return the rendered image
	 */
	public LoveboxImage renderFallback() {
		try {
			Resource resource = this.resourceLoader.getResource(FALLBACK_IMAGE);
			Image image = ImageIO.read(resource.getInputStream());
			image = image.getScaledInstance(DISPLAY_WIDTH, DISPLAY_HEIGHT, Image.SCALE_SMOOTH);
			BufferedImage bufferedImage = new BufferedImage(DISPLAY_WIDTH, DISPLAY_HEIGHT, BufferedImage.TYPE_INT_ARGB);

			Graphics2D graphics = bufferedImage.createGraphics();
			try {
				graphics.drawImage(image, 0, 0, null);
			}
			finally {
				graphics.dispose();
			}

			return toLoveboxImage(bufferedImage);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	protected void drawCenteredMessage(Graphics2D graphics, String text) {
		String message = text.strip();

		// Calculate the font size that fills the display width, capped for emoji glyphs
		graphics.setColor(Color.white);
		Font baseFont = new Font(FONT_NAME, Font.PLAIN, INITIAL_FONT_SIZE);
		graphics.setFont(baseFont);
		FontMetrics baseMetrics = graphics.getFontMetrics();
		String[] lines = message.split("\n");
		int stringWidth = Arrays.stream(lines)
			.mapToInt(line -> baseMetrics.stringWidth(line) + 2 * BORDER_WIDTH)
			.max()
			.orElse(2 * BORDER_WIDTH);
		double widthRatio = (double) DISPLAY_WIDTH / (double) stringWidth;

		int newFontSize = (int) (baseFont.getSize() * widthRatio);
		int fontSizeToUse = Math.min(newFontSize, MAX_EMOJI_FONT_SIZE);
		graphics.setFont(new Font(baseFont.getName(), baseFont.getStyle(), fontSizeToUse));

		// Draw centered lines
		FontMetrics fm = graphics.getFontMetrics();
		log.debug("Using font: {}", fm.getFont());

		int lineHeight = fm.getHeight();
		int yInitialOffset = (lines.length - 1) * lineHeight;
		int y = fm.getAscent() + (DISPLAY_HEIGHT - (fm.getAscent() + fm.getDescent()) - yInitialOffset) / 2;

		for (String line : lines) {
			int x = (DISPLAY_WIDTH - fm.stringWidth(line)) / 2;
			graphics.drawString(line, x, y);
			y += lineHeight;
		}
	}

	protected LoveboxImage toLoveboxImage(BufferedImage image) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(image, "png", output);
		byte[] png = output.toByteArray();
		String base64Image = Base64.getEncoder().encodeToString(png);

		return new LoveboxImage("data:image/png;base64," + base64Image, png);
	}

}
