package at.htl.flowstate;

import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Testing aids for looking at the game without playing it:
 * <ul>
 *   <li>{@code FLOWSTATE_SHOTS=<dir>} saves a screenshot of the window into that folder every
 *       {@code FLOWSTATE_SHOTS_EVERY} seconds of game time (default 1), at most {@code FLOWSTATE_SHOTS_MAX} (default 30).</li>
 * </ul>
 */
public final class DevShots {
    private static final String DIR = DevFlags.SHOTS_DIR;
    private static final double EVERY = DevFlags.SHOTS_EVERY_S;
    private static final int MAX = DevFlags.SHOTS_MAX;

    private static double timer = EVERY;
    private static int taken;

    private DevShots() {}

    /** Call from the game's update. */
    public static void update(double tpf, Scene scene) {
        if (DIR == null || taken >= MAX) return;
        timer -= tpf;
        if (timer > 0) return;
        timer = EVERY;
        taken++;
        WritableImage shot = scene.snapshot(null);
        int w = (int) shot.getWidth(), h = (int) shot.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        PixelReader reader = shot.getPixelReader();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) out.setRGB(x, y, reader.getArgb(x, y));
        }
        try {
            File dir = new File(DIR);
            dir.mkdirs();
            ImageIO.write(out, "png", new File(dir, String.format("shot_%03d.png", taken)));
        } catch (IOException e) {
            System.err.println("[shots] " + e.getMessage());
        }
    }
}
