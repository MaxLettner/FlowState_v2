package at.htl.flowstate.Menu;

import at.htl.flowstate.Assets;
import javafx.scene.Cursor;
import javafx.scene.ImageCursor;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * The golden pixel-art mouse cursor (tools/art/cursor.py): a plain arrow, and a glowing one over anything clickable.
 * <p>
 * JavaFX shrinks a PNG cursor to the platform size (24 px on GTK) with smoothing, which blurs pixel art. A cursor
 * built from a WritableImage is used at its own size, so the art is upscaled here with {@link Assets#frameImage}.
 */
public final class GoldCursor {
    private static final String TEXTURE = "Cursor.png";
    private static final int FRAMES = 2;
    /** The arrow tip in the art (cursor.py HOTSPOT). */
    private static final int TIP = 1;

    private static ImageCursor normal, hover;

    private GoldCursor() {}

    public static ImageCursor normal() {
        load();
        return normal;
    }

    /** Over something clickable: the groove along the arrow turns molten and the arrow glows. */
    public static ImageCursor hover() {
        load();
        return hover;
    }

    /**
     * Puts the arrow on every scene the window shows. FXGL gives each scene its own default cursor right before
     * showing it for the first time, so the arrow is set again whenever the window's root changes.
     */
    public static void install(Stage stage) {
        Scene scene = stage.getScene();
        apply(scene.getRoot());
        scene.rootProperty().addListener((obs, old, root) -> apply(root));
    }

    private static void apply(Parent root) {
        if (root != null && root.getCursor() != Cursor.NONE) root.setCursor(normal());
    }

    private static void load() {
        if (normal != null) return;
        // x2 on a 1080p screen; bigger screens scale the 1920x1080 game up, so the cursor grows with it
        int scale = Math.max(2, (int) Math.round(Screen.getPrimary().getBounds().getHeight() / 540));
        double tip = TIP * scale;
        normal = new ImageCursor(Assets.frameImage(TEXTURE, FRAMES, scale, 0), tip, tip);
        hover = new ImageCursor(Assets.frameImage(TEXTURE, FRAMES, scale, 1), tip, tip);
    }
}
