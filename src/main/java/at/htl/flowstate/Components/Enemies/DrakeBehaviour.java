package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Assets;
import at.htl.flowstate.Combat.Combat;
import at.htl.flowstate.Components.Player.Helpers.DeleteAfterTimeComponent;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Visuals.FrameSprite;
import at.htl.flowstate.Visuals.Frames;
import at.htl.flowstate.Visuals.Impact;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.core.math.FXGLMath;
import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.dsl.components.OffscreenCleanComponent;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.physics.BoundingShape;
import com.almasb.fxgl.physics.HitBox;
import javafx.geometry.Point2D;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;

/**
 * Ash Drake: a bomber. It flies high over you in passes, back and forth, and drops fire bombs timed to land where
 * you are; each leaves a pool of fire. It never comes down by itself: shoot it (bows, crossbows, tridents, spells)
 * or freeze / stun it, and it sinks within reach. After a few passes it climbs away. Kinematic body.
 */
public class DrakeBehaviour extends DemonBehaviour {
    private static final double ALTITUDE = 400;          // above the player's centre ...
    private static final double MIN_CLEARANCE = 300;     // ... and at least this far above the ground below
    private static final double PASS_REACH = 560;        // how far past the player a pass goes before it turns
    private static final double MAX_SPEED = 290;
    private static final double ACCEL = 1.6;
    private static final int PASSES = 5;
    private static final double CONTACT_DAMAGE = 8;
    private static final double BOMB_DAMAGE = 12;
    private static final double BOMB_VY = 40;            // dropped, not thrown: a little downward push
    private static final double BOMB_CARRY = 0.6;         // of the drake's speed
    private static final double BOMB_GRAVITY = 800;       // BallisticProjectileComponent
    private static final double BOMB_COOLDOWN = 0.55;
    private static final double AIM_SLACK = 110;          // drop when the bomb would land this close to the player
    private static final double DROP_TIME = 0.45, DROP_RELEASE = 0.15;
    private static final double POOL_WIDTH = 76, POOL_TIME = 2.2;
    private static final double SINK_SPEED = 150;
    private static final double LEAVE_SPEED = 380;

    private enum State { PASS, DROP, LEAVE }

    private State state = State.PASS;
    private int passDir = FXGLMath.randomBoolean() ? 1 : -1;
    private int passes;
    private double timer;
    private double bombCooldown = 1.0;
    private boolean released;
    private double vx, vy;
    private double time = FXGLMath.random(0, 10);
    private double screechTimer = FXGLMath.random(0.5, 2.0);
    /** Its own flight lane, so two drakes don't fly on top of each other. */
    private final double lane = FXGLMath.random(-80, 60), reach = PASS_REACH * FXGLMath.random(0.85, 1.15);

    @Override
    protected void onReady() {
        // the first pass comes in from the side it spawned on
        passDir = entity.getCenter().getX() < player.getCenter().getX() ? 1 : -1;
    }

    @Override
    protected void think(double tpf) {
        time += tpf;
        double t = dt(tpf);
        bombCooldown -= t;
        if ((screechTimer -= tpf) <= 0 && state != State.LEAVE) {
            screechTimer = FXGLMath.random(4, 7);
            Sfx.DRAKE_SCREECH.at(entity.getCenter(), 0.8, FXGLMath.random(0.9, 1.1));
        }
        switch (state) {
            case PASS -> {
                fly(tpf);
                sprite.loop("fly", 10);
                if (bombCooldown <= 0 && onTarget()) {
                    state = State.DROP;
                    timer = 0;
                    released = false;
                    sprite.setTelegraph(TELL_RANGED);
                }
            }
            case DROP -> {
                fly(tpf);
                timer += t;
                sprite.show("drop", timer < DROP_RELEASE ? 0 : timer < DROP_TIME * 0.75 ? 1 : 2);
                if (!released && timer >= DROP_RELEASE) {
                    released = true;
                    sprite.setTelegraph(null);
                    dropBomb();
                }
                if (timer >= DROP_TIME) {
                    state = State.PASS;
                    bombCooldown = BOMB_COOLDOWN;
                }
            }
            case LEAVE -> {
                steer(passDir * LEAVE_SPEED, -LEAVE_SPEED * 0.6, tpf);
                sprite.loop("fly", 13);
                double top = FXGL.getGameScene().getViewport().getY();
                if (entity.getBottomY() < top - 300) entity.removeFromWorld();
            }
        }
        if (!entity.isActive()) return;
        touch(CONTACT_DAMAGE);
        apply();
        sprite.setFacing(vx >= 0 ? 1 : -1);
    }

