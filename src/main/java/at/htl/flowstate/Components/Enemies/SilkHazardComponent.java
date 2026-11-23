package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Components.Player.PlayerStatsComponent;
import at.htl.flowstate.Game;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.StrokeLineCap;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;

/**
 * A curtain strand of the Void Matriarch's loose silk: first a faint flickering line (the telegraph), then it whips
 * down from the ceiling to the bottom of the arena; touching it while it hangs there hurts and chills.
 * (Her tripwires were replaced by her acid spit, 2026-11-23: see {@link AcidGlobComponent}.)
 */
public class SilkHazardComponent extends Component {
    private static final double DAMAGE = 18, CHILL = 1.2, LINGER = 1.1, SPEED = 3200;
    private static final double FADE = 0.3;
    private static final Color GLOW = Color.rgb(140, 77, 255, 0.45), CORE = Color.rgb(232, 218, 255);

    private final double length;           // from the top down to the bottom
    private double tell, life;
    private double tip;                    // how far down it has whipped
    private double time;
    private boolean live, hit;
    private Line glow, core;

    private SilkHazardComponent(double length, double tell) {
        this.length = length;
        this.tell = tell;
    }

    /** A strand at x whipping down from topY to bottomY. */
    public static Entity curtain(double x, double topY, double bottomY, double tell) {
        return entityBuilder().at(x, topY).with(new SilkHazardComponent(bottomY - topY, tell)).zIndex(12).buildAndAttach();
    }

    @Override
    public void onAdded() {
        glow = new Line(0, 0, 0, length);
        glow.setStroke(GLOW);
        glow.setStrokeWidth(9);
        glow.setStrokeLineCap(StrokeLineCap.ROUND);
        core = new Line(0, 0, 0, length);
        core.setStroke(CORE);
        core.setStrokeWidth(2.6);
        core.setStrokeLineCap(StrokeLineCap.ROUND);
        entity.getViewComponent().addChild(new Group(glow, core));
        show(0);
    }

    /** Only the first {@code len} px of the strand are there. */
    private void show(double len) {
        glow.setEndY(len);
        core.setEndY(len);
    }

    @Override
    public void onUpdate(double tpf) {
        time += tpf;
        if (tell > 0) {                                           // the telegraph: a faint flickering line
            tell -= tpf;
            show(length);
            entity.getViewComponent().setOpacity(0.18 + 0.14 * Math.sin(time * 34));
            core.setStrokeWidth(1);
            if (tell <= 0) start();
            return;
        }
        life -= tpf;
        if (tip < length) {
            tip = Math.min(length, tip + SPEED * tpf);
            show(tip);
        }
        if (life < FADE) entity.getViewComponent().setOpacity(Math.max(0, life / FADE));
        if (life <= 0) {
            entity.removeFromWorld();
            return;
        }
        if (live && !hit) checkPlayer();
    }

    private void start() {
        live = true;
        entity.getViewComponent().setOpacity(1);
        core.setStrokeWidth(2.6);
        life = LINGER + length / SPEED;
        show(0);
        Sfx.THREAD_TWANG.at(new Point2D(entity.getX(), entity.getY() + 300), 0.6, 1.4);
    }

    private void checkPlayer() {
        Entity player = Game.getPlayer();
        double x = entity.getX(), y = entity.getY();
        boolean touching = player.getX() < x + 6 && player.getRightX() > x - 6 && player.getY() < y + tip && player.getBottomY() > y;
        if (!touching) return;
        PlayerStatsComponent stats = player.getComponent(PlayerStatsComponent.class);
        if (stats.takeDamage(DAMAGE, entity, false)) stats.chill(CHILL);
        hit = true;
        Particles.burst(player.getCenter(), Particles.VOID, 10, -90, 120, 60, 220, 0.5, 4, 200);
    }
}
