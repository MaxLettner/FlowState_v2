package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Sfx;
import at.htl.flowstate.Visuals.Impact;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.core.math.FXGLMath;
import javafx.geometry.Point2D;

/**
 * Pit Brute: slow and huge (half knockback, short stuns don't register). Up close it raises both fists,
 * roaring and glowing, and slams the ground: the fists hit right in front of it and a shockwave rolls
 * out to both sides along the floor. Jump the shockwave; freeze it and shatter.
 */
public class BruteBehaviour extends GroundDemonBehaviour {
    private static final double SPEED = 105;
    private static final double CONTACT_DAMAGE = 10;
    private static final double SLAM_DAMAGE = 24;
    private static final double SLAM_RANGE = 150;      // fists reach in front of the body
    private static final double TRIGGER_RANGE = 360;
    private static final double RAISE = 0.3, HOLD = 0.5, DOWN = 0.09;
    private static final double AFTER_IMPACT = 0.25, RECOVER = 0.75;
    /** The Brute's strips have twice the frames of the other demons (idle 8, walk 12, slam 12, air 2). */
    private static final double IDLE_FPS = 14, WALK_FPS = 16, AIR_FPS = 6, RECOVER_FPS = 12;
    private static final double TREMBLE_FPS = 14;      // overhead hold: slam frames 3 and 4 alternate

    private enum State { WALK, WINDUP, SLAM, RECOVER }

    private State state = State.WALK;
    private double timer;
    private double cooldown = FXGLMath.random(0.5, 1.2);
    private boolean impacted;

    @Override
    protected void act(double tpf) {
        double t = dt(tpf);
        cooldown -= t;
        switch (state) {
            case WALK -> walkState();
            case WINDUP -> {
                stop();
                timer += t;
                if (timer < RAISE) {
                    sprite.show("slam", (int) (timer / RAISE * 3));                      // 0..2: fists come up
                } else {
                    sprite.show("slam", 3 + (int) ((timer - RAISE) * TREMBLE_FPS) % 2);  // 3/4: straining overhead
                }
                if (timer >= RAISE + HOLD) {
                    state = State.SLAM;
                    timer = 0;
                    impacted = false;
                    sprite.setTelegraph(null);
                }
            }
            case SLAM -> {
                stop();
                timer += t;
                if (timer < DOWN) {
                    sprite.show("slam", timer < DOWN * 0.5 ? 5 : 6);
                } else {
                    if (!impacted) impact();
                    double after = timer - DOWN;                                          // 7..11: impact, settle
                    sprite.show("slam", after < 0.06 ? 7 : after < AFTER_IMPACT * 0.5 ? 8
                            : after < AFTER_IMPACT ? 9 : after < AFTER_IMPACT + 0.08 ? 10 : 11);
                    if (timer >= DOWN + AFTER_IMPACT + 0.15) {
                        state = State.RECOVER;
                        timer = RECOVER;
                    }
                }
            }
            case RECOVER -> {
                stop();
                sprite.loop("idle", RECOVER_FPS);
                if ((timer -= t) <= 0) state = State.WALK;
            }
        }
    }

    private void walkState() {
        double dx = dxToPlayer();
        walk(Math.abs(dx) < 20 ? 0 : dx > 0 ? 1 : -1, SPEED);
        touch(CONTACT_DAMAGE);
        locomotion("idle", IDLE_FPS, "walk", WALK_FPS, "air", AIR_FPS);
        if (grounded && cooldown <= 0 && Math.abs(dx) < TRIGGER_RANGE && Math.abs(dyToPlayer()) < 200) {
            face(dirToPlayer());
            state = State.WINDUP;
            timer = 0;
            sprite.setTelegraph(TELL_MELEE);
            Sfx.BRUTE_ROAR.at(entity.getCenter());
        }
    }

    private void impact() {
        impacted = true;
        cooldown = FXGLMath.random(3.0, 4.0);
        double groundY = entity.getBottomY();
        double fistX = entity.getCenter().getX() + facing * (entity.getWidth() / 2 + 20);
        Sfx.BRUTE_SLAM.at(entity.getCenter());
        Impact.shake(16);
        Particles.landingDust(new Point2D(fistX, groundY));
        Particles.burst(new Point2D(fistX, groundY - 4), Particles.DUST, 28, -90, 75, 160, 520, 0.7, 8, 900);
        Particles.burst(new Point2D(fistX, groundY - 4), Particles.EMBER, 14, -90, 55, 140, 420, 0.6, 5, 600);

        // the fists themselves
        double px = player.getCenter().getX() - entity.getCenter().getX();
        boolean inFront = px * facing > -entity.getWidth() / 2 && Math.abs(px) < entity.getWidth() / 2 + SLAM_RANGE;
        boolean low = player.getBottomY() > groundY - 110 && player.getY() < groundY + 10;
        if (inFront && low) hurtPlayer(SLAM_DAMAGE);

        ShockwaveComponent.spawn(fistX, groundY, facing, entity);
        ShockwaveComponent.spawn(entity.getCenter().getX() - facing * entity.getWidth() / 2, groundY, -facing, entity);
    }

    @Override
    protected void interrupt() {
        state = State.WALK;
    }

    @Override
    protected void disabledPose() {
        sprite.loop("idle", RECOVER_FPS);
    }
}
