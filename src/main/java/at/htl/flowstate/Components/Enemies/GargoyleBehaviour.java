package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Combat.Combat;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.core.math.FXGLMath;
import javafx.geometry.Point2D;

/**
 * Gargoyle: stands on the ground as a stone statue (nothing hurts it) until you come close. Then it cracks open,
 * circles above you, swoops, lands and turns back to stone for a few seconds before taking off again.
 * Hit it while it flies. Kinematic body like the bats.
 */
public class GargoyleBehaviour extends DemonBehaviour {
    private static final double WAKE_RANGE = 330;
    private static final double WAKE_TIME = 0.6;
    private static final double MAX_SPEED = 300;
    private static final double ACCEL = 3.0;
    private static final double HOVER_HEIGHT = 190;
    private static final double SWOOP_SPEED = 640;
    private static final double SWOOP_TIME = 1.1;
    private static final double SWOOP_DAMAGE = 13;
    private static final double CONTACT_DAMAGE = 5;
    private static final double WINDUP = 0.35;
    private static final double LAND_SPEED = 340;
    private static final double REST_TIME = 2.6;
    private static final double SINK_SPEED = 160;
    private static final javafx.scene.paint.Color[] GRIT = {javafx.scene.paint.Color.web("#9a958c"),
            javafx.scene.paint.Color.web("#55524d"), javafx.scene.paint.Color.web("#ff8a2a")};

    private enum State { STATUE, WAKE, FLY, WINDUP, SWOOP, LAND, REST }

    private State state = State.STATUE;
    private double timer;
    private double swoopTimer;
    private double vx, vy;
    private double time = FXGLMath.random(0, 10);
    private final double side = FXGLMath.randomBoolean() ? 1 : -1;
    private Point2D dir = Point2D.ZERO;

    @Override
    protected void onReady() {
        stone(true);
    }

    private void stone(boolean on) {
        stats.setInvulnerable(on);
        if (on) sprite.show("stone", 0);
    }

    @Override
    protected void think(double tpf) {
        time += tpf;
        double t = dt(tpf);
        switch (state) {
            case STATUE -> {
                vx = vy = 0;
                if (Math.abs(dxToPlayer()) < WAKE_RANGE && Math.abs(dyToPlayer()) < 400) wake();
            }
            case WAKE -> {
                vx = vy = 0;
                timer += tpf;
                sprite.show("wake", Math.min(2, (int) (timer / WAKE_TIME * 3)));
                if (timer >= WAKE_TIME) {
                    state = State.FLY;
                    stone(false);
                    sprite.setTelegraph(null);
                    vy = -MAX_SPEED;
                    swoopTimer = FXGLMath.random(1.2, 2.2);
                }
            }
            case FLY -> fly(tpf, t);
            case WINDUP -> {
                steer(0, -30, tpf);
                face(dxToPlayer());
                if ((timer -= t) <= 0) swoop();
            }
            case SWOOP -> {
                vx = dir.getX() * SWOOP_SPEED * stats.getSpeedMult();
                vy = dir.getY() * SWOOP_SPEED * stats.getSpeedMult();
                timer -= tpf;
                boolean hit = touch(SWOOP_DAMAGE);
                double ground = groundBelow();
                boolean low = !Double.isNaN(ground) && entity.getBottomY() > ground - 10;
                if (hit || low || timer <= 0) {
                    state = State.LAND;
                    vx *= 0.3;
                }
            }
            case LAND -> {
                double ground = groundBelow();
                if (Double.isNaN(ground)) {                    // over a pit: keep flying instead
                    state = State.FLY;
                    break;
                }
                vx *= Math.max(0, 1 - tpf * 5);
                vy = LAND_SPEED;
                if (entity.getBottomY() >= ground - 2) {
                    physics.overwritePosition(new Point2D(entity.getX(), ground - entity.getHeight()));
                    vx = vy = 0;
                    state = State.REST;
                    timer = REST_TIME;
                    stone(true);
                    Sfx.STONE_CRACK.at(entity.getCenter(), 0.8, 0.8);
                    Particles.burst(new Point2D(entity.getCenter().getX(), entity.getBottomY()), Particles.DUST, 10, -90, 70, 60, 200, 0.5, 5, 700);
                }
            }
            case REST -> {
                vx = vy = 0;
                if ((timer -= tpf) <= 0) wake();
            }
        }
        if (state == State.FLY) touch(CONTACT_DAMAGE);
        physics.setVelocityX(vx);
        physics.setVelocityY(vy);
        animate();
    }

