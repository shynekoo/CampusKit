package io.qzxy.lite.preview;

import android.graphics.Bitmap;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.common.GlobalHistogramBinarizer;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

/**
 * Local QR decoding with a scale pyramid.
 *
 * A real bathroom sticker photographed at full resolution failed to decode with a single pass:
 * ZXing only recovered it once the image was reduced to roughly 1/5 of its size (see
 * analysis/private/qr-probe). Screen codes decode at any scale, so the pyramid is only extra work
 * for the hard cases. Every attempt stays local; nothing is uploaded.
 */
final class QrDecoder {
    /**
     * Long-side ratios tried for still images, ordered so clean codes succeed immediately and the
     * window that recovered the real bathroom sticker (0.16-0.20) is reached early. Verified offline
     * with the same ZXing version; see analysis/private/qr-probe.
     */
    private static final double[] IMAGE_SCALES = {1.0, 0.5, 0.25, 0.2, 0.16, 0.4, 0.35, 0.3, 0.6, 0.8, 0.125};
    /** Integer reductions tried for camera frames, where per-frame cost matters. */
    private static final int[] FRAME_DIVISORS = {1, 2, 4, 6};
    private static final int MIN_SIDE = 24;

    private static Map<DecodeHintType, Object> hints(boolean tryHarder) {
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, EnumSet.of(BarcodeFormat.QR_CODE));
        hints.put(DecodeHintType.ALSO_INVERTED, Boolean.TRUE);
        if (tryHarder) hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        return hints;
    }

    private static String decode(LuminanceSource source, boolean tryHarder) throws Exception {
        MultiFormatReader reader = new MultiFormatReader();
        try { return reader.decode(new BinaryBitmap(new HybridBinarizer(source)), hints(tryHarder)).getText(); }
        catch (com.google.zxing.ReaderException missed) {
            // Global thresholding complements local blocks on backlit LCDs and low-contrast codes.
            reader.reset();
            try { return reader.decode(new BinaryBitmap(new GlobalHistogramBinarizer(source)), hints(tryHarder)).getText(); }
            catch (com.google.zxing.ReaderException stillMissing) {
                // Invert luminance BEFORE threshold estimation as well. Inverting only the
                // resulting black/white matrix cannot recover a dark LCD's lost contrast.
                reader.reset();
                return reader.decode(new BinaryBitmap(new HybridBinarizer(source.invert())), hints(tryHarder)).getText();
            }
        }
    }

    /** Single attempt against an already prepared source. */
    static String luminance(LuminanceSource source) throws Exception {
        return decode(source, false);
    }

    static String frame(byte[] bytes, int width, int height) throws Exception {
        if (bytes == null || width <= 0 || height <= 0 || bytes.length < width * height)
            throw NotFoundException.getNotFoundInstance();
        Exception last = NotFoundException.getNotFoundInstance();
        for (int divisor : FRAME_DIVISORS) {
            if (divisor != 1 && (width / divisor < MIN_SIDE || height / divisor < MIN_SIDE)) continue;
            byte[] grey = divisor == 1 ? bytes : reduce(bytes, width, height, divisor);
            int frameWidth = width / divisor, frameHeight = height / divisor;
            LuminanceSource source = new PlanarYUVLuminanceSource(grey, frameWidth, frameHeight, 0, 0, frameWidth, frameHeight, false);
            try { return decode(source, true); } catch (Exception failure) { last = failure; }
            // Keep the full frame above for off-centre codes. The centre crop changes threshold
            // estimation when a small bright device screen is surrounded by dark housing.
            int side = Math.min(frameWidth, frameHeight) * 2 / 3;
            if (side >= MIN_SIDE) {
                try { return decode(source.crop((frameWidth-side)/2,(frameHeight-side)/2,side,side),true); }
                catch (Exception failure) { last = failure; }
            }
        }
        throw last;
    }

    static String bitmap(Bitmap bitmap) throws Exception {
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        if (width <= 0 || height <= 0) throw NotFoundException.getNotFoundInstance();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        Exception last = NotFoundException.getNotFoundInstance();
        for (double scale : IMAGE_SCALES) {
            int targetWidth = Math.max(1, (int) Math.round(width * scale));
            int targetHeight = Math.max(1, (int) Math.round(height * scale));
            if (targetWidth < MIN_SIDE || targetHeight < MIN_SIDE) break;
            int[] source = scale == 1.0 ? pixels : bilinear(pixels, width, height, targetWidth, targetHeight);
            for (boolean tryHarder : new boolean[]{false, true}) {
                try { return decode(new RGBLuminanceSource(targetWidth, targetHeight, source), tryHarder); }
                catch (Exception failure) { last = failure; }
            }
        }
        throw last;
    }

    /** Bilinear resampling of an ARGB buffer, matching the configuration verified offline. */
    private static int[] bilinear(int[] pixels, int width, int height, int targetWidth, int targetHeight) {
        int[] out = new int[targetWidth * targetHeight];
        double xRatio = width / (double) targetWidth, yRatio = height / (double) targetHeight;
        for (int y = 0; y < targetHeight; y++) {
            double sourceY = (y + 0.5) * yRatio - 0.5;
            int y0 = (int) Math.floor(sourceY);
            double fy = sourceY - y0;
            int top = Math.min(Math.max(y0, 0), height - 1), bottom = Math.min(top + 1, height - 1);
            for (int x = 0; x < targetWidth; x++) {
                double sourceX = (x + 0.5) * xRatio - 0.5;
                int x0 = (int) Math.floor(sourceX);
                double fx = sourceX - x0;
                int left = Math.min(Math.max(x0, 0), width - 1), right = Math.min(left + 1, width - 1);
                int topLeft = pixels[top * width + left], topRight = pixels[top * width + right];
                int bottomLeft = pixels[bottom * width + left], bottomRight = pixels[bottom * width + right];
                out[y * targetWidth + x] = blend(blend(topLeft, topRight, fx), blend(bottomLeft, bottomRight, fx), fy);
            }
        }
        return out;
    }

    private static int blend(int first, int second, double weight) {
        int result = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int a = (first >>> shift) & 0xff, b = (second >>> shift) & 0xff;
            result |= (int) Math.round(a + (b - a) * weight) << shift;
        }
        return result;
    }

    /** Box-average reduction of the Y plane of an NV21 frame. */
    private static byte[] reduce(byte[] bytes, int width, int height, int divisor) {
        int targetWidth = width / divisor, targetHeight = height / divisor;
        byte[] out = new byte[targetWidth * targetHeight];
        int samples = divisor * divisor;
        for (int y = 0; y < targetHeight; y++) {
            int row = y * divisor * width;
            for (int x = 0; x < targetWidth; x++) {
                int sum = 0, column = row + x * divisor;
                for (int dy = 0; dy < divisor; dy++)
                    for (int dx = 0; dx < divisor; dx++) sum += bytes[column + dy * width + dx] & 0xff;
                out[y * targetWidth + x] = (byte) (sum / samples);
            }
        }
        return out;
    }
}
