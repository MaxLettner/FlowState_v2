package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Combat.Combat;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.core.math.FXGLMath;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;

/**
 * Hellfire Cultist: keeps its distance and casts three slow homing hellfire orbs (cut through them with a
 * swing). When you get close it vanishes in green fire and reappears further away, so catch it right
 * after a teleport or pin it.
 */
public class CultistBehaviour extends GroundDemonBehaviour {
    private static final double SPEED = 115;
    private static final double CONTACT_DAMAGE = 4;
    private static final double ORB_DAMAGE = 10;
    private static final double TOO_CLOSE = 360, TOO_FAR = 700, MAX_CAST = 950;
    private static final double TELEPORT_NEAR = 170;
    private static final double FADE = 0.35;
    // cast timeline (s): hands up, orb grows, release, recover
    private static final double T1 = 0.25, T2 = 0.55, T_RELEASE = 0.85, T4 = 1.05, T_END = 1.3;
    private static final Color[] FEL = {Color.web("#f4ffd0"), Color.web("#b8ff6a"), Color.web("#5fe83a"), Color.web("#22a82e")};

    private enum State { MOVE, CAST, VANISH, APPEAR }

    private State state = State.MOVE;
    private double timer;
    private boolean released;
    private double castCooldown = FXGLMath.random(0.8, 1.8);
    private double teleportCooldown = 0;

    @Override
    protected void act(double tpf) {
        double t = dt(tpf);
        castCooldown -= t;
        teleportCooldown -= tpf;
        switch (state) {
            case MOVE -> move();
            case CAST -> cast(t);
            case VANISH -> {
                stop();
                timer += tpf;
                entity.setOpacity(Math.max(0, 1 - timer / FADE));
                if (timer >= FADE) reappear();
            }
            case APPEAR -> {
                stop();
                face(dirToPlayer());
                timer += tpf;
                entity.setOpacity(Math.min(1, timer / FADE));
                sprite.loop("idle", 7);
                if (timer >= FADE) {
                    state = State.MOVE;
                    entity.setOpacity(1);
                }
            }
        }
    }

    private void move() {
        double dx = dxToPlayer();
        double adx = Math.abs(dx);
        int toward = dx >= 0 ? 1 : -1;
        if (adx < TOO_CLOSE) walk(-toward, SPEED);
        else if (adx > TOO_FAR) walk(toward, SPEED * 1.2);
        else stop();
        if (adx <= TOO_FAR) face(toward);
        touch(CONTACT_DAMAGE);
        locomotion("idle", "walk", 9, "air");

        if (grounded && adx < TELEPORT_NEAR && teleportCooldown <= 0) {
            vanish();
        } else if (grounded && castCooldown <= 0 && adx < MAX_CAST && adx > 150) {
            state = State.CAST;
            timer = 0;
            released = false;
            face(toward);
            sprite.setTelegraph(TELL_SPELL);
            Sfx.CULTIST_CAST.at(entity.getCenter());
        }
    }

    private void cast(double t) {
        stop();
        timer += t;
        sprite.show("cast", timer < T1 ? 0 : timer < T2 ? 1 : timer < T_RELEASE ? 2 : timer < T4 ? 3 : 4);
        if (timer < T_RELEASE && Math.random() < 0.5) {
            Point2D hands = entity.getCenter().add(facing * 6, -30);
            Particles.burst(hands, FEL, 1, -90, 120, 20, 70, 0.4, 4, -60);
        }
        if (!released && timer >= T_RELEASE) {
            released = true;
            sprite.setTelegraph(null);
            Point2D from = entity.getCenter().add(facing * 16, -26);
            Point2D to = player.getCenter().subtract(from);
            double aim = Math.atan2(to.getY(), to.getX());
            for (int k = -1; k <= 1; k++) HomingOrbComponent.spawn(from, aim + k * 0.6, ORB_DAMAGE, entity);
            Sfx.HELLFIRE_LAUNCH.at(from);
        }
        if (timer >= T_END) {
            state = State.MOVE;
            castCooldown = FXGLMath.random(3.2, 4.2);
        }
    }

    private void vanish() {
        state = State.VANISH;
        timer = 0;
        teleportCooldown = 5.0;
        sprite.setTelegraph(null);
        Sfx.TELEPORT.at(entity.getCenter());
        burst();
    }

    /** Somewhere 450-600 px from the player on solid ground, preferring ahead (where the world goes on). */
    private void reappear() {
        double px = player.getCenter().getX();
        double feet = entity.getBottomY();
        for (int attempt = 0; attempt < 6; attempt++) {
            int side = attempt % 2 == 0 ? 1 : -1;
            double x = px + side * FXGLMath.random(450, 600);
            double ground = Combat.groundYBelow(x, feet - 250);
            if (Double.isNaN(ground)) continue;
            physics.overwritePosition(new Point2D(x - entity.getWidth() / 2, ground - entity.getHeight() - 2));
            physics.setVelocityY(0);
            break;
        }
        state = State.APPEAR;
        timer = 0;
        burst();
    }

    private void burst() {
        Particles.burst(entity.getCenter(), FEL, 22, -90, 180, 60, 260, 0.6, 5, -80);
    }

    @Override
    protected void interrupt() {
        // a stun mid-cast cancels the orbs; mid-teleport it finishes the jump so it never stays invisible
        if (state == State.VANISH) reappear();
        if (state == State.APPEAR) entity.setOpacity(1);
        state = State.MOVE;
        castCooldown = Math.max(castCooldown, 1.0);
    }

    @Override
    protected void disabledPose() {
        sprite.loop("idle", 6);
    }
}
