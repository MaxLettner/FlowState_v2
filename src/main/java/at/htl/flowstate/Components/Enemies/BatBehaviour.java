package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Combat.Combat;
import at.htl.flowstate.Sfx;
import com.almasb.fxgl.core.math.FXGLMath;
import javafx.geometry.Point2D;

/**
 * Cinderbat: flies in swarms (kinematic body, no gravity). Each bat flutters around its own spot above
 * the player, now and then shrieks and stops for a moment, then dives straight at where the player was
 * and climbs back up. One hit kills it. Stunned or frozen bats sink toward the ground.
 */
public class BatBehaviour extends DemonBehaviour {
    private static final double MAX_SPEED = 270;
    private static final double ACCEL = 3.2;           // how fast the velocity follows the wanted one (1/s)
    private static final double HOVER_HEIGHT = 210;    // above the player's centre
    private static final double MIN_ALTITUDE = 70;     // above the ground while hovering
    private static final double CONTACT_DAMAGE = 4;
    private static final double DIVE_DAMAGE = 7;
    private static final double DIVE_SPEED = 560;
    private static final double DIVE_TIME = 1.0;
    private static final double WINDUP = 0.32;
    private static final double CLIMB_TIME = 0.6;
    private static final double SINK_SPEED = 110;

    private enum State { HOVER, WINDUP, DIVE, CLIMB }

    private State state = State.HOVER;
    private final double slotX = FXGLMath.random(-230, 230);
    private final double seed = FXGLMath.random(0, 100);
    private double time = seed;
    private double timer;
    private double diveTimer = FXGLMath.random(1.5, 4.0);
    private double vx, vy;
    private Point2D diveDir = Point2D.ZERO;

    @Override
    protected void think(double tpf) {
        time += tpf;
        double t = dt(tpf);
        switch (state) {
            case HOVER -> hover(tpf, t);
            case WINDUP -> {
                steer(0, -20, tpf);
                timer -= t;
                face(dxToPlayer());
                if (timer <= 0) dive();
            }
            case DIVE -> {
                vx = diveDir.getX() * DIVE_SPEED * stats.getSpeedMult();
                vy = diveDir.getY() * DIVE_SPEED * stats.getSpeedMult();
                timer -= tpf;
                boolean hit = touch(DIVE_DAMAGE);
                double ground = groundBelow();
                boolean nearGround = !Double.isNaN(ground) && entity.getBottomY() > ground - 8;
                if (hit || nearGround || timer <= 0) climb();
            }
            case CLIMB -> {
                steer(vx * 0.5, -MAX_SPEED, tpf);
                timer -= tpf;
                if (timer <= 0) state = State.HOVER;
            }
        }
        if (state != State.DIVE && state != State.WINDUP) touch(CONTACT_DAMAGE);
        apply();
        animate();
    }

    private void hover(double tpf, double t) {
        Point2D target = player.getCenter().add(slotX + Math.sin(time * 1.7) * 40, -HOVER_HEIGHT + Math.sin(time * 2.9) * 30);
        double ground = Combat.groundYBelow(target.getX(), player.getY());
        if (!Double.isNaN(ground)) target = new Point2D(target.getX(), Math.min(target.getY(), ground - MIN_ALTITUDE - entity.getHeight()));
        Point2D to = target.subtract(entity.getCenter());
        Point2D want = to.multiply(2.2);
        if (want.magnitude() > MAX_SPEED) want = want.normalize().multiply(MAX_SPEED);
        steer(want.getX() * stats.getSpeedMult(), want.getY() * stats.getSpeedMult(), tpf);
        face(Math.abs(vx) > 60 ? vx : dxToPlayer());

        diveTimer -= t;
        if (diveTimer <= 0 && entity.getCenter().distance(player.getCenter()) < 520) {
            state = State.WINDUP;
            timer = WINDUP;
            sprite.setTelegraph(TELL_MELEE);
            Sfx.BAT_SCREECH.at(entity.getCenter(), 0.8, FXGLMath.random(0.9, 1.2));
        }
    }

    private void dive() {
        sprite.setTelegraph(null);
        Point2D to = player.getCenter().subtract(entity.getCenter());
        diveDir = to.magnitude() < 1 ? new Point2D(0, 1) : to.normalize();
        state = State.DIVE;
        timer = DIVE_TIME;
        face(diveDir.getX());
    }

    private void climb() {
        state = State.CLIMB;
        timer = CLIMB_TIME;
        diveTimer = FXGLMath.random(2.5, 5.0);
    }

    private void steer(double wantX, double wantY, double tpf) {
        double k = Math.min(1, ACCEL * tpf);
        vx += (wantX - vx) * k;
        vy += (wantY - vy) * k;
    }

    private void apply() {
        physics.setVelocityX(vx);
        physics.setVelocityY(vy);
    }

    private double groundBelow() {
        return Combat.groundYBelow(entity.getCenter().getX(), entity.getY());
    }

    private void face(double dx) {
        if (Math.abs(dx) > 1) sprite.setFacing(dx > 0 ? 1 : -1);
    }

    private void animate() {
        if (state == State.DIVE) {
            sprite.loop("dive", 10);
            // points along the dive: the art faces right, so mirror the angle when flying left
            double deg = Math.toDegrees(Math.atan2(vy, Math.abs(vx)));
            sprite.setTilt(sprite.getFacing() * deg);
        } else {
            sprite.loop("fly", state == State.WINDUP ? 20 : 13);
            sprite.setTilt(0);
        }
    }

    @Override
    protected void whileDisabled(double tpf) {
        if (stats.isKnockedBack()) {
            // the stats push x; damp the upward hop so a kinematic body doesn't float away
            physics.setVelocityY(physics.getVelocityY() * Math.max(0, 1 - tpf * 6));
            vx = physics.getVelocityX();
            vy = physics.getVelocityY();
            return;
        }
        double ground = groundBelow();
        boolean low = !Double.isNaN(ground) && entity.getBottomY() > ground - 4;
        vx = 0;
        vy = low ? 0 : SINK_SPEED;
        apply();
        sprite.setTilt(0);
    }

    @Override
    protected void interrupt() {
        state = State.CLIMB;
        timer = CLIMB_TIME;
        diveTimer = Math.max(diveTimer, 1.5);
    }
}
