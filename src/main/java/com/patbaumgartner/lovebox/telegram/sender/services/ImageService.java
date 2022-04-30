package com.patbaumgartner.lovebox.telegram.sender.services;

import com.patbaumgartner.lovebox.telegram.sender.utils.Pair;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.NoSuchElementException;
import java.util.Random;
import javax.imageio.ImageIO;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.imgscalr.Scalr;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public record ImageService(ResourceLoader resourceLoader) {

    public static final int DISPLAY_WIDTH = 1280;
    public static final int DISPLAY_HEIGHT = 960;
    public static final int BORDER_WIDTH = 20;
    public static final int INITIAL_FONT_SIZE = 18;
    // Emoji Font Limitations: https://mail.openjdk.java.net/pipermail/2d-dev/2021-May/012975.html
    public static final int MAX_EMOJI_FONT_SIZE = 100;
    public static final String FONT_NAME = "Sans";

    @SneakyThrows
    public Pair<String, InputStream> resizeImageToPair(File file, String text) {
        BufferedImage originalImage = ImageIO.read(file);
        BufferedImage resizedImage =
            Scalr.resize(
                originalImage,
                Scalr.Method.AUTOMATIC,
                Scalr.Mode.AUTOMATIC,
                DISPLAY_WIDTH,
                DISPLAY_HEIGHT,
                Scalr.OP_ANTIALIAS);

        BufferedImage image = new BufferedImage(DISPLAY_WIDTH, DISPLAY_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.black);
        graphics.fillRect(0, 0, DISPLAY_WIDTH, DISPLAY_HEIGHT);

        int x = (DISPLAY_WIDTH - resizedImage.getWidth()) / 2;
        int y = (DISPLAY_HEIGHT - resizedImage.getHeight()) / 2;

        graphics.drawImage(resizedImage, x, y, null);

        if (text != null) {
            drawCenteredMessage(graphics, text);
        }

        graphics.dispose();

        return constructImagePair(image);
    }

    @SneakyThrows
    public Pair<String, InputStream> createTextImageToPair(String message) {
        BufferedImage image = new BufferedImage(DISPLAY_WIDTH, DISPLAY_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();

        // Set background color
        Random rnd = new Random();
        int red = rnd.nextInt(256);
        int green = rnd.nextInt(256);
        int blue = rnd.nextInt(256);
        Color color = new Color(red, green, blue);
        graphics.setColor(color);
        graphics.fillRect(0, 0, DISPLAY_WIDTH, DISPLAY_HEIGHT);

        if (message != null) {
            drawCenteredMessage(graphics, message);
        }

        graphics.dispose();

        return constructImagePair(image);
    }

    @SneakyThrows
    public Pair<String, InputStream> createFixedImageToPair() {
        Resource resource = resourceLoader.getResource("lovebox.jpeg");
        Image image = ImageIO.read(resource.getInputStream());
        image = image.getScaledInstance(DISPLAY_WIDTH, DISPLAY_HEIGHT, Image.SCALE_SMOOTH);
        BufferedImage bufferedImage = new BufferedImage(DISPLAY_WIDTH, DISPLAY_HEIGHT, BufferedImage.TYPE_INT_ARGB);

        Graphics2D graphics = bufferedImage.createGraphics();
        graphics.drawImage(image, 0, 0, null);

        graphics.dispose();

        return constructImagePair(bufferedImage);
    }

    protected void drawCenteredMessage(Graphics2D graphics, String text) {
        String message = text.strip();

        FontMetrics initialFm = graphics.getFontMetrics();
        log.debug("Using initial font with settings: {}", initialFm);

        // Calculate max font
        graphics.setColor(Color.white);
        Font newFont = new Font(FONT_NAME, Font.PLAIN, INITIAL_FONT_SIZE);
        graphics.setFont(newFont);
        FontMetrics newFm = graphics.getFontMetrics();
        String[] lines = message.split("\n");
        // int stringWidth = initialFm.stringWidth(text) + 2 * BORDER_WIDTH;
        int stringWidth = Arrays.stream(lines)
            .map(line -> newFm.stringWidth(line) + 2 * BORDER_WIDTH)
            .mapToInt(v -> v)
            .max()
            .orElseThrow(NoSuchElementException::new);
        double widthRatio = (double) DISPLAY_WIDTH / (double) stringWidth;

        int newFontSize = (int) (newFont.getSize() * widthRatio);
        int fontSizeToUse = Math.min(newFontSize, MAX_EMOJI_FONT_SIZE);
        Font finalFont = new Font(newFont.getName(), newFont.getStyle(), fontSizeToUse);
        graphics.setFont(finalFont);

        // Draw centered string
        FontMetrics fm = graphics.getFontMetrics();
        log.debug("Using new/recalculated font with settings: {}", fm);

        int lineHeight = fm.getHeight();
        int yInitialOffset = (lines.length - 1) * lineHeight;

        int x = 0;
        int y = (fm.getAscent() + (DISPLAY_HEIGHT - (fm.getAscent() + fm.getDescent()) - yInitialOffset) / 2);

        for (String line : lines) {
            x = (DISPLAY_WIDTH - fm.stringWidth(line)) / 2;
            graphics.drawString(line, x, y);
            y += lineHeight;
        }
    }

    protected Pair constructImagePair(BufferedImage image) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        String base64Image = Base64.getEncoder().encodeToString(output.toByteArray());

        return new Pair("data:image/png;base64," + base64Image, new ByteArrayInputStream(output.toByteArray()));
    }
}
