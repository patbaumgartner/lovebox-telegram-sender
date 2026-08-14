package com.patbaumgartner.lovebox.telegram.sender.config;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;

import static org.assertj.core.api.Assertions.assertThat;

class AwtRuntimeHintsTests {

	@Test
	void registersJniHintsForImagePipelineTypes() {
		RuntimeHints hints = new RuntimeHints();
		new AwtRuntimeHints().registerHints(hints, getClass().getClassLoader());

		assertThat(hints.jni().typeHints().map(hint -> hint.getType().getName())).contains(
				// JPEG decoding
				"com.sun.imageio.plugins.jpeg.JPEGImageReader", "javax.imageio.plugins.jpeg.JPEGQTable",
				"javax.imageio.plugins.jpeg.JPEGHuffmanTable",
				// JPEG encoding
				"com.sun.imageio.plugins.jpeg.JPEGImageWriter", "javax.imageio.stream.ImageOutputStream",
				// Raster fields accessed natively (e.g. ByteComponentRaster.data)
				"sun.awt.image.ByteComponentRaster", "sun.awt.image.IntegerComponentRaster",
				"sun.awt.image.SunWritableRaster",
				// Rendering internals
				"sun.java2d.SunGraphics2D", "java.awt.image.BufferedImage");
	}

	@Test
	void registersReflectionHintsForImagePipelineTypes() {
		RuntimeHints hints = new RuntimeHints();
		new AwtRuntimeHints().registerHints(hints, getClass().getClassLoader());

		assertThat(hints.reflection().getTypeHint(TypeReference.of("sun.awt.image.ByteComponentRaster"))).isNotNull();
	}

}
