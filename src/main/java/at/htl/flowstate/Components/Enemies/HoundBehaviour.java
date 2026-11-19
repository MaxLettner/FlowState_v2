package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Sfx;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.core.math.FXGLMath;
import javafx.geometry.Point2D;

/**
 * Hellhound: fast and low. From mid range it crouches and growls (glowing red), then lunges a long way;
 * after landing it pants for almost a second, which is the moment to hit it. Jump over the lunge.
 */
public class HoundBehaviour extends GroundDemonBehaviour {
    private static final double SPEED = 320;
    private static final double CONTACT_DAMAGE = 6;
    private static final double LUNGE_DAMAGE = 16;
    private static final double LUNGE_MIN = 140, LUNGE_MAX = 400;
    private static final double WINDUP = 0.55;
    private static final double LUNGE_VX = 820, LUNGE_VY = 260;
    private static final double RECOVER = 0.9;

    private enum State { RUN, WINDUP, LUNGE, RECOVER }

    private State state = State.RUN;
    private double timer;
    private double cooldown = FXGLMath.random(0.5, 1.5);
    private boolean lungeHit, leftGround;
    private double airTime;

    @Override
    protected void act(double tpf) {
        double t = dt(tpf);
        cooldown -= t;
        switch (state) {
            case RUN -> run();
            case WINDUP -> {
                stop();
                face(dirToPlayer());
                sprite.loop("crouch", 16);
                if ((timer -= t) <= 0) lunge();
            }
            case LUNGE -> {
                airTime += tpf;
                if (!grounded) leftGround = true;
                if (!lungeHit && touch(LUNGE_DAMAGE)) lungeHit = true;
                sprite.loop("lunge", 10);
                if (leftGround && grounded && airTime > 0.1) {
                    state = State.RECOVER;
                    timer = RECOVER;
                    Particles.landingDust(new Point2D(entity.getCenter().getX(), entity.getBottomY()));
                }
            }
            case RECOVER -> {
                physics.setVelocityX(physics.getVelocityX() * Math.max(0, 1 - tpf * 8));   // skid to a halt
                sprite.loop("pant", 6);
                if ((timer -= t) <= 0) state = State.RUN;
            }
        }
    }

    private void run() {
        double dx = dxToPlayer();
        walk(Math.abs(dx) < 10 ? 0 : dx > 0 ? 1 : -1, SPEED);
        touch(CONTACT_DAMAGE);
        locomotion("idle", "run", 15, "lunge");
        double adx = Math.abs(dx);
        if (grounded && cooldown <= 0 && adx > LUNGE_MIN && adx < LUNGE_MAX && Math.abs(dyToPlayer()) < 100) {
            state = State.WINDUP;
            timer = WINDUP;
            sprite.setTelegraph(TELL_MELEE);
            Sfx.HOUND_GROWL.at(entity.getCenter());
        }
    }

    private void lunge() {
        int dir = dirToPlayer();
        face(dir);
        sprite.setTelegraph(null);
        physics.setVelocityX(dir * LUNGE_VX * stats.getSpeedMult());
        physics.setVelocityY(-LUNGE_VY);
        Sfx.HOUND_LUNGE.at(entity.getCenter());
        state = State.LUNGE;
        lungeHit = false;
        leftGround = false;
        grounded = false;
        airTime = 0;
        cooldown = FXGLMath.random(2.2, 3.0);
    }

    @Override
    protected void interrupt() {
        state = State.RUN;
    }

    @Override
    protected void disabledPose() {
        sprite.loop("pant", 6);
    }
}
