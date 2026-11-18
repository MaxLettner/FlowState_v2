package at.htl.flowstate.Components.Player.Attacks;

import at.htl.flowstate.Combat.Combat;
import at.htl.flowstate.Combat.Hit;
import at.htl.flowstate.Game;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;

import java.util.HashSet;
import java.util.Set;

/**
 * An aimed punch: short pull back, a snapping jab, a brief hold at full extension, then the retract.
 * Only the jab and the hold can hit. Collision is sub-stepped so the fast jab can't skip over enemies.
 */
public class PunchComponent extends Component {
    private static final double WINDUP = 0.18, EXTEND = 0.3, HOLD = 0.15;
    private static final double PULL_BACK = -0.18;   // fraction of the reach the fist pulls back
    private static final double BASE_OFFSET = 18;   // 10 px from the body + half a fist, as before
    private static final double SUBSTEP = 8;
    private static final double SHOULDER = 0.4;

    private final Entity player;
    private final Hit hit;
    private final double reach;
    private final double duration;
    private final Point2D dir;
    private final int side;
    private final double pivotDy;
    private final MeleeSwingComponent.Listener listener;
    private final Set<Entity> alreadyHit = new HashSet<>();
    private double elapsed = 0;

    public PunchComponent(Hit hit, double reach, double duration, Point2D dir, int side, double pivotDy,
                          MeleeSwingComponent.Listener listener) {
        this.player = Game.getPlayer();
        this.hit = hit;
        this.reach = reach;
        this.duration = duration;
        this.dir = dir;
        this.side = side;
        this.pivotDy = pivotDy;
        this.listener = listener;
    }

    @Override
    public void onAdded() {
        place(extensionAt(0));
    }

    @Override
    public void onUpdate(double tpf) {
        double prevT = Math.min(elapsed / duration, 1.0);
        elapsed += tpf;
        double t = Math.min(elapsed / duration, 1.0);

        double from = Math.max(prevT, WINDUP), to = Math.min(t, WINDUP + EXTEND + HOLD);
        if (to > from) {
            double a = extensionAt(from), b = extensionAt(to);
            int steps = Math.max(1, (int) Math.ceil(Math.abs(b - a) * reach / SUBSTEP));
            for (int i = 1; i <= steps; i++) {
                place(a + (b - a) * i / steps);
                checkHits();
            }
        }

        place(extensionAt(t));
        if (t >= 1.0) entity.removeFromWorld();
    }

    private double extensionAt(double t) {
        if (t < WINDUP) return PULL_BACK * (1 - Math.pow(1 - t / WINDUP, 2));
        if (t < WINDUP + EXTEND) {
            double u = (t - WINDUP) / EXTEND;
            return PULL_BACK + (1 - PULL_BACK) * (1 - Math.pow(1 - u, 3));
        }
        if (t < WINDUP + EXTEND + HOLD) return 1;
        double u = (t - WINDUP - EXTEND - HOLD) / (1 - WINDUP - EXTEND - HOLD);
        return 1 - u * u;
    }

    private void checkHits() {
        for (Entity e : Combat.enemies()) {
            if (alreadyHit.contains(e) || !e.isColliding(entity)) continue;
            alreadyHit.add(e);
            Hit h = hit.copy();
            if (Math.abs(dir.getX()) > 0.35) h.knockback(h.getKnockback(), side);
            else h.knockbackFrom(h.getKnockback(), player.getCenter().getX());
            Point2D at = entity.getCenter();
            Combat.Result r = Combat.hit(e, h);
            if (!r.landed()) continue;
            double angle = Math.toDegrees(Math.atan2(dir.getY(), dir.getX()));
            Particles.slashMark(at, angle + 90, 34, Color.WHITE);
            Particles.slashMark(at, angle, 26, Color.WHITE);
            if (listener != null) listener.onConnect(true);
        }
    }

    private void place(double extension) {
        double cx = player.getCenter().getX() + dir.getX() * (BASE_OFFSET + extension * reach);
        double cy = player.getY() + player.getHeight() * SHOULDER + pivotDy + dir.getY() * (BASE_OFFSET + extension * reach);
        entity.setPosition(cx - entity.getWidth() / 2, cy - entity.getHeight() / 2);
    }
}
