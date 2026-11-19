package at.htl.flowstate.Components.Player.Attacks;

import at.htl.flowstate.Combat.Combat;
import at.htl.flowstate.Combat.Hit;
import at.htl.flowstate.Components.Enemies.EnemyProjectileComponent;
import at.htl.flowstate.Components.Player.PlayerMovementComponent;
import at.htl.flowstate.Game;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Visuals.Impact;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A melee swing attached to the player, animated in three phases:
 * <ol>
 *   <li>windup: the blade is pulled back a little further (anticipation, no hitbox)</li>
 *   <li>strike: a fast snap through the arc; the only phase that hits</li>
 *   <li>follow-through: the blade overshoots, settles and fades out</li>
 * </ol>
 * Angles are screen angles in degrees (0 = right, 90 = down). Each enemy is hit at most once.
 * The strike is sub-stepped so even very fast swings can't skip over an enemy between frames.
 */
public class MeleeSwingComponent extends Component {
    /** Called once, the first time the swing connects with an enemy or deflects a projectile. */
    public interface Listener {
        void onConnect(boolean enemy);
    }

    /** What the swing looks like and does. Built by MeleeAbility. */
    public static final class Params {
        final Hit hit;
        final double duration, from, to;
        double windup = 0.22;
        double strike = 0.28;
        boolean down;
        Color color = Color.WHITE;
        double pivotDx = 0, pivotDy = 0;
        Listener listener;
        boolean held;
        Sfx sound;
        double soundRate = 1;

        /** @param from start angle, @param to end angle; the blade sweeps from one to the other */
        public Params(Hit hit, double duration, double from, double to) {
            this.hit = hit;
            this.duration = duration;
            this.from = from;
            this.to = to;
        }

        /** Fraction of the duration spent winding up (lower for an already raised weapon). */
        public Params windup(double fraction) { this.windup = fraction; return this; }
        /** Fraction of the duration spent in the strike (the only phase that hits). */
        public Params strike(double fraction) { this.strike = fraction; return this; }
        /** Visual only: hold the pulled-back windup pose until removed (Zweihander charge). See {@link #setTremble}. */
        public Params held() { this.held = true; return this; }
        /** Down-slash: pivots lower and bounces the player off whatever it hits. */
        public Params down() { this.down = true; return this; }
        public Params color(Color c) { this.color = c; return this; }
        public Params pivotOffset(double dx, double dy) { this.pivotDx = dx; this.pivotDy = dy; return this; }
        public Params listener(Listener l) { this.listener = l; return this; }
        /** The whoosh, played when the strike starts (not at the windup). */
        public Params sound(Sfx s, double rate) { this.sound = s; this.soundRate = rate; return this; }
    }

    private static final double PULL_BACK = 28;      // extra degrees of windup
    private static final double OVERSHOOT = 14;      // degrees past the end during the follow-through
    private static final double SUB_STEP = 10;       // max degrees between collision checks
    private static final double TRAIL_DEGREES = 110;
    private static final double DEFLECT_HITSTOP = 0.06;
    private static final double DEFLECT_SHAKE = 3;
    private static final double HOLD_TREMBLE = 3;    // max degrees a held windup trembles

    private final Entity player;
    private final Params p;
    private final double sign;
    private final Set<Entity> alreadyHit = new HashSet<>();
    private boolean connected = false;
    private boolean strikeDone = false;
    private boolean whooshed = false;
    private double elapsed = 0;
    private double angle;
    private double strikeStart;
    private Arc trail, trailCore;
    private Entity trailEntity;
    private double tremble = 0;

    public MeleeSwingComponent(Params params) {
        this.player = Game.getPlayer();
        this.p = params;
        this.sign = Math.signum(params.to - params.from);
        this.strikeStart = params.from - sign * PULL_BACK;
    }

    @Override
    public void onAdded() {
        entity.setRotationOrigin(new Point2D(entity.getWidth() / 2, 0));
        angle = p.held ? strikeStart : p.from;
        place();

        double radius = entity.getHeight() * 0.78;
        trail = arc(radius, Math.max(8, entity.getHeight() * 0.32), p.color.deriveColor(0, 1, 1, 0.28));
        trailCore = arc(radius * 1.04, Math.max(3, entity.getHeight() * 0.06), Color.rgb(255, 255, 255, 0.85));
        trailEntity = FXGL.entityBuilder().view(new Group(trail, trailCore)).zIndex(12).buildAndAttach();
        updateTrail(0);
    }

    private static Arc arc(double radius, double width, Color color) {
        Arc a = new Arc(0, 0, radius, radius, 0, 0);
        a.setType(ArcType.OPEN);
        a.setFill(null);
        a.setStroke(color);
        a.setStrokeWidth(width);
        a.setStrokeLineCap(StrokeLineCap.ROUND);
        return a;
    }

    @Override
    public void onRemoved() {
        if (trailEntity != null) trailEntity.removeFromWorld();
    }

