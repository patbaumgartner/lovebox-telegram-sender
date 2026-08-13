package com.patbaumgartner.lovebox.telegram.sender.image;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.imgscalr.Scalr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Renders 1280x960 PNG images for the Lovebox display: scaled photos with an optional
 * caption, and text messages centered on a random background color.
 * <p>
 * Text is word-wrapped and sized so that it always fills the display without ever
 * becoming unreadable. Messages that cannot be shown legibly are rejected with an
 * {@link UnsupportedMessageException} rather than silently shrunk: the recipient reads
 * this on a physical device and has no way to zoom in.
 */
@Component
public class ImageService {

	private static final Logger log = LoggerFactory.getLogger(ImageService.class);

	public static final int DISPLAY_WIDTH = 1280;

	public static final int DISPLAY_HEIGHT = 960;

	public static final int BORDER_WIDTH = 20;

	/** Smallest size that is still comfortably readable on the physical display. */
	public static final int MIN_FONT_SIZE = 24;

	// Emoji Font Limitations:
	// https://mail.openjdk.java.net/pipermail/2d-dev/2021-May/012975.html
	public static final int MAX_FONT_SIZE = 100;

	/** Captions must not swallow the photo they are drawn on. */
	public static final int MAX_CAPTION_FONT_SIZE = 56;

	public static final String FONT_NAME = "Sans";

	/** Refuse photos above this many pixels before allocating a raster for them. */
	static final long MAX_PHOTO_PIXELS = 8L * 1024 * 1024;

	/** Refuse photo files above this size before reading them. */
	static final long MAX_PHOTO_BYTES = 16L * 1024 * 1024;

	private static final float SCRIM_OPACITY = 0.45f;

	static {
		// ImageIO caches stream data in temporary files by default, which would write
		// every rendered image to disk on its way into an in-memory byte array.
		ImageIO.setUseCache(false);
	}