    private void wake() {
        state = State.WAKE;
        timer = 0;
        face(dxToPlayer());
        sprite.setTelegraph(TELL_MELEE);
        Sfx.STONE_CRACK.at(entity.getCenter());
        Particles.burst(entity.getCenter(), GRIT, 12, -90, 120, 60, 220, 0.6, 5, 600);
    }

    private void fly(double tpf, double t) {
        Point2D target = player.getCenter().add(side * (140 + Math.sin(time * 1.3) * 60), -HOVER_HEIGHT + Math.sin(time * 2.2) * 25);
        Point2D want = target.subtract(entity.getCenter()).multiply(2.0);
        if (want.magnitude() > MAX_SPEED) want = want.normalize().multiply(MAX_SPEED);
        steer(want.getX() * stats.getSpeedMult(), want.getY() * stats.getSpeedMult(), tpf);
        face(dxToPlayer());
        swoopTimer -= t;
        if (swoopTimer <= 0) {
            state = State.WINDUP;
            timer = WINDUP;
            sprite.setTelegraph(TELL_MELEE);
            Sfx.BAT_SCREECH.at(entity.getCenter(), 0.9, 0.55);
        }
    }

    private void swoop() {
        sprite.setTelegraph(null);
        Point2D to = player.getCenter().subtract(entity.getCenter());
        dir = to.magnitude() < 1 ? new Point2D(0, 1) : to.normalize();
        state = State.SWOOP;
        timer = SWOOP_TIME;
        face(dir.getX());
    }

    private void steer(double wx, double wy, double tpf) {
        double k = Math.min(1, ACCEL * tpf);
        vx += (wx - vx) * k;
        vy += (wy - vy) * k;
    }

    private double groundBelow() {
        return Combat.groundYBelow(entity.getCenter().getX(), entity.getY());
    }

    private void face(double dx) {
        if (Math.abs(dx) > 1) sprite.setFacing(dx > 0 ? 1 : -1);
    }

    private void animate() {
        switch (state) {
            case FLY, WINDUP -> {
                sprite.loop("fly", state == State.WINDUP ? 18 : 11);
                sprite.setTilt(0);
            }
            case SWOOP, LAND -> {
                sprite.loop("swoop", 8);
                double deg = Math.toDegrees(Math.atan2(vy, Math.abs(vx) + 1)) * 0.5;
                sprite.setTilt(sprite.getFacing() * deg);
            }
            case STATUE, REST -> sprite.setTilt(0);
            default -> { }
        }
    }

    @Override
    protected void whileDisabled(double tpf) {
        if (stats.isKnockedBack()) {
            physics.setVelocityY(physics.getVelocityY() * Math.max(0, 1 - tpf * 6));
            vx = physics.getVelocityX();
            vy = physics.getVelocityY();
            return;
        }
        double ground = groundBelow();
        boolean low = !Double.isNaN(ground) && entity.getBottomY() > ground - 4;
        vx = 0;
        vy = low ? 0 : SINK_SPEED;
        physics.setVelocityX(vx);
        physics.setVelocityY(vy);
        sprite.setTilt(0);
    }

    @Override
    protected void interrupt() {
        if (state == State.WINDUP || state == State.SWOOP) state = State.LAND;
    }
}
