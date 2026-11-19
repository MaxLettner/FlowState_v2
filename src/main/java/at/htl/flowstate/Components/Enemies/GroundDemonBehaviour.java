package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Combat.Combat;
import at.htl.flowstate.Sfx;
import com.almasb.fxgl.core.math.FXGLMath;
import com.almasb.fxgl.entity.Entity;
import javafx.geometry.Point2D;

/** A demon that walks: steps up small ledges and jumps over higher ones on its way to the player. */
public abstract class GroundDemonBehaviour extends DemonBehaviour {
    private static final double STEP_HEIGHT = 20.0;
    private static final double STEP_LOOK_AHEAD = 12.0;
    private static final double STEP_FORWARD_NUDGE = STEP_LOOK_AHEAD + 2.0;
    private static final double JUMP_LOOK_AHEAD = 30.0;
    private static final double MAX_JUMP_HEIGHT = 300.0;
    private static final double JUMP_FORCE = 600.0;

    /** Every demon walks a little faster or slower than its kin, so packs spread out. */
    protected final double jitter = FXGLMath.random(0.9, 1.1);

    protected boolean grounded, wasGrounded;
    private boolean jumpConsumed;
    protected int facing = 1;

    @Override
    public final void think(double tpf) {
        updateGround();
        act(tpf);
        sprite.setFacing(facing);
    }

    @Override
    protected void whileDisabled(double tpf) {
        updateGround();
        if (!stats.isKnockedBack()) physics.setVelocityX(0);
        disabledPose();
    }

    /** The demon's AI, with {@link #grounded} up to date. */
    protected abstract void act(double tpf);

    /** What the sprite shows while stunned or frozen (the frozen frame is held anyway). */
    protected void disabledPose() {}

    private void updateGround() {
        wasGrounded = grounded;
        grounded = Math.abs(physics.getVelocityY()) < 0.1;
        if (grounded) jumpConsumed = false;
    }

    protected boolean justLanded() {
        return grounded && !wasGrounded;
    }

    /** Walks in a direction (-1, 0, 1) at a speed, stepping and jumping over terrain in the way. */
    protected void walk(int direction, double speed) {
        if (direction != 0) facing = direction;
        physics.setVelocityX(speed * jitter * stats.getSpeedMult() * direction);
        if (grounded && direction != 0) {
            tryStep(direction);
            tryJump(direction);
        }
    }

    protected void stop() {
        physics.setVelocityX(0);
    }

    protected void face(int direction) {
        if (direction != 0) facing = direction;
    }

    private void tryStep(int direction) {
        double feetY = entity.getY() + entity.getHeight();
        double leadingEdge = direction > 0 ? entity.getX() + entity.getWidth() : entity.getX();

        for (Entity platform : Combat.platforms()) {
            double platTop = platform.getY();
            double platLeft = platform.getX();
            double platRight = platform.getX() + platform.getWidth();

            boolean adjacent = direction > 0
                    ? platLeft >= leadingEdge - 2.0 && platLeft <= leadingEdge + STEP_LOOK_AHEAD
                    : platRight <= leadingEdge + 2.0 && platRight >= leadingEdge - STEP_LOOK_AHEAD;
            if (!adjacent) continue;

            double stepSize = feetY - platTop;
            if (stepSize < 1.0 || stepSize > STEP_HEIGHT) continue;

            double savedVelX = physics.getVelocityX();
            physics.overwritePosition(new Point2D(entity.getX() + direction * STEP_FORWARD_NUDGE, platTop - entity.getHeight()));
            physics.setVelocityX(savedVelX);
            physics.setVelocityY(0);
            return;
        }
    }

    private void tryJump(int direction) {
        if (jumpConsumed) return;
        double feetY = entity.getY() + entity.getHeight();
        double leadingEdge = direction > 0 ? entity.getX() + entity.getWidth() : entity.getX();

        for (Entity platform : Combat.platforms()) {
            double platTop = platform.getY();
            double platLeft = platform.getX();
            double platRight = platform.getX() + platform.getWidth();

            boolean ahead = direction > 0
                    ? platLeft >= leadingEdge - 2.0 && platLeft <= leadingEdge + JUMP_LOOK_AHEAD
                    : platRight <= leadingEdge + 2.0 && platRight >= leadingEdge - JUMP_LOOK_AHEAD;
            if (!ahead) continue;

            double obstacleHeight = feetY - platTop;
            if (obstacleHeight <= STEP_HEIGHT || obstacleHeight > MAX_JUMP_HEIGHT) continue;

            physics.setVelocityY(-JUMP_FORCE * jitter);
            Sfx.ENEMY_JUMP.at(entity.getCenter());
            jumpConsumed = true;
            grounded = false;
            return;
        }
    }

    /** Idle / walk / air from the movement, for demons in their neutral state. */
    protected void locomotion(String idle, String walk, double walkFps, String air) {
        if (!grounded) {
            sprite.loop(air, 6);
        } else if (Math.abs(physics.getVelocityX()) > 1.0) {
            sprite.loop(walk, walkFps);
        } else {
            sprite.loop(idle, 7);
        }
    }
}
