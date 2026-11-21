package at.htl.flowstate.Visuals;

import com.almasb.fxgl.core.math.FXGLMath;
import com.almasb.fxgl.entity.Entity;

import java.util.ArrayList;
import java.util.List;

import static com.almasb.fxgl.dsl.FXGL.getGameScene;

/**
 * Game feel: freeze frames (hit stop), camera shake and the trembling of whatever was hit.
 * Hit stop puts the game scene into single-step mode, which halts the entity world and physics while
 * input, timers and {@code Game.onUpdate} keep running. {@link #update} must be called from
 * {@code Game.onUpdate} every frame with the real frame time, or the game would stay frozen.
 */
public final class Impact {
    private static final double MAX_FREEZE = 0.22;
    private static final double MAX_SHAKE = 14;
    private static final double MAX_QUAKE = 26;     // bosses' heaviest blows may shake past the usual cap
    private static final double SHAKE_DECAY = 18;   // per second, exponential
    private static final double JITTER = 4;

    private static double freezeLeft = 0;
    private static boolean frozen = false;
    private static boolean freezeStartedThisFrame = false;
    private static double shakePower = 0;
    private static double shakeX = 0, shakeY = 0;
    private static final List<Entity> trembling = new ArrayList<>();

    private Impact() {}

    /** Freezes the game for {@code seconds}. Overlapping requests take the longest, they don't add up. */
    public static void hitStop(double seconds) {
        if (seconds <= 0) return;
        freezeLeft = Math.min(MAX_FREEZE, Math.max(freezeLeft, seconds));
        if (!frozen) {
            frozen = true;
            freezeStartedThisFrame = true;
            getGameScene().setSingleStep(true);
        }
    }

    public static void shake(double power) {
        shakePower = Math.min(MAX_SHAKE, Math.max(shakePower, power));
    }

    /** A boss-sized shake: like {@link #shake}, but allowed well past the usual cap. */
    public static void quake(double power) {
        shakePower = Math.min(MAX_QUAKE, Math.max(shakePower, power));
    }

    /** Makes the entity's sprite tremble while the game is frozen. */
    public static void tremble(Entity e) {
        if (e != null && !trembling.contains(e)) trembling.add(e);
    }

    public static boolean isFrozen() {
        return frozen;
    }

    public static double getShakeX() { return shakeX; }
    public static double getShakeY() { return shakeY; }

    /** Clears all state (new game). */
    public static void reset() {
        freezeLeft = 0;
        shakePower = 0;
        shakeX = shakeY = 0;
        stopTrembling();
        if (frozen) getGameScene().setSingleStep(false);
        frozen = false;
    }

    public static void update(double tpf) {
        if (frozen) {
            // the frame that started the freeze was already simulated: don't count it
            if (freezeStartedThisFrame) freezeStartedThisFrame = false;
            else freezeLeft -= tpf;

            for (Entity e : trembling) {
                e.getViewComponent().getParent().setTranslateX(FXGLMath.random(-JITTER, JITTER));
                e.getViewComponent().getParent().setTranslateY(FXGLMath.random(-JITTER, JITTER) * 0.5);
            }
            if (freezeLeft <= 0) {
                frozen = false;
                freezeLeft = 0;
                stopTrembling();
                getGameScene().setSingleStep(false);
            }
        } else if (!trembling.isEmpty()) {
            stopTrembling();
        }

        if (shakePower > 0.2) {
            double a = FXGLMath.random(0, Math.PI * 2);
            shakeX = Math.cos(a) * shakePower;
            shakeY = Math.sin(a) * shakePower;
            shakePower *= Math.exp(-SHAKE_DECAY * tpf);
        } else {
            shakePower = 0;
            shakeX = shakeY = 0;
        }
    }

    private static void stopTrembling() {
        for (Entity e : trembling) {
            e.getViewComponent().getParent().setTranslateX(0);
            e.getViewComponent().getParent().setTranslateY(0);
        }
        trembling.clear();
    }
}
