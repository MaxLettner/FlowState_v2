package at.htl.flowstate.Visuals;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

/**
 * Recolours art to void (the Hollow Ember's copies of the player's weapons), like void_recolor() in
 * tools/art/boss3_concepts.py: warm / ember pixels go to the violet glow ramp, the rest to the violet-black body
 * ramp by brightness, alpha kept. Assets loads "void:Name.png" through here.
 */
public final class VoidTint {
    private static final int[] BODY = {0x9484b4, 0x4c3c6a, 0x2c2044, 0x1a1228, 0x0c0814};
    private static final int[] GLOW = {0xf6eaff, 0xd8b4ff, 0xa86cff, 0x7a3ae0, 0x4a1a9a};

    private VoidTint() {}

    public static Image recolor(Image src) {
        int w = (int) src.getWidth(), h = (int) src.getHeight();
        WritableImage out = new WritableImage(w, h);
        PixelReader in = src.getPixelReader();
        PixelWriter writer = out.getPixelWriter();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = in.getArgb(x, y);
                int a = argb >>> 24;
                if (a == 0) continue;
                int r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
                double lum = (r + g + b) / 765.0;
                int rgb;
                if (r - b > 60 && r > 110) rgb = GLOW[(int) (clamp(1 - lum * 1.3) * 4.99)];
                else if (a == 255) rgb = BODY[(int) (clamp(1 - lum / 0.4) * 4.99)];
                else rgb = argb & 0xffffff;
                writer.setArgb(x, y, a << 24 | rgb);
            }
        }
        return out;
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
