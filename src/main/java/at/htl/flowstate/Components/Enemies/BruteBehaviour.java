package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Components.Player.PlayerStatsComponent;
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
    private static final double SLAM_RANGE = 120;      // fists reach in front of the body
    private static final double TRIGGER_RANGE = 230;
    private static final double RAISE = 0.3, HOLD = 0.5, DOWN = 0.09;
    private static final double AFTER_IMPACT = 0.25, RECOVER = 0.75;

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
                sprite.show("slam", timer < RAISE ? 0 : 1);
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
                    sprite.show("slam", 2);
                } else {
                    if (!impacted) impact();
                    sprite.show("slam", timer < DOWN + AFTER_IMPACT * 0.5 ? 3 : timer < DOWN + AFTER_IMPACT ? 4 : 5);
                    if (timer >= DOWN + AFTER_IMPACT + 0.15) {
                        state = State.RECOVER;
                        timer = RECOVER;
                    }
                }
            }
            case RECOVER -> {
                stop();
                sprite.loop("idle", 6);
                if ((timer -= t) <= 0) state = State.WALK;
            }
        }
    }

    private void walkState() {
        double dx = dxToPlayer();
        walk(Math.abs(dx) < 20 ? 0 : dx > 0 ? 1 : -1, SPEED);
        touch(CONTACT_DAMAGE);
        locomotion("idle", "walk", 8, "air");
        if (grounded && cooldown <= 0 && Math.abs(dx) < TRIGGER_RANGE && Math.abs(dyToPlayer()) < 140) {
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
        double fistX = entity.getCenter().getX() + facing * (entity.getWidth() / 2 + 30);
        Sfx.BRUTE_SLAM.at(entity.getCenter());
        Impact.shake(11);
        Particles.landingDust(new Point2D(fistX, groundY));
        Particles.burst(new Point2D(fistX, groundY - 4), Particles.DUST, 16, -90, 70, 120, 380, 0.6, 6, 900);
        Particles.burst(new Point2D(fistX, groundY - 4), Particles.EMBER, 8, -90, 50, 100, 300, 0.5, 4, 600);

        // the fists themselves
        double px = player.getCenter().getX() - entity.getCenter().getX();
        boolean inFront = px * facing > -entity.getWidth() / 2 && Math.abs(px) < entity.getWidth() / 2 + SLAM_RANGE;
        boolean low = player.getBottomY() > groundY - 70 && player.getY() < groundY + 10;
        if (inFront && low) player.getComponent(PlayerStatsComponent.class).takeDamage(SLAM_DAMAGE, entity, false);

        ShockwaveComponent.spawn(fistX, groundY, 1);
        ShockwaveComponent.spawn(entity.getCenter().getX() - facing * entity.getWidth() / 2, groundY, -1);
    }

    @Override
    protected void interrupt() {
        state = State.WALK;
    }

    @Override
    protected void disabledPose() {
        sprite.loop("idle", 6);
    }
}
