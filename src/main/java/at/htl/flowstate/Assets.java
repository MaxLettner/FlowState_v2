package at.htl.flowstate;

import at.htl.flowstate.Visuals.Frames;
import at.htl.flowstate.Visuals.VoidTint;
import com.almasb.fxgl.texture.Texture;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads textures from /assets/textures and caches the decoded images.
 * Pixel art is upscaled by an integer factor with nearest-neighbour sampling so it stays crisp.
 */
public final class Assets {
    private static final Map<String, Image> CACHE = new HashMap<>();
    private static final Map<String, Frames> FRAMES = new HashMap<>();

    private Assets() {}

    public static Image image(String name, double width, double height) {
        return cached(name + "@" + width + "x" + height, () -> new Image(url(name), width, height, false, true));
    }

    public static Texture texture(String name, double width, double height) {
        return new Texture(image(name, width, height));
    }

    /** The texture at its native size. */
    public static Image raw(String name) {
        // "void:Name.png" is the texture recoloured to void (the Hollow Ember's copies of the player's weapons)
        if (name.startsWith("void:")) return cached(name + "@raw", () -> VoidTint.recolor(raw(name.substring(5))));
        return cached(name + "@raw", () -> new Image(url(name), 0, 0, false, false));
    }

    /** The texture upscaled by an integer factor (crisp pixels). */
    public static Image pixels(String name, int scale) {
        return cached(name + "@x" + scale, () -> upscale(raw(name), scale));
    }

    /** A crisp ImageView of a single-frame texture. */
    public static ImageView sprite(String name, int scale) {
        ImageView view = new ImageView(pixels(name, scale));
        view.setSmooth(false);
        return view;
    }

    /** A horizontal frame strip of {@code count} equally wide frames, upscaled by {@code scale}. */
    public static Frames frames(String name, int count, int scale) {
        String key = name + "@x" + scale + "/" + count;
        Frames f = FRAMES.get(key);
        if (f == null) {
            Image img = pixels(name, scale);
            f = new Frames(img, count, img.getWidth() / count, img.getHeight());
            FRAMES.put(key, f);
        }
        return f;
    }

    /** One frame of a strip as its own (upscaled) image, e.g. for ImagePattern fills. */
    public static Image frameImage(String name, int count, int scale, int index) {
        return cached(name + "@x" + scale + "#" + index + "/" + count, () -> {
            Image strip = pixels(name, scale);
            int w = (int) (strip.getWidth() / count);
            return new WritableImage(strip.getPixelReader(), index * w, 0, w, (int) strip.getHeight());
        });
    }

    /** Not computeIfAbsent: loaders call each other, and HashMap forbids nested modification. */
    private static Image cached(String key, java.util.function.Supplier<Image> loader) {
        Image img = CACHE.get(key);
        if (img == null) {
            img = loader.get();
            CACHE.put(key, img);
        }
        return img;
    }

    private static String url(String name) {
        URL url = Assets.class.getResource("/assets/textures/" + name);
        if (url == null) throw new IllegalArgumentException("Missing texture: " + name);
        return url.toExternalForm();
    }

    private static Image upscale(Image src, int scale) {
        if (scale == 1) return src;
        int w = (int) src.getWidth(), h = (int) src.getHeight();
        WritableImage out = new WritableImage(w * scale, h * scale);
        PixelReader reader = src.getPixelReader();
        PixelWriter writer = out.getPixelWriter();
        javafx.scene.image.WritablePixelFormat<java.nio.IntBuffer> argb = javafx.scene.image.PixelFormat.getIntArgbInstance();
        int[] row = new int[w];
        int[] scaled = new int[w * scale];
        for (int y = 0; y < h; y++) {
            reader.getPixels(0, y, w, 1, argb, row, 0, w);
            for (int x = 0; x < w; x++) {
                for (int k = 0; k < scale; k++) scaled[x * scale + k] = row[x];
            }
            for (int k = 0; k < scale; k++) {
                writer.setPixels(0, y * scale + k, w * scale, 1, argb, scaled, 0, w * scale);
            }
        }
        return out;
    }
}
