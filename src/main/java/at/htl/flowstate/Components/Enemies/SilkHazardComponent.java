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
 * The Void Matriarch's loose silk. Two kinds, both shown first as a faint flickering line (the telegraph):
 * <ul>
 *   <li>a <b>tripwire</b>: a low horizontal strand over a ledge or a bridge; walking into it snaps it, stings and
 *       chills (slows) the player. Jump over it.</li>
 *   <li>a <b>curtain strand</b>: a strand that whips down from the ceiling to the bottom of the arena; touching it
 *       while it hangs there hurts and chills.</li>
 * </ul>
 */
public class SilkHazardComponent extends Component {
    private static final double TRIP_DAMAGE = 4, TRIP_CHILL = 1.6, TRIP_LIFE = 10;
    private static final double CURTAIN_DAMAGE = 18, CURTAIN_CHILL = 1.2, CURTAIN_LINGER = 1.1, CURTAIN_SPEED = 3200;
    private static final double FADE = 0.3;
    private static final Color GLOW = Color.rgb(140, 77, 255, 0.45), CORE = Color.rgb(232, 218, 255);

    private final boolean curtain;
    private final double length;           // tripwire: its width; curtain: from the top down to the bottom
    private double tell, life;
    private double tip;                    // curtain: how far down it has whipped
    private double time;
    private boolean live, hit;
    private Line glow, core;

    private SilkHazardComponent(boolean curtain, double length, double tell) {
        this.curtain = curtain;
        this.length = length;
        this.tell = tell;
    }

    /** A low strand from x1 to x2, {@code height} px above the surface at y. */
    public static Entity tripwire(double x1, double x2, double surfaceY, double height, double tell) {
        return entityBuilder().at(x1, surfaceY - height).with(new SilkHazardComponent(false, x2 - x1, tell)).zIndex(12).buildAndAttach();
    }

    /** A strand at x whipping down from topY to bottomY. */
    public static Entity curtain(double x, double topY, double bottomY, double tell) {
        return entityBuilder().at(x, topY).with(new SilkHazardComponent(true, bottomY - topY, tell)).zIndex(12).buildAndAttach();
    }

    @Override
    public void onAdded() {
        double x2 = curtain ? 0 : length, y2 = curtain ? length : 0;
        glow = new Line(0, 0, x2, y2);
        glow.setStroke(GLOW);
        glow.setStrokeWidth(curtain ? 9 : 6);
        glow.setStrokeLineCap(StrokeLineCap.ROUND);
        core = new Line(0, 0, x2, y2);
        core.setStroke(CORE);
        core.setStrokeWidth(curtain ? 2.6 : 2);
        core.setStrokeLineCap(StrokeLineCap.ROUND);
        entity.getViewComponent().addChild(new Group(glow, core));
        show(0);
    }

    /** Only the first {@code len} px of the strand are there. */
    private void show(double len) {
        if (!curtain) return;
        glow.setEndY(len);
        core.setEndY(len);
    }

    @Override
    public void onUpdate(double tpf) {
        time += tpf;
        if (tell > 0) {                                           // the telegraph: a faint flickering line
            tell -= tpf;
            if (curtain) show(length);
            entity.getViewComponent().setOpacity(0.18 + 0.14 * Math.sin(time * 34));
            core.setStrokeWidth(1);
            if (tell <= 0) start();
            return;
        }
        life -= tpf;
        if (curtain && tip < length) {
            tip = Math.min(length, tip + CURTAIN_SPEED * tpf);
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
        core.setStrokeWidth(curtain ? 2.6 : 2);
        if (curtain) {
            life = CURTAIN_LINGER + length / CURTAIN_SPEED;
            show(0);
            Sfx.THREAD_TWANG.at(new Point2D(entity.getX(), entity.getY() + 300), 0.6, 1.4);
        } else {
            life = TRIP_LIFE;
            Sfx.THREAD_TWANG.at(entity.getPosition().add(length / 2, 0), 0.5, 1.6);
        }
    }

    private void checkPlayer() {
        Entity player = Game.getPlayer();
        double x = entity.getX(), y = entity.getY();
        boolean touching = curtain
                ? player.getX() < x + 6 && player.getRightX() > x - 6 && player.getY() < y + tip && player.getBottomY() > y
                : player.getX() < x + length && player.getRightX() > x && player.getY() < y + 3 && player.getBottomY() > y - 3;
        if (!touching) return;
        PlayerStatsComponent stats = player.getComponent(PlayerStatsComponent.class);
        if (curtain) {
            if (stats.takeDamage(CURTAIN_DAMAGE, entity, false)) stats.chill(CURTAIN_CHILL);
            hit = true;
            Particles.burst(player.getCenter(), Particles.VOID, 10, -90, 120, 60, 220, 0.5, 4, 200);
        } else {
            stats.takeDamage(TRIP_DAMAGE, entity, false);
            stats.chill(TRIP_CHILL);
            Sfx.THREAD_SNAP.at(player.getCenter(), 0.7, 1.3);
            Particles.burst(new Point2D(player.getCenter().getX(), y), Particles.VOID, 12, -90, 160, 60, 200, 0.5, 4, 300);
            entity.removeFromWorld();
        }
    }
}
