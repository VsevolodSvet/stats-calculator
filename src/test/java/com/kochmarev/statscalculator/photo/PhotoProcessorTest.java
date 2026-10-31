package com.kochmarev.statscalculator.photo;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhotoProcessorTest {
    static byte[] image(int width, int height, Color color, String format) throws Exception {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);
        image.flush();
        return output.toByteArray();
    }

    @Test
    void normalizesJpegToPngAndResizesWithoutChangingAspectRatio() throws Exception {
        var processed = PhotoProcessor.process(image(2400, 1200, Color.GRAY, "jpeg"));
        assertThat(processed.analysis().width()).isEqualTo(1200);
        assertThat(processed.analysis().height()).isEqualTo(600);
        var decoded = ImageIO.read(new ByteArrayInputStream(processed.bytes()));
        assertThat(decoded.getWidth()).isEqualTo(1200);
        assertThat(processed.bytes()).startsWith((byte) 137, (byte) 80, (byte) 78, (byte) 71);
        assertThat(processed.analysis().feedback()).anyMatch(text -> text.contains("no face detection"));
    }

    @Test
    void feedbackReflectsWholeImageLightingNotAClaimAboutFaces() throws Exception {
        var dark = PhotoProcessor.process(image(64, 64, Color.BLACK, "png"));
        var bright = PhotoProcessor.process(image(64, 64, Color.WHITE, "png"));
        assertThat(dark.analysis().brightnessPercent()).isZero();
        assertThat(bright.analysis().brightnessPercent()).isEqualTo(100);
        assertThat(dark.analysis().edgeDetail()).isZero();
        assertThat(dark.analysis().feedback()).anyMatch(text -> text.contains("relatively dark"));
        assertThat(bright.analysis().feedback()).anyMatch(text -> text.contains("relatively bright"));
    }

    @Test
    void rejectsMalformedUnsupportedTinyOversizedAndExcessivePixelImages() throws Exception {
        for (byte[] bytes : new byte[][]{new byte[0], new byte[PhotoProcessor.MAX_BYTES + 1],
                "not an image".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                image(64, 64, Color.GRAY, "gif"), image(16, 16, Color.GRAY, "png"),
                image(6001, 32, Color.GRAY, "png"), image(4000, 3100, Color.GRAY, "png")}) {
            assertThatThrownBy(() -> PhotoProcessor.process(bytes)).isInstanceOf(ResponseStatusException.class);
        }
    }

    @Test
    void discardsTrailingContentWhenReencoding() throws Exception {
        byte[] image = image(64, 64, Color.GRAY, "png");
        var input = new ByteArrayOutputStream();
        input.write(image);
        input.write("private EXIF-like location payload".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var processed = PhotoProcessor.process(input.toByteArray());
        assertThat(new String(processed.bytes(), java.nio.charset.StandardCharsets.ISO_8859_1))
                .doesNotContain("private EXIF-like location payload");
    }
}
