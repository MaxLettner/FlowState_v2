package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Components.Player.PlayerStatsComponent;
import at.htl.flowstate.Game;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Visuals.FrameSprite;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import com.almasb.fxgl.physics.BoundingShape;
import com.almasb.fxgl.physics.HitBox;
import javafx.geometry.Point2D;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;
import static com.almasb.fxgl.dsl.FXGL.getGameWorld;

/**
 * A ghost of the player's past: replays a recorded stretch of their movement (position and animation frame), void
 * tinted and see-through. It fades in where it starts without hurting, then hurts on contact until it has caught up
 * with the present and fades out. Dodge your past self.
 */
public class HollowEcho extends Component {
    public static final int MAX = 3;
    private static final double FADE_IN = 0.8, FADE_OUT = 0.4, DAMAGE = 12;
    private static final double OFFSET_X = 20 - 64, OFFSET_Y = 80 - 126;   // like SpriteComponent: 128 px frame on the 40x80 body
    private static final double FPS = 60;

    private final HollowRecorder.Clip clip;
    private final FrameSprite sprite;
    private double age;

    private HollowEcho(HollowRecorder.Clip clip, FrameSprite sprite) {
        this.clip = clip;
        this.sprite = sprite;
    }

    public static int alive() {
        return getGameWorld().getEntitiesByComponent(HollowEcho.class).size();
    }

    /** An echo of the last {@code samples} frames of the player, if that is fair. */
    static boolean spawnFrom(HollowRecorder recorder, int samples) {
        return spawn(recorder.clip(samples));
    }

    /** False when the echo would start right on top of the player (unfair): nothing is spawned. */
    static boolean spawn(HollowRecorder.Clip clip) {
        if (clip.length() < 40) return false;
        Entity player = Game.getPlayer();
        double dx = Math.abs(clip.x()[0] - player.getX()), dy = Math.abs(clip.y()[0] - player.getY());
        if (dx < 260 && dy < 160) return false;
        FrameSprite sprite = new FrameSprite(clip.frames()[0]);
        sprite.setTranslateX(OFFSET_X);
        sprite.setTranslateY(OFFSET_Y);
        ColorAdjust tint = new ColorAdjust(-0.667, 0.1, 0.15, 0);        // ember orange to violet
        tint.setInput(new DropShadow(14, Color.web("#a86cff")));
        sprite.setEffect(tint);
        entityBuilder()
                .at(clip.x()[0], clip.y()[0])
                .bbox(new HitBox(BoundingShape.box(40, 80)))
                .view(sprite)
                .with(new HollowEcho(clip, sprite))
                .zIndex(15)
                .buildAndAttach();
        Sfx.HOLLOW_ECHO.at(new Point2D(clip.x()[0] + 20, clip.y()[0] + 40));
        return true;
    }

    @Override
    public void onUpdate(double tpf) {
        age += tpf;
        int i = Math.min(clip.length() - 1, (int) (age * FPS));
        entity.setPosition(clip.x()[i], clip.y()[i]);
        sprite.show(clip.frames()[i], clip.frame()[i]);
        sprite.setScaleX(clip.facing()[i]);
        double left = clip.length() / FPS - age;
        if (left <= 0) {
            Particles.burst(entity.getCenter(), Particles.VOID, 14, -90, 180, 40, 200, 0.5, 5, 0);
            entity.removeFromWorld();
            return;
        }
        // a ghost: faint while it appears (a harmless warning), then a little stronger
        entity.setOpacity(age < FADE_IN ? 0.15 + 0.2 * age / FADE_IN : Math.min(0.5, 0.5 * left / FADE_OUT));
        if (age >= FADE_IN && entity.isColliding(Game.getPlayer())) {
            Entity player = Game.getPlayer();
            if (player.getComponent(PlayerStatsComponent.class).takeDamage(DAMAGE, entity, false)) {
                Particles.burst(player.getCenter(), Particles.VOID, 10, -90, 180, 80, 260, 0.4, 5, 200);
            }
        }
    }

    /** The Hollow died: the past goes with it. */
    public static void clear() {
        getGameWorld().getEntitiesByComponent(HollowEcho.class).forEach(Entity::removeFromWorld);
    }
}
