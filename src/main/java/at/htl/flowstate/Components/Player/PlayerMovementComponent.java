package at.htl.flowstate.Components.Player;

import at.htl.flowstate.Combat.Combat;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Components.SpriteComponents.SpriteComponent;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import com.almasb.fxgl.physics.PhysicsComponent;
import javafx.geometry.Point2D;

public class PlayerMovementComponent extends Component {
    private PhysicsComponent physics;

    private static final double JUMP_FORCE = 600.0;
    private static final double START_MOVE_SPEED_PCT = 60.0;
    private static final double MAX_MOVE_SPEED = 400;
    private static final double MAX_SPEED_FRAMES = 80;
    private static final double MAX_SPEED_THRESHOLD = 20;
    private static final int COYOTE_FRAMES = 5;
    private static final double LEVITATION_SPEED = 300.0;
    private static final double SLOW_FALL_SPEED = 120.0;
    private static final double DASH_SPEED = 750.0;   // 150 px in 0.2 s
    private static final double DASH_TIME = 0.2;
    private static final double SLAM_SPEED = 900.0;
    private static final double POGO_FORCE = 560.0;
    private static final double RUN_FRAMES = 50;          // running frames before the walk turns into a run
    private static final double WALK_STEP = 0.32;         // seconds between footstep squelches
    private static final double RUN_STEP = 0.22;


    private static final double PLAYER_WIDTH = 40.0;
    private static final double PLAYER_HEIGHT = 80.0;
    private static final double STEP_HEIGHT = 20.0;
    private static final double STEP_LOOK_AHEAD = 12.0;
    private static final double STEP_FORWARD_NUDGE = STEP_LOOK_AHEAD + 2.0;

    private boolean isGrounded = false;
    private boolean wasGrounded = false;
    private boolean jumpConsumed = false;
    private int coyoteTimer = 0;
    private double currentRunningFrames = 0;
    private int lastMoveDirection = 0;
    private int currentWatchDirection = 1;

    private boolean isLevitationActive = false;
    private boolean isLevitatingUp = false;
    private boolean isLevitatingDown = false;

    private double dashLeft = 0;
    private int dashDirection = 1;
    private double dashSpeed = DASH_SPEED;
    private boolean dodging = false;
    private double facingLock = 0;
    private boolean diving = false;                        // hammer slam fall
    private boolean dead = false;
    private double stepTimer = 0;

    @Override
    public void onAdded() {
        physics = entity.getComponent(PhysicsComponent.class);
    }

    @Override
    public void onUpdate(double tpf) {
        LoadoutComponent loadout = entity.getComponent(LoadoutComponent.class);
        isLevitationActive = loadout.isLevitating();

        wasGrounded = isGrounded;
        if (facingLock > 0) facingLock -= tpf;

        if (dashLeft > 0) {
            dashLeft -= tpf;
            physics.setVelocityX(dashDirection * dashSpeed);
            if (dashLeft <= 0) {
                dodging = false;
                if (lastMoveDirection == 0) physics.setVelocityX(0);
            }
        }

        if (isLevitationActive) {
            if (isLevitatingUp) {
                physics.setVelocityY(-LEVITATION_SPEED);
            } else if (isLevitatingDown) {
                physics.setVelocityY(LEVITATION_SPEED);
            } else {
                physics.setVelocityY(2);
            }
            isGrounded = false;
        } else {
            updateGroundState();
            if (loadout.isSlowFalling() && physics.getVelocityY() > SLOW_FALL_SPEED) {
                physics.setVelocityY(SLOW_FALL_SPEED);
            }
            if (isGrounded && lastMoveDirection != 0 && dashLeft <= 0) {
                tryStep(lastMoveDirection);
            }
        }

        keepOnScreen();
        updateSprite();
        updateSteps(tpf);
    }

    private void updateSteps(double tpf) {
        if (!isGrounded || lastMoveDirection == 0 || dashLeft > 0 || dead || isLevitationActive) {
            stepTimer = 0.08;   // the first step lands shortly after starting to move
            return;
        }
        stepTimer -= tpf;
        if (stepTimer <= 0) {
            boolean running = currentRunningFrames >= RUN_FRAMES;
            stepTimer = running ? RUN_STEP : WALK_STEP;
            Sfx.STEP.play(running ? 1.2 : 1, 1);
        }
    }

    private void updateSprite() {
        SpriteComponent sprite = entity.getComponent(SpriteComponent.class);
        LoadoutComponent loadout = entity.getComponent(LoadoutComponent.class);

        sprite.setFacing(currentWatchDirection);
        if (!wasGrounded && isGrounded) {
            if (diving) {
                sprite.playSlam();   // the hammer plays the slam's boom
            } else {
                sprite.playLand();
                Sfx.LAND.play();
            }
            diving = false;
            Particles.landingDust(new Point2D(entity.getCenter().getX(), entity.getY() + PLAYER_HEIGHT));
        }
        if (isLevitationActive) diving = false;

        double charge = loadout.getChargeProgress();
        if (dodging) {
            sprite.setState(SpriteComponent.State.DASH, 0);
        } else if (isLevitationActive) {
            sprite.setState(SpriteComponent.State.LEVITATE, 0);
        } else if (!isGrounded) {
            if (diving) {
                sprite.setState(SpriteComponent.State.DIVE, 0);
            } else if (loadout.isSlowFalling() && physics.getVelocityY() >= SLOW_FALL_SPEED - 1) {
                sprite.setState(SpriteComponent.State.GLIDE, 0);
            } else {
                sprite.setAirVelocity(physics.getVelocityX(), physics.getVelocityY());
            }
        } else if (loadout.isBlocking()) {
            sprite.setState(SpriteComponent.State.BLOCK, 0);
        } else if (lastMoveDirection != 0) {
            sprite.setState(currentRunningFrames >= RUN_FRAMES ? SpriteComponent.State.RUN : SpriteComponent.State.WALK, 0);
        } else if (charge >= 0) {
            sprite.setState(SpriteComponent.State.CHARGE, charge);
        } else {
            sprite.setState(SpriteComponent.State.IDLE, 0);
        }
    }

