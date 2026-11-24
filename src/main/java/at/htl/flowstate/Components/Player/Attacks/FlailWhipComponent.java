package at.htl.flowstate.Components.Player.Attacks;

import at.htl.flowstate.Combat.Combat;
import at.htl.flowstate.Components.Enemies.EnemyProjectileComponent;
import at.htl.flowstate.Game;
import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import com.almasb.fxgl.entity.components.BoundingBoxComponent;
import javafx.geometry.Point2D;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Morningstar's tap: the chain is lashed like a whip. The grip swings through the same windup / strike /
 * follow-through as a blade (see MeleeSwingComponent), and every link follows the grip's path a little later
 * than the one before, so the chain curls behind the hand and the ball cracks through last.
 * The whole chain hits while it moves through its strike; each enemy is hit at most once.
 */
public class FlailWhipComponent extends Component {
    private static final double PULL_BACK = 28;       // extra degrees of windup
    private static final double OVERSHOOT = 14;       // degrees past the end during the follow-through
    private static final double LINK_LENGTH = 10;
    private static final double TIP_LAG = 0.12;       // seconds the ball trails the grip
    private static final double SUB_STEP = 0.005;     // seconds between collision checks
    private static final double LINK_RADIUS = 8;
    private static final double HEAD_RADIUS = 16;
    private static final double FADE = 0.12;

    private final Entity player = Game.getPlayer();
    private final MeleeSwingComponent.Params p;
    private final double sign;
    private final double strikeStart;
    private final int linkCount;
    private final Set<Entity> alreadyHit = new HashSet<>();
    private final FlailView view;
    private boolean connected = false;
    private boolean whooshed = false;
    private double elapsed = 0;

    private FlailWhipComponent(MeleeSwingComponent.Params params, FlailView view, int linkCount) {
        this.p = params;
        this.view = view;
        this.linkCount = linkCount;
        this.sign = Math.signum(params.to - params.from);
        this.strikeStart = params.from - sign * PULL_BACK;
    }

    /** Lashes the flail; {@code reach} is the distance from the hand to the ball's centre. */
    public static Entity spawn(MeleeSwingComponent.Params params, double reach) {
        int links = FlailView.linkCount(reach, LINK_LENGTH);
        FlailView view = new FlailView(links);
        return FXGL.entityBuilder()
                .view(view)
                .with(new FlailWhipComponent(params, view, links))
                .zIndex(13)
                .buildAndAttach();
    }

    @Override
    public void onAdded() {
        view.draw(hand(), joints(0));
    }

    @Override
    public void onUpdate(double tpf) {
        double before = elapsed;
        elapsed += tpf;

        if (!whooshed && elapsed >= p.windup * p.duration) {
            whooshed = true;
            if (p.sound != null) p.sound.play(1, p.soundRate);
        }

        // sub-stepped so the fast end of the chain can't skip over an enemy between frames
        int steps = Math.max(1, (int) Math.ceil((elapsed - before) / SUB_STEP));
        List<Entity> projectiles = new ArrayList<>(FXGL.getGameWorld().getEntitiesByComponent(EnemyProjectileComponent.class));
        for (int i = 1; i <= steps; i++) sweep(before + (elapsed - before) * i / steps, projectiles);

        view.draw(hand(), joints(elapsed));
        double end = p.duration + TIP_LAG;
        entity.setOpacity(Math.max(0, Math.min(1, (end - elapsed) / FADE)));
        if (elapsed >= end) entity.removeFromWorld();
    }

    //-----Motion-----

    /** The grip's angle at time t: pull back, snap through the arc, overshoot and settle. */
    private double gripAngle(double time) {
        double t = Math.max(0, Math.min(time / p.duration, 1.0));
        double strikeEnd = p.windup + p.strike;
        if (t < p.windup) {
            double k = t / p.windup;
            return p.from - sign * PULL_BACK * (1 - (1 - k) * (1 - k));
        }
        if (t < strikeEnd) {
            double k = (t - p.windup) / p.strike;
            return strikeStart + (p.to + sign * OVERSHOOT - strikeStart) * (1 - Math.pow(1 - k, 3));
        }
        double k = (t - strikeEnd) / (1 - strikeEnd);
        return p.to + sign * OVERSHOOT * (1 - k * (2 - k));
    }

    /** Time a joint lags behind the grip: 0 at the grip's tip, TIP_LAG at the ball. */
    private double lag(int joint) {
        return TIP_LAG * joint / linkCount;
    }

    private Point2D[] joints(double time) {
        Point2D hand = hand();
        Point2D[] joints = new Point2D[linkCount + 1];
        joints[0] = hand.add(direction(gripAngle(time)).multiply(FlailView.handleLength()));
        for (int i = 1; i <= linkCount; i++) {
            joints[i] = joints[i - 1].add(direction(gripAngle(time - lag(i))).multiply(LINK_LENGTH));
        }
        return joints;
    }

    private static Point2D direction(double degrees) {
        double r = Math.toRadians(degrees);
        return new Point2D(Math.cos(r), Math.sin(r));
    }

    private Point2D hand() {
        double y = p.down ? player.getY() + player.getHeight() * 0.55 : player.getY() + player.getHeight() / 4;
        return new Point2D(player.getCenter().getX() + p.pivotDx, y + p.pivotDy);
    }

    //-----Hits-----

    /** Checks every joint that is in its strike at this time against enemies and enemy projectiles. */
    private void sweep(double time, List<Entity> projectiles) {
        double strikeFrom = p.windup * p.duration;
        double strikeTo = (p.windup + p.strike) * p.duration;
        Point2D[] joints = joints(time);
        for (int i = 0; i < joints.length; i++) {
            double local = time - lag(i);
            if (local < strikeFrom || local > strikeTo) continue;
            double radius = i == joints.length - 1 ? HEAD_RADIUS : LINK_RADIUS;
            double markAngle = gripAngle(local) + 90 * sign;
            for (Entity e : Combat.enemies()) {
                if (!alreadyHit.contains(e) && touches(e, joints[i], radius)) {
                    alreadyHit.add(e);
                    if (MeleeSwingComponent.strike(p, e, markAngle)) connect(true);
                }
            }
            for (Entity proj : projectiles) {
                if (proj.isActive() && touches(proj, joints[i], radius)) {
                    MeleeSwingComponent.deflectProjectile(proj);
                    connect(false);
                }
            }
        }
    }

    private static boolean touches(Entity e, Point2D at, double radius) {
        BoundingBoxComponent b = e.getBoundingBoxComponent();
        double x = Math.max(b.getMinXWorld(), Math.min(at.getX(), b.getMaxXWorld()));
        double y = Math.max(b.getMinYWorld(), Math.min(at.getY(), b.getMaxYWorld()));
        return at.distance(x, y) <= radius;
    }

    private void connect(boolean enemy) {
        if (connected) return;
        connected = true;
        if (p.listener != null) p.listener.onConnect(enemy);
    }
}
