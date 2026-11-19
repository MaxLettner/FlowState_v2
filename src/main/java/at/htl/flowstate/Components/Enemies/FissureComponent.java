package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Components.Player.PlayerStatsComponent;
import at.htl.flowstate.Game;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Visuals.Impact;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.core.math.FXGLMath;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import com.almasb.fxgl.physics.BoundingShape;
import com.almasb.fxgl.physics.HitBox;
import javafx.geometry.Point2D;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;

/**
 * Pit Lord fissure: the ground cracks open and glows for a moment, then a pillar of hellfire bursts out of it.
 * Far too tall to jump over: get off the crack while it glows.
 */
public class FissureComponent extends Component {
    private static final double WARN = 0.75, ERUPT = 0.5;
    private static final double W = 90, H = 320;
    private static final double GROW = 0.08;          // the pillar shoots up this fast

    private final double damage;
    private final Entity owner;
    private final Entity player = Game.getPlayer();
    private Rectangle crack, pillar, core;
    private double age;
    private boolean erupted, hit;

    private FissureComponent(double damage, Entity owner) {
        this.damage = damage;
        this.owner = owner;
    }

    /** A fissure centred on x, on the ground at groundY. */
    public static void spawn(double x, double groundY, double damage, Entity owner) {
        FissureComponent f = new FissureComponent(damage, owner);
        f.crack = new Rectangle(W, 6, Color.web("#ff7a1f"));
        f.crack.setTranslateY(H - 4);
        f.crack.setEffect(new DropShadow(20, Color.rgb(255, 110, 30, 0.9)));
        f.pillar = new Rectangle(W * 0.7, 0, new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(255, 194, 61, 0)),
                new Stop(0.3, Color.rgb(255, 194, 61, 0.85)),
                new Stop(1, Color.rgb(255, 122, 31, 0.95))));
        f.pillar.setTranslateX(W * 0.15);
        f.pillar.setTranslateY(H);
        f.pillar.setEffect(new DropShadow(30, Color.rgb(255, 90, 20, 0.9)));
        f.core = new Rectangle(W * 0.3, 0, new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(255, 246, 192, 0)),
                new Stop(0.4, Color.rgb(255, 246, 192, 0.9)),
                new Stop(1, Color.rgb(255, 255, 255, 1))));
        f.core.setTranslateY(H);
        entityBuilder()
                .at(x - W / 2, groundY - H)
                .view(f.pillar)
                .view(f.core)
                .view(f.crack)
                .bbox(new HitBox(BoundingShape.box(W, H)))
                .with(f)
                .zIndex(12)
                .buildAndAttach();
        Sfx.CRACKLE.at(new Point2D(x, groundY), 1, 0.6);
    }

    @Override
    public void onUpdate(double tpf) {
        age += tpf;
        double groundY = entity.getBottomY();
        double cx = entity.getCenter().getX();
        if (age < WARN) {
            // the crack widens and pulses brighter as it is about to blow
            double k = age / WARN;
            crack.setWidth(W * (0.4 + 0.6 * k));
            crack.setTranslateX(W * (0.3 - 0.3 * k));
            crack.setOpacity(0.55 + 0.45 * Math.abs(Math.sin(age * (10 + 20 * k))));
            if (Math.random() < tpf * 30) {
                Particles.burst(new Point2D(cx + (Math.random() - 0.5) * W * k, groundY - 2),
                        Particles.EMBER, 1, -90, 20, 60, 160 * k + 40, 0.4, 4, 200);
            }
            return;
        }
        if (!erupted) {
            erupted = true;
            Sfx.HELLFIRE_LAUNCH.at(entity.getCenter(), 1, 0.7);
            Impact.shake(6);
            Particles.burst(new Point2D(cx, groundY - 4), Particles.EMBER, 30, -90, 18, 400, 900, 0.7, 7, 300);
            Particles.burst(new Point2D(cx, groundY - 4), Particles.DUST, 14, -90, 70, 100, 300, 0.5, 7, 700);
        }
        double t = age - WARN;
        double h = H * Math.min(1, t / GROW);
        // flickering, ragged flame: the widths jitter every frame
        double pw = W * FXGLMath.random(0.6, 0.8), cw = W * FXGLMath.random(0.22, 0.36);
        pillar.setWidth(pw);
        pillar.setTranslateX((W - pw) / 2 + FXGLMath.random(-3, 3));
        pillar.setHeight(h);
        pillar.setTranslateY(H - h);
        core.setWidth(cw);
        core.setTranslateX((W - cw) / 2);
        core.setHeight(h * FXGLMath.random(0.75, 0.9));
        core.setTranslateY(H - core.getHeight());
        if (Math.random() < tpf * 40) {
            Particles.burst(new Point2D(cx + (Math.random() - 0.5) * W * 0.6, groundY - Math.random() * h),
                    Particles.EMBER, 1, -90, 15, 150, 350, 0.4, 5, 0);
        }
        double fade = 1 - Math.max(0, (t - ERUPT * 0.6) / (ERUPT * 0.4));
        pillar.setOpacity(fade);
        core.setOpacity(fade);
        crack.setOpacity(fade);
        if (!hit && t < ERUPT * 0.7 && entity.isColliding(player)) {
            hit = player.getComponent(PlayerStatsComponent.class).takeDamage(damage, entity, false);
            if (hit) EliteComponent.onHit(owner);
        }
        if (t >= ERUPT) entity.removeFromWorld();
    }
}
