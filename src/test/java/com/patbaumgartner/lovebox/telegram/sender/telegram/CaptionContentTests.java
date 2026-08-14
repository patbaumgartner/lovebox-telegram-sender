package com.patbaumgartner.lovebox.telegram.sender.telegram;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CaptionContentTests {

	private static final Instant SENT_AT = Instant.parse("2024-01-01T10:00:00Z");

	@Test
	void showsTheMessageAndItsStatus() {
		String caption = new CaptionContent("hello", SENT_AT).render("sending");

		assertThat(caption).contains("hello").contains("Status: [sending].").contains("2024-01-01");
	}

	@Test
	void keepsSquareBracketsWrittenBySender() {
		String caption = new CaptionContent("meeting [today]. bring cake", SENT_AT).render("read");

		assertThat(caption).contains("meeting [today]. bring cake").contains("Status: [read].");
	}

	@Test
	void keepsRegularExpressionMetacharacters() {
		String caption = new CaptionContent("costs $1 and \\ backslash", SENT_AT).render("read");

		assertThat(caption).contains("costs $1 and \\ backslash");
	}

	@Test
	void collapsesNewlinesSoTheStatusLineStaysRecognisable() {
		String caption = new CaptionContent("first\nsecond", SENT_AT).render("read");

		assertThat(caption).contains("first second");
	}

	@Test
	void acceptsAMissingMessageText() {
		assertThat(new CaptionContent(null, SENT_AT).render("sending")).contains("Status: [sending].");
	}

	@Test
	void staysWithinTheTelegramCaptionLimit() {
		String caption = new CaptionContent("x".repeat(4096), SENT_AT).render("sending");

		assertThat(caption).hasSizeLessThanOrEqualTo(CaptionContent.TELEGRAM_CAPTION_LIMIT)
			.contains("…")
			.contains("Status: [sending].");
	}

	@Test
	void neverTruncatesInsideASurrogatePair() {
		String caption = new CaptionContent("🚀".repeat(2000), SENT_AT).render("sending");

		assertThat(caption).hasSizeLessThanOrEqualTo(CaptionContent.TELEGRAM_CAPTION_LIMIT);
		// A well-formed pair decodes to one non-surrogate code point; a split pair
		// leaves a lone surrogate behind, which renders as a replacement character.
		assertThat(caption.codePoints().noneMatch(codePoint -> codePoint >= 0xD800 && codePoint <= 0xDFFF))
			.as("caption contains no unpaired surrogate")
			.isTrue();
	}

}
