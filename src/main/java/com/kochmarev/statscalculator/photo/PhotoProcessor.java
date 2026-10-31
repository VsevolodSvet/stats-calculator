package com.kochmarev.statscalculator.photo;

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class PhotoProcessor {
    static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final long MAX_PIXELS = 12_000_000;

    private PhotoProcessor() {
    }

    static Processed process(byte[] bytes) {
        if (bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Choose a non-empty image up to 5 MiB.");
        }
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalidImage();
            var reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!format.equalsIgnoreCase("JPEG") && !format.equalsIgnoreCase("PNG")) {
                    throw invalidImage();
                }
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 32 || height < 32 || width > 6000 || height > 6000 || (long) width * height > MAX_PIXELS) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Use an image at least 32 pixels per side, at most 6000 per side and 12 megapixels total.");
                }
                var decoded = reader.read(0);
                double scale = Math.min(1, 1200.0 / Math.max(width, height));
                var normalized = new BufferedImage(Math.max(1, (int) Math.round(width * scale)),
                        Math.max(1, (int) Math.round(height * scale)), BufferedImage.TYPE_INT_RGB);
                var graphics = normalized.createGraphics();
                try {
                    graphics.setColor(Color.WHITE);
                    graphics.fillRect(0, 0, normalized.getWidth(), normalized.getHeight());
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    graphics.drawImage(decoded, 0, 0, normalized.getWidth(), normalized.getHeight(), null);
                } finally {
                    graphics.dispose();
                    decoded.flush();
                }
                var output = new ByteArrayOutputStream();
                if (!ImageIO.write(normalized, "png", output)) {
                    throw new IllegalStateException("PNG encoder is unavailable.");
                }
                byte[] encoded = output.toByteArray();
                if (encoded.length > MAX_BYTES) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The normalized image is too large. Use a smaller image.");
                }
                var processed = new Processed(encoded, analyze(normalized));
                normalized.flush();
                return processed;
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot decode this image. Choose a valid JPEG or PNG.", exception);
        }
    }

    static Analysis analyzeStored(byte[] bytes) {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalStateException("Stored normalized photo cannot be decoded.");
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                var image = reader.read(0);
                try {
                    return analyze(image);
                } finally {
                    image.flush();
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Stored normalized photo cannot be read.", exception);
        }
    }

    static Analysis analyze(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        double sum = 0;
        double clipped = 0;
        double edgeSum = 0;
        double edgeSquares = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double light = light(image.getRGB(x, y));
                sum += light;
                if (light < 10 || light > 245) clipped++;
                if (x > 0 && x < width - 1 && y > 0 && y < height - 1) {
                    double edge = 4 * light - light(image.getRGB(x - 1, y)) - light(image.getRGB(x + 1, y))
                            - light(image.getRGB(x, y - 1)) - light(image.getRGB(x, y + 1));
                    edgeSum += edge;
                    edgeSquares += edge * edge;
                }
            }
        }
        double count = (double) width * height;
        double edgeCount = (double) (width - 2) * (height - 2);
        double brightness = sum / count;
        double detail = Math.max(0, edgeSquares / edgeCount - Math.pow(edgeSum / edgeCount, 2));
        var feedback = new ArrayList<String>();
        if (brightness < 55) feedback.add("The whole image is relatively dark. Consider more even lighting.");
        else if (brightness > 210) feedback.add("The whole image is relatively bright. Check for washed-out details.");
        else feedback.add("Average whole-image brightness is in the suggested range.");
        if (clipped / count > 0.3) feedback.add("Many pixels are near black or white. Check whether important details are lost.");
        if (detail < 80) feedback.add("Low edge detail may indicate blur or a plain background. Check the preview; this cannot establish face sharpness.");
        else feedback.add("Edge detail is present. This does not guarantee that the face is in focus.");
        if (Math.min(width, height) < 300) feedback.add("The stored image is small. A higher-resolution source may show more detail.");
        feedback.add("Whole-image heuristics only: no face detection, face rating, framing or pose assessment. These thresholds are not validated.");
        return new Analysis(width, height, Math.round(brightness * 100.0 / 255),
                Math.round(detail), List.copyOf(feedback));
    }

    private static double light(int rgb) {
        return 0.2126 * ((rgb >> 16) & 255) + 0.7152 * ((rgb >> 8) & 255) + 0.0722 * (rgb & 255);
    }

    private static ResponseStatusException invalidImage() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid JPEG or PNG image. Other formats are not supported.");
    }

    record Processed(byte[] bytes, Analysis analysis) {
    }

    record Analysis(int width, int height, long brightnessPercent, long edgeDetail, List<String> feedback) {
    }
}
