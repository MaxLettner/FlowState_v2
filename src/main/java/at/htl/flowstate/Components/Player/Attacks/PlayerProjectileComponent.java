package at.htl.flowstate.Components.Player.Attacks;

import at.htl.flowstate.Combat.Combat;
import at.htl.flowstate.Combat.Hit;
import at.htl.flowstate.Sfx;
import com.almasb.fxgl.app.scene.Viewport;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import javafx.geometry.Point2D;

import java.util.HashSet;
import java.util.Set;

import static com.almasb.fxgl.dsl.FXGL.getGameScene;

/**
 * Every player projectile: arrows, bolts, spells, shards.
 * Pierce N = passes through N enemies (hits N+1); -1 = infinite until terrain.
 */
public class PlayerProjectileComponent extends Component {
    /** Builds the hit for the n-th enemy struck (0-based). May return null for "no damage". */
    public interface HitFactory {
        Hit create(Entity enemy, int index);
    }

    /** Called when the projectile stops on an enemy (pierce used up) or on terrain. */
    public interface StopListener {
        void onStop(Point2D position, Point2D velocity, Entity lastEnemy, boolean terrain);
    }

    private Point2D velocity;
    private int pierce;
    private final HitFactory hitFactory;
    private double gravity = 0;
    private double maxDistance = 0;
    private double terrainGrace = 0;
    private StopListener stopListener;
    private Sfx terrainSound;
    private double terrainSoundRate = 1;
    private Entity homingTarget;
    private double homingTurnRad;
    private double homingAfter;

    private double traveled = 0;
    private int hits = 0;
    private Entity lastEnemy;
    private boolean stopped = false;
    private final Set<Entity> alreadyHit = new HashSet<>();
    private double light;

    public PlayerProjectileComponent(Point2D velocity, int pierce, HitFactory hitFactory) {
        this.velocity = velocity;
        this.pierce = pierce;
        this.hitFactory = hitFactory;
    }

    public PlayerProjectileComponent gravity(double g) { this.gravity = g; return this; }
    /** It glows: lights up this radius around it in the dark (the Void Matriarch's last phase). */
    public PlayerProjectileComponent light(double radius) { this.light = radius; return this; }
    public double getLight() { return light; }
    public PlayerProjectileComponent maxDistance(double d) { this.maxDistance = d; return this; }
    public PlayerProjectileComponent onStop(StopListener l) { this.stopListener = l; return this; }
    /** Played where the projectile hits terrain (arrows thunk into the ground). */
    public PlayerProjectileComponent terrainSound(Sfx s, double rate) { this.terrainSound = s; this.terrainSoundRate = rate; return this; }
    /** Ignore terrain for the first given distance (projectiles spawned on the ground). */
    public PlayerProjectileComponent terrainGrace(double distance) { this.terrainGrace = distance; return this; }
    /** The projectile passes through this enemy without hitting it. */
    public PlayerProjectileComponent ignore(Entity enemy) { if (enemy != null) alreadyHit.add(enemy); return this; }

    public PlayerProjectileComponent homing(Entity target, double turnDegreesPerSecond, double afterDistance) {
        this.homingTarget = target;
        this.homingTurnRad = Math.toRadians(turnDegreesPerSecond);
        this.homingAfter = afterDistance;
        return this;
    }

    @Override
    public void onAdded() {
        rotate();
    }

    @Override
    public void onUpdate(double tpf) {
        if (stopped) return;

        steer(tpf);
        velocity = velocity.add(0, gravity * tpf);
        Point2D step = velocity.multiply(tpf);
        entity.translate(step);
        traveled += step.magnitude();
        rotate();

        for (Entity e : Combat.enemies()) {
            if (alreadyHit.contains(e) || !entity.isColliding(e)) continue;
            alreadyHit.add(e);
            lastEnemy = e;
            Hit hit = hitFactory.create(e, hits++);
            if (hit != null) Combat.hit(e, hit);
            if (pierce == 0) {
                stop(false);
                return;
            }
            if (pierce > 0) pierce--;
        }

        if (traveled > terrainGrace && Combat.touchesTerrain(entity)) {
            stop(true);
            return;
        }

        if (maxDistance > 0 && traveled >= maxDistance) {
            entity.removeFromWorld();
            stopped = true;
            return;
        }

        Viewport vp = getGameScene().getViewport();
        double x = entity.getX(), y = entity.getY();
        if (x < vp.getX() - 300 || x > vp.getX() + vp.getWidth() + 300 || y > 1600 || y < vp.getY() - 1500) {
            entity.removeFromWorld();
            stopped = true;
        }
    }

    private void steer(double tpf) {
        if (homingTarget == null || traveled < homingAfter) return;
        if (!homingTarget.isActive()) {
            homingTarget = null;
            return;
        }
        Point2D toTarget = homingTarget.getCenter().subtract(entity.getCenter());
        double desired = Math.atan2(toTarget.getY(), toTarget.getX());
        double current = Math.atan2(velocity.getY(), velocity.getX());
        double delta = desired - current;
        while (delta > Math.PI) delta -= 2 * Math.PI;
        while (delta < -Math.PI) delta += 2 * Math.PI;
        double maxTurn = homingTurnRad * tpf;
        double angle = Math.abs(delta) <= maxTurn ? desired : current + Math.signum(delta) * maxTurn;
        double speed = velocity.magnitude();
        velocity = new Point2D(Math.cos(angle) * speed, Math.sin(angle) * speed);
    }

    private void rotate() {
        entity.setRotation(Math.toDegrees(Math.atan2(velocity.getY(), velocity.getX())));
    }

    private void stop(boolean terrain) {
        stopped = true;
        if (terrain && terrainSound != null) terrainSound.at(entity.getCenter(), 1, terrainSoundRate);
        if (stopListener != null) stopListener.onStop(entity.getCenter(), velocity, lastEnemy, terrain);
        entity.removeFromWorld();
    }
}