    /** Death: input stops moving the player, who just falls and melts. */
    public void die() {
        dead = true;
        lastMoveDirection = 0;
        isLevitatingUp = false;
        isLevitatingDown = false;
        dashLeft = 0;
        dodging = false;
        physics.setVelocityX(0);
    }

    private void tryStep(int direction) {
        double feetY = entity.getY() + PLAYER_HEIGHT;
        double leadingEdge = direction > 0 ? entity.getX() + PLAYER_WIDTH : entity.getX();

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
            double newX = entity.getX() + direction * STEP_FORWARD_NUDGE;
            double newY = platTop - PLAYER_HEIGHT;
            physics.overwritePosition(new Point2D(newX, newY));
            physics.setVelocityX(savedVelX);
            physics.setVelocityY(0);
            return;
        }
    }

    private void keepOnScreen() {
        double viewX = FXGL.getGameScene().getViewport().getX();
        if (entity.getX() < viewX) {
            entity.setX(viewX);
            if (physics.getVelocityX() < 0) {
                physics.setVelocityX(0);
            }
        }
    }

    public void startMoveRight() {
        if (dead) return;
        lastMoveDirection = 1;
        move(1);
        if (facingLock <= 0) currentWatchDirection = 1;
    }

    public void startMoveLeft() {
        if (dead) return;
        lastMoveDirection = -1;
        move(-1);
        if (facingLock <= 0) currentWatchDirection = -1;
    }

    public void stopHorizontalMovement() {
        lastMoveDirection = 0;
        currentRunningFrames = 0;
        if (dashLeft <= 0) physics.setVelocityX(0);
    }

    private void move(int direction) {
        if (dashLeft > 0) return;
        double speedMult = entity.getComponent(LoadoutComponent.class).getMoveSpeedMult();
        double speed = (MAX_MOVE_SPEED * speedMult / 100.0)
                * (START_MOVE_SPEED_PCT + ((100.0 - START_MOVE_SPEED_PCT) / 100.0) * (currentRunningFrames / MAX_SPEED_FRAMES) * 100.0);

        if (isGrounded) {
            if (currentRunningFrames < MAX_SPEED_FRAMES) currentRunningFrames++;
        } else {
            if (currentRunningFrames > MAX_SPEED_THRESHOLD) currentRunningFrames -= 0.5;
        }

        physics.setVelocityX(speed * direction);
    }

    public void startJump() {
        if (dead) return;
        if (isLevitationActive) {
            isLevitatingUp = true;
        } else {
            if ((isGrounded || coyoteTimer > 0) && !jumpConsumed) {
                physics.setVelocityY(-JUMP_FORCE);
                entity.getComponent(SpriteComponent.class).playTakeoff();
                Sfx.JUMP.play();
                jumpConsumed = true;
                isGrounded = false;
                coyoteTimer = 0;
            }
        }
    }

    public void stopJump() {
        isLevitatingUp = false;
        if (!isLevitationActive && physics.getVelocityY() < 0) {
            physics.setVelocityY(physics.getVelocityY() * 0.45);
        }
    }

    public void startDown() {
        isLevitatingDown = true;
    }

    public void stopDown() {
        isLevitatingDown = false;
    }

    /** Leather Gloves dodge: short dash in the movement (or facing) direction. */
    public void startDash() {
        if (dead) return;
        dashDirection = lastMoveDirection != 0 ? lastMoveDirection : currentWatchDirection;
        dashLeft = DASH_TIME;
        dashSpeed = DASH_SPEED;
        dodging = true;
        Sfx.DASH.play();
    }

    /** Turns the player towards an attack and keeps them facing it for {@code seconds}. */
    public void faceAttack(int direction, double seconds) {
        if (direction != 1 && direction != -1) return;
        currentWatchDirection = direction;
        facingLock = Math.max(facingLock, seconds);
    }

    /** Small step forward that sells the weight of a melee swing. Only on the ground, never over a dodge. */
    public void lunge(int direction, double speed, double seconds) {
        if (dodging || !isGrounded || isLevitationActive) return;
        dashDirection = direction;
        dashSpeed = speed;
        dashLeft = seconds;
    }

    /** Bounce up after a downward strike connects (pogo). */
    public void pogo() {
        physics.setVelocityY(-POGO_FORCE);
        Sfx.JUMP.play(1, 1.25);
        diving = false;
        entity.getComponent(SpriteComponent.class).playTakeoff();
        isGrounded = false;
        jumpConsumed = true;
    }

    /** Hammer ground slam: fall fast. */
    public void startSlam() {
        physics.setVelocityY(SLAM_SPEED);
        Sfx.SLAM_DIVE.play();
        diving = true;
        isGrounded = false;
    }

    private void updateGroundState() {
        boolean wasGroundedLocal = isGrounded;
        isGrounded = Math.abs(physics.getVelocityY()) < 0.1;

        if (wasGroundedLocal && !isGrounded) {
            coyoteTimer = COYOTE_FRAMES;
        } else if (isGrounded) {
            coyoteTimer = 0;
            jumpConsumed = false;
        } else if (coyoteTimer > 0) {
            coyoteTimer--;
        }
    }

    public boolean isGrounded() { return isGrounded; }

    /** S is held (down-slash in the air, fly down while levitating). */
    public boolean isDownHeld() { return isLevitatingDown; }

    public int getCurrentWatchDirection() { return currentWatchDirection; }
}