    /** Steers toward the end of the current pass, high above the player; turns around once it's there. */
    private void fly(double tpf) {
        Point2D p = player.getCenter();
        double targetX = p.getX() + passDir * reach;
        double targetY = p.getY() - ALTITUDE + lane + Math.sin(time * 1.3) * 25;
        double ground = Combat.groundYBelow(entity.getCenter().getX(), p.getY() - 200);
        if (!Double.isNaN(ground)) targetY = Math.min(targetY, ground - MIN_CLEARANCE);
        double dx = targetX - entity.getCenter().getX();
        double dy = targetY - entity.getCenter().getY();
        if (dx * passDir < 40) {                           // reached the far end: turn for the next pass
            passDir = -passDir;
            if (++passes >= PASSES) {
                state = State.LEAVE;
                sprite.setTelegraph(null);
                return;
            }
        }
        double speed = MAX_SPEED * stats.getSpeedMult();
        double wantX = Math.max(-speed, Math.min(speed, dx * 1.5));
        double wantY = Math.max(-speed * 0.6, Math.min(speed * 0.6, dy * 2));
        steer(wantX, wantY, tpf);
    }

    /** Where a bomb dropped now would land (roughly, on the player's height) vs. the player. */
    private boolean onTarget() {
        Point2D c = bombStart();
        double fallY = player.getCenter().getY() - c.getY();
        if (fallY < 100) return false;
        double vy0 = BOMB_VY;
        double tFall = (-vy0 + Math.sqrt(vy0 * vy0 + 2 * BOMB_GRAVITY * fallY)) / BOMB_GRAVITY;
        double landX = c.getX() + vx * BOMB_CARRY * tFall;
        return Math.abs(landX - player.getCenter().getX()) < AIM_SLACK;
    }

    private Point2D bombStart() {
        return entity.getCenter().add(sprite.getFacing() * 6, 22);
    }

    private void dropBomb() {
        Point2D from = bombStart();
        Frames frames = Assets.frames("DrakeBomb.png", 4, 2);
        FrameSprite view = new FrameSprite(frames).loop(frames, 10);
        view.setTranslateX(8 - frames.width() / 2.0);
        view.setTranslateY(8 - frames.height() / 2.0);
        entityBuilder()
                .at(from.subtract(8, 8))
                .view(view)
                .bbox(new HitBox(BoundingShape.box(16, 16)))
                .with(new BallisticProjectileComponent(vx * BOMB_CARRY, BOMB_VY))
                .with(new EnemyProjectileComponent(BOMB_DAMAGE, DrakeBehaviour::burst).owner(entity))
                .with(new DeleteAfterTimeComponent(8))
                .with(new OffscreenCleanComponent())
                .zIndex(12)
                .buildAndAttach();
        Sfx.BOMB_DROP.at(from, 0.8, FXGLMath.random(0.95, 1.1));
        Particles.of(Particles.EMBER).count(6).angle(90, 50).speed(40, 120).life(0.4).size(4).gravity(300).at(from);
    }

    private static void burst(Entity bomb) {
        Point2D c = bomb.getCenter();
        Sfx.EXPLOSION.at(c, 0.55, 1.25);
        Impact.shake(4);
        Particles.of(Particles.EMBER).count(18).angle(-90, 80).speed(120, 380).life(0.6).size(6).gravity(700).at(c);
        Particles.of(Particles.DUST).count(8).angle(-90, 70).speed(60, 180).life(0.7).size(7).gravity(200).at(c);
        MagmaPoolComponent.spawn(c.getX(), c.getY() - 10, POOL_WIDTH, POOL_TIME);
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

    /** Stunned or frozen it sinks toward the ground, within reach of a sword; knocked back it drifts. */
    @Override
    protected void whileDisabled(double tpf) {
        if (stats.isKnockedBack()) {
            physics.setVelocityY(physics.getVelocityY() * Math.max(0, 1 - tpf * 6));
            vx = physics.getVelocityX();
            vy = physics.getVelocityY();
            return;
        }
        double ground = Combat.groundYBelow(entity.getCenter().getX(), entity.getY());
        boolean low = !Double.isNaN(ground) && entity.getBottomY() > ground - 30;
        vx = 0;
        vy = low ? 0 : SINK_SPEED;
        apply();
        sprite.loop("fly", 4);
    }

    @Override
    protected void interrupt() {
        if (state == State.DROP) state = State.PASS;
        bombCooldown = Math.max(bombCooldown, 0.8);
    }
}
