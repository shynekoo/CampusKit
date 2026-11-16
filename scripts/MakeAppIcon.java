import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Turns the 1024px app icon artwork into the density buckets Android needs.
 *
 * <p>Two variants come out of the same picture:</p>
 * <ul>
 *   <li>{@code ic_app.png} — the artwork with its outer white margin trimmed back to a rounded
 *       square and the corners made transparent. This is the legacy icon (Android 7 and any
 *       launcher that ignores adaptive icons), so it must carry its own shape.</li>
 *   <li>{@code ic_app_background.png} — full bleed, no transparency. An adaptive icon's
 *       background layer is masked by the launcher, so a pre-rounded picture would show its white
 *       corners through a squircle or circular mask.</li>
 * </ul>
 *
 * <p>Usage: {@code java MakeAppIcon.java <source.jpg> <res dir>}</p>
 */
public class MakeAppIcon {
    /** Launcher icon sizes in dp; multiplied by the bucket scale below. */
    private static final int[] LEGACY_DP = {48, 72, 96, 144, 192};
    /** An adaptive icon's background layer is 108dp square. */
    private static final int[] ADAPTIVE_DP = {108, 162, 216, 324, 432};
    private static final String[] BUCKETS = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};

    public static void main(String[] args) throws Exception {
        File source = new File(args[0]);
        File res = new File(args[1]);
        BufferedImage art = ImageIO.read(source);
        if (art == null) throw new IllegalStateException("Unreadable image: " + source);
        int side = Math.min(art.getWidth(), art.getHeight());
        if (art.getWidth() != art.getHeight())
            art = art.getSubimage((art.getWidth() - side) / 2, (art.getHeight() - side) / 2, side, side);

        // The artwork sits on white, so the corner distance to the first coloured pixel gives the
        // drawn radius: a circle of radius r meets the diagonal at r * (1 - 1/sqrt(2)) = 0.293r.
        double diagonal = firstInk(art);
        double radius = diagonal / 0.2929;
        if (radius < side * 0.10 || radius > side * 0.30) radius = side * 0.22;
        System.out.printf("source %dx%d, corner ink at %.1fpx -> radius %.1fpx%n", side, side, diagonal, radius);

        BufferedImage rounded = roundCorners(art, radius);
        BufferedImage bleed = bleed(art);
        for (int i = 0; i < BUCKETS.length; i++) {
            write(rounded, new File(res, "mipmap-" + BUCKETS[i] + "/ic_app.png"), LEGACY_DP[i], true);
            write(bleed, new File(res, "mipmap-" + BUCKETS[i] + "/ic_app_background.png"), ADAPTIVE_DP[i], false);
        }
    }

    /**
     * Paints the white margin around the rounded artwork with the colour just inside it.
     *
     * <p>An adaptive icon's background layer is masked by whatever shape the launcher uses, and a
     * squircle can be squarer than the picture's own corners, which would expose a white wedge.
     * Only the four corner squares are touched, and only pixels that are actually white, so the
     * pale blue gradient and the white clock face in the middle survive untouched.</p>
     */
    private static BufferedImage bleed(BufferedImage art) {
        int side = art.getWidth();
        int region = (int) Math.round(side * 0.30);
        BufferedImage out = new BufferedImage(side, side, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(art, 0, 0, null);
        g.dispose();
        for (int cornerX = 0; cornerX < 2; cornerX++) {
            for (int cornerY = 0; cornerY < 2; cornerY++) {
                int x0 = cornerX == 0 ? 0 : side - region;
                int y0 = cornerY == 0 ? 0 : side - region;
                int step = cornerX == 0 ? 1 : -1;
                for (int y = y0; y < y0 + region; y++) {
                    for (int x = x0; x < x0 + region; x++) {
                        if (!white(art.getRGB(x, y))) continue;
                        int sample = x;
                        while (sample > 0 && sample < side - 1) {
                            sample += step;
                            if (!white(art.getRGB(sample, y))) break;
                        }
                        out.setRGB(x, y, art.getRGB(sample, y));
                    }
                }
            }
        }
        return out;
    }

    /** White means white on every channel, with only JPEG noise allowed between them. */
    private static boolean white(int argb) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        int low = Math.min(r, Math.min(g, b)), high = Math.max(r, Math.max(g, b));
        return low >= 242 && high - low <= 12;
    }

    /** Distance along the top-left diagonal to the first pixel that is not the white margin. */
    private static double firstInk(BufferedImage image) {
        int limit = image.getWidth() / 4;
        for (int d = 0; d < limit; d++) {
            int rgb = image.getRGB(d, d) & 0xFFFFFF;
            int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
            if (Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) > 10 || Math.min(r, Math.min(g, b)) < 240)
                return d;
        }
        return 0;
    }

    private static BufferedImage roundCorners(BufferedImage art, double radius) {
        int side = art.getWidth();
        BufferedImage out = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setClip(new java.awt.geom.RoundRectangle2D.Double(0, 0, side, side, radius * 2, radius * 2));
        g.drawImage(art, 0, 0, null);
        g.dispose();
        return out;
    }

    /** Halves the picture down to about twice the target, then does one bicubic step. */
    private static void write(BufferedImage source, File target, int size, boolean alpha) throws Exception {
        BufferedImage current = source;
        while (current.getWidth() / 2 >= size * 2) current = scale(current, current.getWidth() / 2, alpha);
        BufferedImage scaled = scale(current, size, alpha);
        target.getParentFile().mkdirs();
        ImageIO.write(scaled, "png", target);
    }

    private static BufferedImage scale(BufferedImage source, int size, boolean alpha) {
        BufferedImage out = new BufferedImage(size, size,
            alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(source, 0, 0, size, size, null);
        g.dispose();
        return out;
    }
}
