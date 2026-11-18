package at.htl.flowstate.Visuals;

import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;

/** A horizontal strip of equally sized animation frames (already upscaled). */
public record Frames(Image image, int count, double width, double height) {
    public Rectangle2D viewport(int frame) {
        int f = Math.floorMod(frame, count);
        return new Rectangle2D(f * width, 0, width, height);
    }
}
