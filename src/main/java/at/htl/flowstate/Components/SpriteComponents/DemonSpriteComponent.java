package at.htl.flowstate.Components.SpriteComponents;

import at.htl.flowstate.Assets;
import at.htl.flowstate.Components.Enemies.DemonType;
import at.htl.flowstate.Visuals.FrameSprite;
import at.htl.flowstate.Visuals.Frames;
import com.almasb.fxgl.entity.component.Component;
import javafx.scene.Node;
import javafx.scene.effect.Blend;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.ColorInput;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Effect;
import javafx.scene.paint.Color;

import java.util.HashMap;
import java.util.Map;

/**
 * Demon sprite: named strips {@code <prefix>_<anim>.png} from tools/art/demons.py, shown at x2 and placed by
 * the {@link DemonType} anchor. The behaviour picks the animation ({@link #loop}, {@link #once}, {@link #show});
 * on top of it this draws the hit flash, the icy tint while frozen, stun stars and a pulsing glow that
 * telegraphs an attack.
 */
public class DemonSpriteComponent extends Component {
    private static final int SCALE = 2;
    private static final double FLASH_TIME = 0.12;
    private static final double TURN_SPEED = 16;
    private static final Color ICE = Color.rgb(170, 225, 255, 0.6);

    private final DemonType type;
    private final Map<String, Frames> strips = new HashMap<>();
    private final FrameSprite sprite;
    private final FrameSprite stunStars;

    private String current;
    private boolean oneShot;
    private int facing = 1;
    private double scaleX = 1;
    private double flashLeft;
    private boolean frozen;
    private Color telegraph;
    private double telegraphTime;

    public DemonSpriteComponent(DemonType type) {
        this.type = type;
        Frames first = strip(type.flying ? "fly" : "idle");
        sprite = new FrameSprite(first);
        current = type.flying ? "fly" : "idle";
        sprite.loop(first, 8);
        double anchorY = type.flying ? type.height / 2 : type.height;
        sprite.setTranslateX(type.width / 2 - type.anchorX * SCALE);
        sprite.setTranslateY(anchorY - type.anchorY * SCALE);

        Frames stars = Assets.frames("StunStars.png", 4, SCALE);
        stunStars = new FrameSprite(stars).loop(stars, 8);
        stunStars.setTranslateX(type.width / 2 - stars.width() / 2);
        stunStars.setTranslateY(-4 - stars.height());
        stunStars.setVisible(false);
    }

    private Frames strip(String name) {
        return strips.computeIfAbsent(name, n -> {
            String file = type.prefix + "_" + n + ".png";
            int count = (int) Math.round(Assets.pixels(file, SCALE).getWidth() / (type.frameW * SCALE));
            return Assets.frames(file, Math.max(1, count), SCALE);
        });
    }

    @Override
    public void onAdded() {
        entity.getViewComponent().addChild(sprite);
        entity.getViewComponent().addChild(stunStars);
    }

    @Override
    public void onUpdate(double tpf) {
        scaleX += (facing - scaleX) * Math.min(1, tpf * TURN_SPEED);
        if (Math.abs(facing - scaleX) < 0.02) scaleX = facing;
        double s = Math.abs(scaleX) < 0.15 ? Math.signum(scaleX == 0 ? facing : scaleX) * 0.15 : scaleX;
        sprite.setScaleX(s);

        boolean refresh = false;
        if (flashLeft > 0) {
            flashLeft -= tpf;
            if (flashLeft <= 0) refresh = true;
        }
        if (telegraph != null) {
            telegraphTime += tpf;
            refresh = true;
        }
        if (refresh) refreshEffect();
    }

    //-----animation-----
    /** Loops a strip (no restart when it is already looping). Cuts a running one-shot. */
    public void loop(String anim, double fps) {
        if (anim.equals(current) && !oneShot) return;
        current = anim;
        oneShot = false;
        sprite.loop(strip(anim), fps);
    }

    /** Plays a strip once, holding the last frame; {@code onDone} may be null. */
    public void once(String anim, double fps, Runnable onDone) {
        current = anim;
        oneShot = true;
        sprite.once(strip(anim), fps, () -> {
            oneShot = false;
            if (onDone != null) onDone.run();
        });
    }

    /** Holds one frame of a strip. */
    public void show(String anim, int frame) {
        Frames f = strip(anim);
        current = anim + "#" + frame;
        oneShot = false;
        sprite.show(f, Math.max(0, Math.min(f.count() - 1, frame)));
    }

    public boolean isOneShotPlaying() {
        return oneShot;
    }

    public void setFacing(int direction) {
        if (direction != 0) facing = direction > 0 ? 1 : -1;
    }

    public int getFacing() {
        return facing;
    }

    /** Tilts the sprite (a diving bat points where it flies); degrees, clockwise. */
    public void setTilt(double degrees) {
        sprite.setRotate(degrees);
    }

    //-----feedback-----
    public void flash() {
        flashLeft = FLASH_TIME;
        refreshEffect();
    }

    public void setFrozen(boolean on) {
        if (frozen == on) return;
        frozen = on;
        sprite.setPaused(on);
        refreshEffect();
    }

    public void setStunned(boolean on) {
        stunStars.setVisible(on);
    }

    /** A pulsing glow in this colour (an attack is coming); null turns it off. */
    public void setTelegraph(Color color) {
        if (color == null && telegraph == null) return;
        if (color != null && telegraph == null) telegraphTime = 0;
        telegraph = color;
        refreshEffect();
    }

    private void refreshEffect() {
        Effect e = null;
        if (frozen) {
            Frames f = sprite.getFrames();
            e = new Blend(BlendMode.SRC_ATOP, null, new ColorInput(0, 0, f.width(), f.height(), ICE));
        }
        if (telegraph != null) {
            double pulse = 0.5 + 0.5 * Math.sin(telegraphTime * 22);
            DropShadow glow = new DropShadow(10 + 8 * pulse, telegraph);
            glow.setSpread(0.35 + 0.25 * pulse);
            glow.setInput(e);
            e = glow;
        }
        if (flashLeft > 0) {
            ColorAdjust white = new ColorAdjust(0, -0.4, 0.75, 0);
            white.setInput(e);
            e = white;
        }
        sprite.setEffect(e);
    }

    public Node getNode() {
        return sprite;
    }
}