    /** 0..1: how hard a held weapon trembles (charge level). */
    public void setTremble(double amount) {
        tremble = amount;
    }

    @Override
    public void onUpdate(double tpf) {
        if (p.held) {
            angle = strikeStart + (Math.random() * 2 - 1) * HOLD_TREMBLE * tremble;
            place();
            trailEntity.setOpacity(0);
            return;
        }
        elapsed += tpf;
        double t = Math.min(elapsed / p.duration, 1.0);
        double strikeEnd = p.windup + p.strike;

        double previous = angle;
        if (t < p.windup) {
            // anticipation: ease out into the pulled-back pose
            double k = t / p.windup;
            angle = p.from - sign * PULL_BACK * (1 - (1 - k) * (1 - k));
        } else if (t < strikeEnd) {
            // the snap: starts at full speed and brakes into the overshoot
            double k = (t - p.windup) / p.strike;
            double eased = 1 - Math.pow(1 - k, 3);
            angle = strikeStart + (p.to + sign * OVERSHOOT - strikeStart) * eased;
        } else {
            // follow-through: settle back from the overshoot
            double k = (t - strikeEnd) / (1 - strikeEnd);
            angle = p.to + sign * OVERSHOOT * (1 - k * (2 - k));
        }

        if (!whooshed && t >= p.windup) {
            whooshed = true;
            if (p.sound != null) p.sound.play(1, p.soundRate);
        }

        // hitbox only while the blade moves forward through the strike
        if (!strikeDone && t >= p.windup) {
            sweepHits(previous, angle);
            if (t >= strikeEnd) strikeDone = true;
        }

        place();
        updateTrail(t);
        entity.setOpacity(t < 0.7 ? 1 : Math.max(0, 1 - (t - 0.7) / 0.3));

        if (t >= 1.0) entity.removeFromWorld();
    }

    /** Checks collisions at every few degrees between two angles of this frame. */
    private void sweepHits(double fromAngle, double toAngle) {
        int steps = Math.max(1, (int) Math.ceil(Math.abs(toAngle - fromAngle) / SUB_STEP));
        List<Entity> projectiles = new ArrayList<>(FXGL.getGameWorld().getEntitiesByComponent(EnemyProjectileComponent.class));
        for (int i = 1; i <= steps; i++) {
            double a = fromAngle + (toAngle - fromAngle) * i / steps;
            angle = a;
            place();
            for (Entity e : Combat.enemies()) {
                if (!alreadyHit.contains(e) && e.isColliding(entity)) {
                    alreadyHit.add(e);
                    hitEnemy(e, a);
                }
            }
            for (Entity proj : projectiles) {
                if (proj.isActive() && proj.isColliding(entity)) deflect(proj);
            }
        }
    }

    private void hitEnemy(Entity e, double atAngle) {
        Combat.Result r = Combat.hit(e, p.hit);
        if (!r.landed()) return;
        // streak across the target along the direction of the swing
        Particles.slashMark(e.getCenter(), atAngle + 90 * sign, e.getHeight() * 1.1, p.color);
        if (p.down) player.getComponent(PlayerMovementComponent.class).pogo();
        connect(true);
    }

    private void deflect(Entity proj) {
        Point2D at = proj.getCenter();
        proj.removeFromWorld();
        Sfx.DEFLECT.play();
        Particles.glint(at, Particles.SPARK, 10);
        Impact.hitStop(DEFLECT_HITSTOP);
        Impact.shake(DEFLECT_SHAKE);
        connect(false);
    }

    private void connect(boolean enemy) {
        if (connected) return;
        connected = true;
        if (p.listener != null) p.listener.onConnect(enemy);
    }

    private Point2D pivot() {
        double y = p.down ? player.getY() + player.getHeight() * 0.55 : player.getY() + player.getHeight() / 4;
        return new Point2D(player.getCenter().getX() + p.pivotDx, y + p.pivotDy);
    }

    private void place() {
        Point2D pivot = pivot();
        entity.setRotation(angle - 90); // the texture hangs straight down (90 degrees) at rotation 0
        entity.setPosition(pivot.getX() - entity.getWidth() / 2, pivot.getY());
    }

    /** Arc behind the blade from where the strike began. JavaFX arcs run counter-clockwise from +x. */
    private void updateTrail(double t) {
        Point2D pivot = pivot();
        trailEntity.setPosition(pivot.getX(), pivot.getY());
        double swept = (angle - strikeStart) * sign;
        double length = t < p.windup ? 0 : Math.max(0, Math.min(swept, TRAIL_DEGREES)) * sign;
        for (Arc a : new Arc[]{trail, trailCore}) {
            a.setStartAngle(-angle);
            a.setLength(length);
        }
        double strikeEnd = p.windup + p.strike;
        trailEntity.setOpacity(t < strikeEnd ? 1 : Math.max(0, 1 - (t - strikeEnd) / (1 - strikeEnd) * 1.6));
    }
}
