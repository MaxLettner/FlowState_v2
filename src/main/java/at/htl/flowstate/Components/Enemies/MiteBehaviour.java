package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Sfx;
import com.almasb.fxgl.core.math.FXGLMath;

/**
 * Void mite: a tiny spider out of the Matriarch's egg sacs. Skitters at the player and pounces; it can spring up
 * onto a bridge above (it does not need a ledge to climb, unlike an imp).
 */
public class MiteBehaviour extends GroundDemonBehaviour {
    private static final double SPEED = 270;
    private static final double CONTACT_DAMAGE = 4;
    private static final double LEAP_DAMAGE = 8;
    private static final double LEAP_MIN = 40, LEAP_MAX = 220;
    private static final double CROUCH_TIME = 0.25, RECOVER_TIME = 0.35;
    private static final double LEAP_VY = 460, SPRING_VY = 720;

    private enum State { CHASE, CROUCH, LEAP, RECOVER }

    private State state = State.CHASE;
    private double timer;
    private double cooldown = FXGLMath.random(0.4, 1.2);
    private boolean leapHit, leftGround, spring;

    @Override
    protected void act(double tpf) {
        double t = dt(tpf);
        cooldown -= t;
        switch (state) {
            case CHASE -> chase();
            case CROUCH -> {
                stop();
                face(dirToPlayer());
                sprite.show("leap", 0);
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
                sprite.loop("idle", 10);
                if ((timer -= t) <= 0) state = State.CHASE;
            }
        }
    }

    private void chase() {
        double dx = dxToPlayer();
        walk(Math.abs(dx) < 10 ? 0 : dx > 0 ? 1 : -1, SPEED);
        touch(CONTACT_DAMAGE);
        locomotion("idle", 10, "walk", 18, "leap", 6);
        if (!grounded || cooldown > 0) return;
        double adx = Math.abs(dx), dy = dyToPlayer();
        boolean above = dy < -90 && adx < 200;                     // the player is up on a bridge: spring up
        if (above || (adx > LEAP_MIN && adx < LEAP_MAX && Math.abs(dy) < 100)) {
            spring = above;
            state = State.CROUCH;
            timer = CROUCH_TIME;
            Sfx.IMP_LEAP.at(entity.getCenter(), 0.5, FXGLMath.random(1.4, 1.7));
        }
    }

    private void leap() {
        int dir = dirToPlayer();
        face(dir);
        double reach = spring ? Math.min(260, Math.abs(dxToPlayer()) * 1.6)
                : Math.max(260, Math.min(520, Math.abs(dxToPlayer()) * 2.4));
        physics.setVelocityX(dir * reach * stats.getSpeedMult());
        physics.setVelocityY(-(spring ? SPRING_VY : LEAP_VY));
        state = State.LEAP;
        leapHit = false;
        leftGround = false;
        grounded = false;
        cooldown = FXGLMath.random(1.2, 2.0);
    }

    @Override
    protected void interrupt() {
        state = State.CHASE;
    }

    @Override
    protected void disabledPose() {
        sprite.show("leap", 0);
    }
}
