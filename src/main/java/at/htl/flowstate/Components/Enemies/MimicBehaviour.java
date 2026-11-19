package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Combat.Hit;
import at.htl.flowstate.Components.Player.PlayerStatsComponent;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Visuals.Impact;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.core.math.FXGLMath;
import com.almasb.fxgl.dsl.components.HealthDoubleComponent;
import com.almasb.fxgl.entity.Entity;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;

/**
 * Mimic: a chest that isn't. Asleep it looks exactly like one (now and then the lid twitches); reach for it, or hit
 * it, and it springs awake, hops after you on spindly legs and bites. Killing it pays out what the chest would have:
 * a skill point, and the experience on top.
 */
public class MimicBehaviour extends GroundDemonBehaviour {
    private static final double WAKE_X = 70, WAKE_Y = 90;      // how close (beyond the hitbox) wakes it
    private static final double WAKE_TIME = 0.5;
    private static final double CONTACT_DAMAGE = 8;
    private static final double BITE_DAMAGE = 18;
    private static final double BITE_RANGE_X = 70, BITE_RANGE_Y = 70;
    private static final double BITE_OPEN = 0.32, BITE_SNAP = 0.1, BITE_RECOVER = 0.4;
    private static final double HOP_CROUCH = 0.16, HOP_VX = 330, HOP_VY = 470, HOP_LAND = 0.14, HOP_REST = 0.22;
    private static final Color[] GOLD = {Color.web("#fff4b0"), Color.web("#f5cd52"), Color.web("#cf9c2f")};

    private enum State { DORMANT, WAKE, REST, CROUCH, HOP, LAND, BITE }

    private State state = State.DORMANT;
    private double timer;
    private double twitchTimer = FXGLMath.random(3, 7);
    private boolean bit, leftGround;

    /** Asleep: homing spells don't pick it out of the chests. */
    @Override
    public boolean isLurking() {
        return state == State.DORMANT;
    }

    @Override
    protected void act(double tpf) {
        double t = dt(tpf);
        switch (state) {
            case DORMANT -> dormant(tpf);
            case WAKE -> {
                stop();
                timer += t;
                sprite.show("wake", Math.min(2, (int) (timer / WAKE_TIME * 3)));
                if (timer >= WAKE_TIME) rest(0.1);
            }
            case REST -> {
                stop();
                face(dirToPlayer());
                touch(CONTACT_DAMAGE);
                sprite.loop("idle", 8);
                if ((timer -= t) <= 0) {
                    if (inBiteRange()) startBite();
                    else {
                        state = State.CROUCH;
                        timer = HOP_CROUCH;
                    }
                }
            }
            case CROUCH -> {
                stop();
                face(dirToPlayer());
                sprite.show("hop", 0);
                if ((timer -= t) <= 0) hop();
            }
            case HOP -> {
                if (!grounded) leftGround = true;
                touch(CONTACT_DAMAGE);
                sprite.show("hop", physics.getVelocityY() < 0 ? 1 : 2);
                if (leftGround && grounded) {
                    state = State.LAND;
                    timer = HOP_LAND;
                    stop();
                    Sfx.LAND.at(entity.getCenter(), 0.6, 0.7);
                    Particles.landingDust(new Point2D(entity.getCenter().getX(), entity.getBottomY()));
                }
            }
            case LAND -> {
                stop();
                sprite.show("hop", 3);
                if ((timer -= t) <= 0) {
                    if (inBiteRange()) startBite();
                    else rest(HOP_REST);
                }
            }
            case BITE -> {
                stop();
                timer += t;
                if (timer < BITE_OPEN) {
                    sprite.show("bite", 0);
                } else {
                    if (!bit) {
                        bit = true;
                        sprite.setTelegraph(null);
                        Sfx.MIMIC_CHOMP.at(entity.getCenter());
                        if (inBiteRange()) {
                            hurtPlayer(BITE_DAMAGE);
                            Impact.shake(5);
                        }
                    }
                    sprite.show("bite", timer < BITE_OPEN + BITE_SNAP ? 1 : 2);
                    if (timer >= BITE_OPEN + BITE_SNAP + BITE_RECOVER) rest(HOP_REST);
                }
            }
        }
    }

    private void dormant(double tpf) {
        stop();
        // the tell: once in a while the lid lifts a hair
        twitchTimer -= tpf;
        sprite.show("dormant", twitchTimer < 0.14 ? 1 : 0);
        if (twitchTimer <= 0) twitchTimer = FXGLMath.random(4, 8);
        double gapX = Math.abs(dxToPlayer()) - (entity.getWidth() + player.getWidth()) / 2;
        boolean hurt = entity.getComponent(HealthDoubleComponent.class).getValuePercent() < 100;   // burnt, poisoned
        if (hurt || gapX < WAKE_X && Math.abs(dyToPlayer()) < WAKE_Y) wake();
    }

    private void wake() {
        if (state != State.DORMANT) return;
        state = State.WAKE;
        timer = 0;
        face(dirToPlayer());
        Sfx.CHEST_OPEN.at(entity.getCenter(), 0.8, 0.6);
        Sfx.MIMIC_CHOMP.at(entity.getCenter(), 0.9, 0.8);
        Particles.burst(entity.getCenter(), Particles.DUST, 10, -90, 80, 60, 200, 0.6, 5, 500);
    }

    /** A hit wakes it (and lands in full). */
    @Override
    public double guard(Hit h, Entity player) {
        wake();
        return 1;
    }

    private void rest(double time) {
        state = State.REST;
        timer = time;
    }

    private boolean inBiteRange() {
        double gapX = Math.abs(dxToPlayer()) - (entity.getWidth() + player.getWidth()) / 2;
        return gapX < BITE_RANGE_X && Math.abs(dyToPlayer()) < BITE_RANGE_Y;
    }

    private void startBite() {
        state = State.BITE;
        timer = 0;
        bit = false;
        face(dirToPlayer());
        sprite.setTelegraph(TELL_MELEE);
    }

    private void hop() {
        int dir = dirToPlayer();
        face(dir);
        double reach = Math.max(180, Math.min(HOP_VX, Math.abs(dxToPlayer()) * 1.6));
        physics.setVelocityX(dir * reach * stats.getSpeedMult());
        physics.setVelocityY(-HOP_VY);
        Sfx.ENEMY_JUMP.at(entity.getCenter(), 0.8, 0.8);
        state = State.HOP;
        leftGround = false;
        grounded = false;
    }

    /** Pays out the chest: the skill point and a shower of gold. */
    @Override
    public void onDeath() {
        player.getComponent(PlayerStatsComponent.class).addSkillPoint();
        Sfx.CHEST_OPEN.play();
        Particles.burst(entity.getCenter(), GOLD, 22, -90, 50, 200, 480, 0.9, 5, 700);
    }

    @Override
    protected void interrupt() {
        if (state == State.DORMANT || state == State.WAKE) return;
        sprite.setTelegraph(null);
        rest(0.2);
    }

    @Override
    protected void disabledPose() {
        if (state == State.DORMANT) sprite.show("dormant", 0);
        else sprite.show("hop", 0);
    }
}