	/**
	 * Scales a photo to the Lovebox display size, centered on a black canvas, and draws
	 * the given text on top of it.
	 * @param file the photo to render
	 * @param text optional text drawn centered over the photo, may be {@code null}
	 * @return the rendered image
	 * @throws UnsupportedMessageException if the file is not a readable image, is too
	 * large to decode safely, or the text cannot be rendered legibly
	 */
	public LoveboxImage renderPhoto(File file, String text) {
		try {
			BufferedImage photo = readPhoto(file);
			BufferedImage resized = Scalr.resize(photo, Scalr.Method.AUTOMATIC, Scalr.Mode.AUTOMATIC, DISPLAY_WIDTH,
					DISPLAY_HEIGHT, Scalr.OP_ANTIALIAS);

			return render(graphics -> {
				graphics.setColor(Color.black);
				graphics.fillRect(0, 0, DISPLAY_WIDTH, DISPLAY_HEIGHT);
				graphics.drawImage(resized, (DISPLAY_WIDTH - resized.getWidth()) / 2,
						(DISPLAY_HEIGHT - resized.getHeight()) / 2, null);

				if (text != null && !text.isBlank()) {
					drawCenteredText(graphics, text, MAX_CAPTION_FONT_SIZE, Color.white, true);
				}
			});
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/**
	 * Renders a text message centered on a random background color.
	 * @param message the message text, may be {@code null}
	 * @return the rendered image
	 * @throws UnsupportedMessageException if the text cannot be rendered legibly
	 */
	public LoveboxImage renderText(String message) {
		Color background = new Color(ThreadLocalRandom.current().nextInt(256), ThreadLocalRandom.current().nextInt(256),
				ThreadLocalRandom.current().nextInt(256));

		return render(graphics -> {
			graphics.setColor(background);
			graphics.fillRect(0, 0, DISPLAY_WIDTH, DISPLAY_HEIGHT);

			if (message != null && !message.isBlank()) {
				drawCenteredText(graphics, message, MAX_FONT_SIZE, contrastingTextColor(background), false);
			}
		});
	}

	/**
	 * Picks the text color with the better contrast against the given background, using
	 * the WCAG 2.2 relative luminance formula. The 0.179 threshold is the luminance at
	 * which black and white text reach the same contrast ratio.
	 * @param background the background color the text is drawn on
	 * @return {@link Color#BLACK} or {@link Color#WHITE}
	 */
	static Color contrastingTextColor(Color background) {
		double luminance = 0.2126 * gammaExpand(background.getRed()) + 0.7152 * gammaExpand(background.getGreen())
				+ 0.0722 * gammaExpand(background.getBlue());
		return luminance > 0.179 ? Color.black : Color.white;
	}

	private static double gammaExpand(int channel) {
		double value = channel / 255.0;
		return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
	}

	/**
	 * Decodes a photo, refusing anything oversized before a raster is allocated for it.
	 * The dimensions are taken from the image header, so a decompression bomb is rejected
	 * without ever being expanded into memory.
	 */
	private static BufferedImage readPhoto(File file) throws IOException {
		if (file.length() > MAX_PHOTO_BYTES) {
			throw new UnsupportedMessageException("That photo is too large (%d MB, limit %d MB)."
				.formatted(file.length() / (1024 * 1024), MAX_PHOTO_BYTES / (1024 * 1024)));
		}
		try (ImageInputStream stream = ImageIO.createImageInputStream(file)) {
			Iterator<ImageReader> readers = stream != null ? ImageIO.getImageReaders(stream) : null;
			if (readers == null || !readers.hasNext()) {
				throw new UnsupportedMessageException("Unsupported image format - please send a JPEG or PNG photo.");
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(stream, true, true);
				long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
				if (pixels > MAX_PHOTO_PIXELS) {
					throw new UnsupportedMessageException("That photo is too large (%d megapixels, limit %d)."
						.formatted(pixels / 1_000_000, MAX_PHOTO_PIXELS / 1_000_000));
				}
				return reader.read(0);
			}
			finally {
				reader.dispose();
			}
		}
	}

	private LoveboxImage render(Painter painter) {
		BufferedImage image = new BufferedImage(DISPLAY_WIDTH, DISPLAY_HEIGHT, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		try {
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			painter.paint(graphics);
		}
		finally {
			graphics.dispose();
		}
		return encode(image);
	}

	private void drawCenteredText(Graphics2D graphics, String text, int maxFontSize, Color color, boolean scrim) {
		TextBlock block = layout(graphics, text.strip(), maxFontSize);
		graphics.setFont(block.font());

		FontMetrics metrics = graphics.getFontMetrics();
		int lineHeight = metrics.getHeight();
		int top = (DISPLAY_HEIGHT - block.lines().size() * lineHeight) / 2;

		if (scrim) {
			graphics.setColor(new Color(0, 0, 0, Math.round(SCRIM_OPACITY * 255)));
			graphics.fillRect(0, Math.max(0, top - BORDER_WIDTH / 2), DISPLAY_WIDTH,
					block.lines().size() * lineHeight + BORDER_WIDTH);
		}

		graphics.setColor(color);
		int baseline = top + metrics.getAscent();
		for (String line : block.lines()) {
			graphics.drawString(line, (DISPLAY_WIDTH - metrics.stringWidth(line)) / 2, baseline);
			baseline += lineHeight;
		}
		log.debug("Rendered {} line(s) at font size {}", block.lines().size(), block.font().getSize());
	}

	/**
	 * Finds the largest font size in {@code [MIN_FONT_SIZE, maxFontSize]} whose wrapped
	 * text still fits the display. Wrapping guarantees the width fits, and a larger font
	 * never produces a shorter block, so the fit is monotonic in the font size and can be
	 * found with a binary search.
	 */
	private static TextBlock layout(Graphics2D graphics, String text, int maxFontSize) {
		int usableWidth = DISPLAY_WIDTH - 2 * BORDER_WIDTH;
		int usableHeight = DISPLAY_HEIGHT - 2 * BORDER_WIDTH;

		TextBlock best = null;
		int low = MIN_FONT_SIZE;
		int high = Math.max(MIN_FONT_SIZE, maxFontSize);
		while (low <= high) {
			int size = (low + high) >>> 1;
			TextBlock candidate = layoutAt(graphics, text, size, usableWidth);
			if (candidate.height() <= usableHeight) {
				best = candidate;
				low = size + 1;
			}
			else {
				high = size - 1;
			}
		}
		if (best == null) {
			throw new UnsupportedMessageException(
					"That message is too long to show legibly on the Lovebox (%d characters). Please split it up."
						.formatted(text.codePointCount(0, text.length())));
		}
		return best;
	}

	private static TextBlock layoutAt(Graphics2D graphics, String text, int fontSize, int usableWidth) {
		Font font = new Font(FONT_NAME, Font.PLAIN, fontSize);
		FontMetrics metrics = graphics.getFontMetrics(font);
		return new TextBlock(wrap(text, metrics, usableWidth), font, metrics.getHeight());
	}

	private static List<String> wrap(String text, FontMetrics metrics, int usableWidth) {
		List<String> lines = new ArrayList<>();
		for (String paragraph : text.split("\n", -1)) {
			wrapParagraph(paragraph.strip(), metrics, usableWidth, lines);
		}
		return lines;
	}

	private static void wrapParagraph(String paragraph, FontMetrics metrics, int usableWidth, List<String> lines) {
		if (paragraph.isEmpty()) {
			lines.add("");
			return;
		}
		StringBuilder line = new StringBuilder();
		for (String word : paragraph.split("\\s+")) {
			for (String piece : breakUp(word, metrics, usableWidth)) {
				if (line.isEmpty()) {
					line.append(piece);
				}
				else if (metrics.stringWidth(line + " " + piece) <= usableWidth) {
					line.append(' ').append(piece);
				}
				else {
					lines.add(line.toString());
					line.setLength(0);
					line.append(piece);
				}
			}
		}
		if (!line.isEmpty()) {
			lines.add(line.toString());
		}
	}

	/**
	 * Splits a single word that is wider than one line. Iterates by code point so that
	 * surrogate pairs - every non-BMP emoji - are never cut in half.
	 */
	private static List<String> breakUp(String word, FontMetrics metrics, int usableWidth) {
		if (metrics.stringWidth(word) <= usableWidth) {
			return List.of(word);
		}
		List<String> pieces = new ArrayList<>();
		StringBuilder piece = new StringBuilder();
		for (int index = 0; index < word.length();) {
			int codePoint = word.codePointAt(index);
			index += Character.charCount(codePoint);
			piece.appendCodePoint(codePoint);
			if (metrics.stringWidth(piece.toString()) > usableWidth
					&& piece.length() > Character.charCount(codePoint)) {
				piece.setLength(piece.length() - Character.charCount(codePoint));
				pieces.add(piece.toString());
				piece.setLength(0);
				piece.appendCodePoint(codePoint);
			}
		}
		pieces.add(piece.toString());
		return pieces;
	}

	private static LoveboxImage encode(BufferedImage image) {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		try {
			if (!ImageIO.write(image, "png", output)) {
				throw new IllegalStateException("No PNG writer is available in this runtime");
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		byte[] png = output.toByteArray();
		return new LoveboxImage("data:image/png;base64," + Base64.getEncoder().encodeToString(png), png);
	}

	@FunctionalInterface
	private interface Painter {

		void paint(Graphics2D graphics);

	}

	private record TextBlock(List<String> lines, Font font, int lineHeight) {

		int height() {
			return this.lines.size() * this.lineHeight;
		}

	}

}
