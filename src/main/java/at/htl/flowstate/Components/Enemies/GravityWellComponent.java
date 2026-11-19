package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Assets;
import at.htl.flowstate.Components.Player.PlayerMovementComponent;
import at.htl.flowstate.Components.Player.PlayerStatsComponent;
import at.htl.flowstate.Game;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Visuals.FrameSprite;
import at.htl.flowstate.Visuals.Frames;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.core.math.FXGLMath;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;

/**
 * Void Caller's gravity well: a knot of void hanging where the player stood. It drags the player toward its centre
 * (strongest close in, a bit slower than running so you can walk out; a dash breaks free) and the core bites.
 * Grows in, holds, collapses.
 */
public class GravityWellComponent extends Component {
    private static final double RADIUS = 320;
    private static final double PULL = 230;              // px/s at the core, falling off toward the rim
    private static final double CORE = 46;
    private static final double CORE_DAMAGE = 6;
    private static final double GROW = 0.35, COLLAPSE = 0.3;
    private static final int SCALE = 3;                  // GravityWell.png 64x64 shown at x3
    private static final Color[] MOTES = {Color.web("#e8f6ff"), Color.web("#9cc2ff"), Color.web("#6266e8")};

    private final Entity player = Game.getPlayer();
    private final double duration;
    private final Entity owner;
    private FrameSprite view;
    private double age, humTimer, moteTimer;

    private GravityWellComponent(double duration, Entity owner) {
        this.duration = duration;
        this.owner = owner;
    }

    /** A well centred on {@code at}, lasting {@code duration} s. */
    public static Entity spawn(Point2D at, double duration, Entity owner) {
        return entityBuilder()
                .at(at)
                .with(new GravityWellComponent(duration, owner))
                .zIndex(-2)
                .buildAndAttach();
    }

    @Override
    public void onAdded() {
        Frames frames = Assets.frames("GravityWell.png", 8, SCALE);
        view = new FrameSprite(frames).loop(frames, 12);
        view.setTranslateX(-frames.width() / 2.0);
        view.setTranslateY(-frames.height() / 2.0);
        entity.getViewComponent().addChild(view);
        Sfx.WELL_OPEN.at(entity.getPosition());
    }

    @Override
    public void onUpdate(double tpf) {
        age += tpf;
        double size = age < GROW ? age / GROW : age > duration - COLLAPSE ? Math.max(0, (duration - age) / COLLAPSE) : 1;
        view.setScaleX(size);
        view.setScaleY(size);
        view.setRotate(age * 40);
        if (age >= duration) {
            Particles.burst(entity.getPosition(), MOTES, 16, -90, 180, 80, 260, 0.5, 5, 0);
            entity.removeFromWorld();
            return;
        }
        Point2D c = entity.getPosition();
        // motes falling in from the rim
        if ((moteTimer -= tpf) <= 0) {
            moteTimer = 0.03;
            double a = FXGLMath.random(0, 360);
            double d = RADIUS * size * FXGLMath.random(0.55, 1.0);
            Point2D from = c.add(Math.cos(Math.toRadians(a)) * d, Math.sin(Math.toRadians(a)) * d * 0.8);
            Particles.burst(from, MOTES, 1, a + 180, 4, d * 1.6, d * 1.9, 0.5, 4, 0);
        }
        if ((humTimer -= tpf) <= 0) {
            humTimer = 0.9;
            Sfx.WELL_HUM.at(c, 0.6 * size, 1);
        }
        // the pull
        Point2D pc = player.getCenter();
        double dx = c.getX() - pc.getX();
        double dist = pc.distance(c);
        if (dist < RADIUS * size && Math.abs(dx) > 4) {
            double strength = PULL * size * Math.sqrt(1 - dist / RADIUS);
            PlayerMovementComponent move = player.getComponent(PlayerMovementComponent.class);
            move.pull(Math.signum(dx) * Math.min(Math.abs(dx), strength * tpf));
        }
        if (dist < CORE * size && player.getComponent(PlayerStatsComponent.class).takeDamage(CORE_DAMAGE, entity, false)) {
            EliteComponent.onHit(owner);
        }
    }
}
