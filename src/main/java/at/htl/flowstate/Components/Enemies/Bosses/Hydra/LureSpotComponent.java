package at.htl.flowstate.Components.Enemies.Bosses.Hydra;

import at.htl.flowstate.Components.Enemies.Bosses.BossArena;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.core.math.FXGLMath;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Ellipse;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;

/**
 * The spot of ember light the Lure head's bulb throws onto a pillar or a ledge while it sweeps. It glides toward the
 * player and locks; the next spit or bite lands where it locked. It is both the heads working together and the warning.
 */
public class LureSpotComponent extends Component {
    private final HydraArena lake = BossArena.active().hydra();
    private static final double W = 180, H = 36;
    private static final double LOCKED_LIFE = 1.6, FADE = 0.3;
    private static final Color CORE = Color.rgb(255, 230, 130, 1.0), RIM = Color.rgb(255, 120, 30, 0.0);

    private final Ellipse light = new Ellipse(W / 2, H / 2);
    private double x, age, lockedAge = -1;

    /** A spot at x (it finds the surface under it itself). */
    static Entity spawn(double x) {
        LureSpotComponent c = new LureSpotComponent();
        c.x = x;
        return entityBuilder().at(c.point()).with(c).zIndex(11).buildAndAttach();
    }

    @Override
    public void onAdded() {
        light.setFill(new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                new Stop(0, CORE), new Stop(0.5, Color.rgb(255, 170, 60, 0.7)), new Stop(1, RIM)));
        entity.getViewComponent().addChild(new Group(light));
        entity.setOpacity(0);
    }

    /** Glides toward tx at speed (px/s); a locked spot stays. */
    void follow(double tx, double speed, double tpf) {
        if (lockedAge >= 0) return;
        double d = tx - x;
        x += Math.signum(d) * Math.min(Math.abs(d), speed * tpf);
    }

    /** Stops where it is. @return the point it marks (on the surface) */
    Point2D lock() {
        if (lockedAge < 0) lockedAge = 0;
        return point();
    }

    Point2D point() {
        return new Point2D(x, lake.floorAt(x, -1e9));
    }

    /** The strike landed: it fades out. */
    void done() {
        lockedAge = Math.max(lockedAge, LOCKED_LIFE - FADE);
    }

    @Override
    public void onUpdate(double tpf) {
        age += tpf;
        Point2D p = point();
        entity.setPosition(p.getX(), p.getY());
        light.setCenterX(0);
        light.setCenterY(-2);
        double pulse = 0.85 + 0.15 * Math.sin(age * (lockedAge >= 0 ? 26 : 9));
        double scale = lockedAge >= 0 ? 0.8 + 0.15 * Math.sin(age * 26) : 1;
        light.setRadiusX(W / 2 * scale);
        light.setRadiusY(H / 2 * scale);
        double fadeIn = Math.min(1, age / 0.25);
        double fadeOut = lockedAge >= 0 ? Math.max(0, Math.min(1, (LOCKED_LIFE - lockedAge) / FADE)) : 1;
        entity.setOpacity(fadeIn * fadeOut * pulse);
        if (FXGLMath.randomDouble() < tpf * 10) {
            Particles.of(Particles.EMBER).count(1).angle(-90, 20).speed(20, 60)
                    .life(0.5).size(3).gravity(-40).at(p.add((FXGLMath.randomDouble() - 0.5) * W * 0.6, -4));
        }
        if (lockedAge >= 0) {
            lockedAge += tpf;
            if (lockedAge >= LOCKED_LIFE) entity.removeFromWorld();
        }
    }
}
