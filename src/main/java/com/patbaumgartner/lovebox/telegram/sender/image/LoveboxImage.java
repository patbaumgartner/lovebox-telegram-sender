package com.patbaumgartner.lovebox.telegram.sender.image;

/**
 * A PNG image rendered for the Lovebox display.
 *
 * @param dataUri the image as {@code data:image/png;base64,...} URI, as expected by the
 * Lovebox API
 * @param png the raw PNG bytes, used to echo the image back to Telegram
 */
public record LoveboxImage(String dataUri, byte[] png) {

}
