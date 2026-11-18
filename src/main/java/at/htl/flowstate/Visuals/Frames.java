package at.htl.flowstate.Visuals;

import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;

/** A horizontal strip of equally sized animation frames (already upscaled). */
public record Frames(Image image, int count, double width, double height, Rectangle2D[] viewports) {
    public Frames(Image image, int count, double width, double height) {
        this(image, count, width, height, buildViewports(count, width, height));
    }

    /** The cached viewport of a frame: the same instance every call, so ImageView sees no change. */
    public Rectangle2D viewport(int frame) {
        return viewports[Math.floorMod(frame, count)];
    }

    private static Rectangle2D[] buildViewports(int count, double width, double height) {
        Rectangle2D[] v = new Rectangle2D[count];
        for (int f = 0; f < count; f++) v[f] = new Rectangle2D(f * width, 0, width, height);
        return v;
    }
}
