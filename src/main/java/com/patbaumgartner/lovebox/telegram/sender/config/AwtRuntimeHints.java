package com.patbaumgartner.lovebox.telegram.sender.config;

import java.util.List;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

/**
 * GraalVM native-image JNI hints for the AWT/Java2D/ImageIO pipeline used to render
 * Lovebox images ({@code ImageIO.read} → raster manipulation → Scalr convolve →
 * {@code ImageIO.write}).
 * <p>
 * The native AWT libraries (libawt, libjavajpeg, libmlib_image) resolve Java classes,
 * methods and fields through JNI at runtime. Any lookup missing from the native-image
 * configuration fails with errors such as
 * {@code NoSuchFieldError: javax.imageio.plugins.jpeg.JPEGQTable.qTable} or
 * {@code NoSuchFieldError: sun.awt.image.ByteComponentRaster.data} — the latter only once
 * a real photo is decoded, so the image starts fine and breaks on first use. Exactly
 * which lookups are pre-registered varies between GraalVM releases, so this list
 * registers every type the pipeline is known to touch rather than only the ones that
 * happen to fail with the current builder.
 */
public class AwtRuntimeHints implements RuntimeHintsRegistrar {

	private static final List<String> AWT_JNI_TYPES = List.of(
			// ImageIO JPEG decoding (libjavajpeg): JPEGImageReader.initReaderIDs
			// resolves methods on the reader/stream and fields of the table classes.
			"com.sun.imageio.plugins.jpeg.JPEGImageReader", "javax.imageio.stream.ImageInputStream",
			"javax.imageio.plugins.jpeg.JPEGQTable", "javax.imageio.plugins.jpeg.JPEGHuffmanTable",
			// Rasters (libawt/libmlib_image access their fields, e.g.
			// ByteComponentRaster.data, directly through JNI)
			"sun.awt.image.ByteBandedRaster", "sun.awt.image.ByteComponentRaster",
			"sun.awt.image.ByteInterleavedRaster", "sun.awt.image.BytePackedRaster",
			"sun.awt.image.IntegerComponentRaster", "sun.awt.image.IntegerInterleavedRaster",
			"sun.awt.image.ShortBandedRaster", "sun.awt.image.ShortComponentRaster",
			"sun.awt.image.ShortInterleavedRaster", "sun.awt.image.SunWritableRaster",
			"sun.awt.image.ImageRepresentation", "sun.awt.image.BufImgSurfaceData",
			"sun.awt.image.BufImgSurfaceData$ICMColorData",
			// Image and color model types resolved by the native imaging code
			"java.awt.image.BufferedImage", "java.awt.image.Raster", "java.awt.image.ColorModel",
			"java.awt.image.ComponentColorModel", "java.awt.image.DirectColorModel", "java.awt.image.IndexColorModel",
			"java.awt.image.SampleModel", "java.awt.image.BandedSampleModel", "java.awt.image.ComponentSampleModel",
			"java.awt.image.MultiPixelPackedSampleModel", "java.awt.image.PixelInterleavedSampleModel",
			"java.awt.image.SinglePixelPackedSampleModel", "java.awt.image.AffineTransformOp",
			"java.awt.image.ConvolveOp", "java.awt.image.Kernel",
			// Geometry and composite types used by Graphics2D operations
			"java.awt.Color", "java.awt.AlphaComposite", "java.awt.geom.AffineTransform", "java.awt.geom.GeneralPath",
			"java.awt.geom.Path2D", "java.awt.geom.Path2D$Float", "java.awt.geom.Point2D$Float",
			"java.awt.geom.Rectangle2D$Float",
			// Font/glyph rasterization (libfontmanager/freetype JNI)
			"sun.font.Font2D", "sun.font.FontStrike", "sun.font.PhysicalStrike", "sun.font.FreetypeFontScaler",
			"sun.font.GlyphList", "sun.font.StrikeMetrics", "sun.font.TrueTypeFont", "sun.font.Type1Font",
			"sun.font.CharToGlyphMapper",
			// Java2D rendering internals (drawImage/drawString render loops)
			"sun.awt.SunHints", "sun.java2d.Disposer", "sun.java2d.InvalidPipeException", "sun.java2d.NullSurfaceData",
			"sun.java2d.SunGraphics2D", "sun.java2d.SurfaceData", "sun.java2d.pipe.Region",
			"sun.java2d.pipe.RegionIterator", "sun.java2d.loops.Blit", "sun.java2d.loops.BlitBg",
			"sun.java2d.loops.CompositeType", "sun.java2d.loops.DrawGlyphList", "sun.java2d.loops.DrawGlyphListAA",
			"sun.java2d.loops.DrawGlyphListLCD", "sun.java2d.loops.DrawLine", "sun.java2d.loops.DrawParallelogram",
			"sun.java2d.loops.DrawPath", "sun.java2d.loops.DrawPolygons", "sun.java2d.loops.DrawRect",
			"sun.java2d.loops.FillParallelogram", "sun.java2d.loops.FillPath", "sun.java2d.loops.FillRect",
			"sun.java2d.loops.FillSpans", "sun.java2d.loops.GraphicsPrimitive", "sun.java2d.loops.GraphicsPrimitiveMgr",
			"sun.java2d.loops.MaskBlit", "sun.java2d.loops.MaskFill", "sun.java2d.loops.ScaledBlit",
			"sun.java2d.loops.SurfaceType", "sun.java2d.loops.TransformHelper", "sun.java2d.loops.XORComposite");

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		for (String typeName : AWT_JNI_TYPES) {
			TypeReference type = TypeReference.of(typeName);
			hints.jni().registerType(type, MemberCategory.values());
			// Some code paths resolve the same types reflectively (e.g. service lookup)
			hints.reflection().registerType(type, MemberCategory.values());
		}
	}

}
