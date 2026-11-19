package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Sfx;
import com.almasb.fxgl.core.math.FXGLMath;

/**
 * Imp: small, fast pack demon. Runs at the player (each imp aims a little to the side so a pack spreads out),
 * crouches for a blink and hops at them claws first.
 */
public class ImpBehaviour extends GroundDemonBehaviour {
    private static final double SPEED = 250;
    private static final double CONTACT_DAMAGE = 4;
    private static final double LEAP_DAMAGE = 7;
    private static final double LEAP_MIN = 50, LEAP_MAX = 210;
    private static final double CROUCH_TIME = 0.28;
    private static final double RECOVER_TIME = 0.35;
    private static final double LEAP_VY = 430;

    private enum State { CHASE, CROUCH, LEAP, RECOVER }

    private State state = State.CHASE;
    private double timer;
    private double cooldown = FXGLMath.random(0.3, 1.5);
    private double offset = FXGLMath.random(-40, 40);
    private double offsetTimer;
    private boolean leapHit, leftGround;

    @Override
    protected void act(double tpf) {
        double t = dt(tpf);
        cooldown -= t;
        switch (state) {
            case CHASE -> chase(tpf);
            case CROUCH -> {
                stop();
                face(dirToPlayer());
                sprite.show("crouch", 0);
                if ((timer -= t) <= 0) leap();
            }
            case LEAP -> {
                if (!grounded) leftGround = true;
                if (!leapHit && touch(LEAP_DAMAGE)) leapHit = true;
                sprite.show("leap", physics.getVelocityY() < 0 ? 0 : 1);
                if (leftGround && grounded) {
                    state = State.RECOVER;
                    timer = RECOVER_TIME;
                    stop();
                }
            }
            case RECOVER -> {
                stop();
                sprite.show("crouch", 0);
                if ((timer -= t) <= 0) state = State.CHASE;
            }
        }
    }

    private void chase(double tpf) {
        if ((offsetTimer -= tpf) <= 0) {
            offsetTimer = FXGLMath.random(1.5, 3.0);
            offset = FXGLMath.random(-40, 40);
        }
        double dx = dxToPlayer() + offset;
        walk(Math.abs(dx) < 8 ? 0 : dx > 0 ? 1 : -1, SPEED);
        if (Math.abs(dx) < 8) face(dirToPlayer());
        touch(CONTACT_DAMAGE);
        locomotion("idle", "run", 14, "leap");

        double adx = Math.abs(dxToPlayer());
        if (grounded && cooldown <= 0 && adx > LEAP_MIN && adx < LEAP_MAX && Math.abs(dyToPlayer()) < 120) {
            state = State.CROUCH;
            timer = CROUCH_TIME;
            Sfx.IMP_LEAP.at(entity.getCenter(), 0.7, FXGLMath.random(0.9, 1.15));
        }
    }

    private void leap() {
        int dir = dirToPlayer();
        face(dir);
        double reach = Math.max(260, Math.min(520, Math.abs(dxToPlayer()) * 2.4));
        physics.setVelocityX(dir * reach * stats.getSpeedMult());
        physics.setVelocityY(-LEAP_VY);
        state = State.LEAP;
        leapHit = false;
        leftGround = false;
        grounded = false;
        cooldown = FXGLMath.random(1.6, 2.6);
    }

    @Override
    protected void interrupt() {
        state = State.CHASE;
    }

    @Override
    protected void disabledPose() {
        sprite.show("crouch", 0);
    }
}
