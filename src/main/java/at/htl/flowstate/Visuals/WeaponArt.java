package at.htl.flowstate.Visuals;

import at.htl.flowstate.Assets;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

/**
 * Places the diagonal weapon textures from tools/art/weapons.py. Convention: square canvas S x S,
 * handle end at (2.5, S - 2.5), tip at (S - 2.5, 2.5), shown at x2.
 */
public final class WeaponArt {
    public static final int SCALE = 2;
    /** How far the handle end sticks out behind the hand. */
    private static final double GRIP = 6;

    private WeaponArt() {}

    /** Weapon hanging straight down from (pivotX, pivotY): used by swings (entity rotation points it). */
    public static ImageView hanging(String texture, double pivotX, double pivotY, double size) {
        ImageView v = view(texture, size);
        double d = v.getFitWidth();
        double half = halfAxis(v, size);
        v.setRotate(135);
        v.setTranslateX(pivotX - d / 2);
        v.setTranslateY(pivotY - GRIP * size + half - d / 2);
        return v;
    }

    /** Weapon pointing right with its tip at (tipX, tipY): stabs. */
    public static ImageView tipAt(String texture, double tipX, double tipY, double size) {
        ImageView v = view(texture, size);
        double d = v.getFitWidth();
        v.setRotate(45);
        v.setTranslateX(tipX - halfAxis(v, size) - d / 2);
        v.setTranslateY(tipY - d / 2);
        return v;
    }

    /** Weapon pointing right, centred on (cx, cy): thrown weapons. */
    public static ImageView centred(String texture, double cx, double cy, double size) {
        ImageView v = view(texture, size);
        double d = v.getFitWidth();
        v.setRotate(45);
        v.setTranslateX(cx - d / 2);
        v.setTranslateY(cy - d / 2);
        return v;
    }

    /** Displayed handle-to-tip length of a weapon texture. */
    public static double length(String texture, double size) {
        return (Assets.raw(texture).getWidth() - 5) * Math.sqrt(2) * SCALE * size;
    }

    private static ImageView view(String texture, double size) {
        Image img = Assets.pixels(texture, SCALE);
        ImageView v = new ImageView(img);
        v.setSmooth(false);
        v.setFitWidth(img.getWidth() * size);
        v.setFitHeight(img.getHeight() * size);
        return v;
    }

    /** Distance from the image centre to the handle (or tip) after rotating onto an axis. */
    private static double halfAxis(ImageView v, double size) {
        double nativeSize = v.getImage().getWidth() / SCALE;
        return (nativeSize / 2 - 2.5) * SCALE * size * Math.sqrt(2);
    }
}
