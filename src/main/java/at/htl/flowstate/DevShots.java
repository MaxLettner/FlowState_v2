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
 *   <li>{@code FLOWSTATE_AUTOSTART=1} skips the main menu and starts a run right away.</li>
 *   <li>{@code FLOWSTATE_SHOTS=<dir>} saves a screenshot of the window into that folder every
 *       {@code FLOWSTATE_SHOTS_EVERY} seconds of game time (default 1), at most {@code FLOWSTATE_SHOTS_MAX} (default 30).</li>
 * </ul>
 */
public final class DevShots {
    public static final boolean AUTOSTART = "1".equals(System.getenv("FLOWSTATE_AUTOSTART"));
    private static final String DIR = System.getenv("FLOWSTATE_SHOTS");
    private static final double EVERY = number("FLOWSTATE_SHOTS_EVERY", 1.0);
    private static final int MAX = (int) number("FLOWSTATE_SHOTS_MAX", 30);

    private static double timer = EVERY;
    private static int taken;

    private DevShots() {}

    private static double number(String env, double fallback) {
        try {
            String v = System.getenv(env);
            return v == null ? fallback : Double.parseDouble(v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

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
