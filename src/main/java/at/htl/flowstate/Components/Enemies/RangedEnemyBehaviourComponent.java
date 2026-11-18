package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Components.Player.Helpers.DeleteAfterTimeComponent;
import at.htl.flowstate.Assets;
import com.almasb.fxgl.dsl.components.OffscreenCleanComponent;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.SpawnData;
import com.almasb.fxgl.physics.BoundingShape;
import com.almasb.fxgl.physics.HitBox;
import com.almasb.fxgl.physics.PhysicsComponent;
import com.almasb.fxgl.physics.box2d.dynamics.BodyType;
import com.almasb.fxgl.physics.box2d.dynamics.FixtureDef;
import at.htl.flowstate.Components.SpriteComponents.SpriteComponent;
import javafx.scene.image.ImageView;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;


import static com.almasb.fxgl.dsl.FXGL.entityBuilder;
import static com.almasb.fxgl.dsl.FXGL.text;

public class RangedEnemyBehaviourComponent extends EnemyBehaviourComponent {
    private static final double ATTACK_INTERVAL = 2.0;
    private static final double PROJECTILE_SPEED = 800;
    private static final double GRAVITY = 800; //must match Game.java initPhysics

    private static final double DAMAGE = 20;

    private boolean inAttackMode = false;
    private double attackCooldown = 0;

    @Override
    public void onUpdate(double tpf) {
        if (attackCooldown > 0 && stats != null) attackCooldown -= tpf * stats.getSpeedMult();
        super.onUpdate(tpf);
    }

    @Override
    protected void chasePlayer() {
        double dx = player.getX() - entity.getX();
        int direction;
        if (dx < 500 && dx > -500) {
            inAttackMode = true;
            direction = 0;
            lastMoveDirection = dx >= 0 ? 1 : -1; // face the player while standing still
        } else {
            inAttackMode = false;
            direction = dx > 0 ? 1 : -1;
            lastMoveDirection = direction;
        }
        physics.setVelocityX(moveSpeed() * direction);
        if (isGrounded) {
            tryStep(direction);
            tryJump(direction);
        }
    }

    @Override
    public void attack() {
        if (!inAttackMode || attackCooldown > 0) return;

        Point2D vel = calculateBallisticVelocity();
        if (vel == null) return;

        double spawnX = entity.getCenter().getX() + Math.signum(vel.getX()) * 30;
        double spawnY = entity.getCenter().getY();

        // spear drawn pointing right, centred on the 14x4 hitbox (rotation origin)
        ImageView spear = Assets.sprite("Spear.png", 2);
        spear.setRotate(45);
        spear.setTranslateX(7 - spear.getImage().getWidth() / 2);
        spear.setTranslateY(2 - spear.getImage().getHeight() / 2);
        entity.getComponent(SpriteComponent.class).playAttack();

        entityBuilder()
                .from(new SpawnData(spawnX, spawnY))
                .view(spear)
                .bbox(BoundingShape.box(14, 4))
                .with(new BallisticProjectileComponent(vel.getX(), vel.getY()))
                .with(new EnemyProjectileComponent(DAMAGE))
                .with(new DeleteAfterTimeComponent(10))
                .with(new OffscreenCleanComponent())
                .buildAndAttach();

        attackCooldown = ATTACK_INTERVAL;
    }

    //DO NOT TOUCH THIS UNLESS YOU KNOW EXACTLY HOW MATH MATHING
    private Point2D calculateBallisticVelocity() {
        double dx = player.getCenter().getX() - entity.getCenter().getX();
        double dy = player.getCenter().getY() - entity.getCenter().getY();
        double s = PROJECTILE_SPEED;
        double g = GRAVITY;

        double a = 0.25 * g * g;
        double b = -(s * s + g * dy);
        double c = dx * dx + dy * dy;

        double discriminant = b * b - 4 * a * c;
        if (discriminant < 0) return null;

        double sqrt = Math.sqrt(discriminant);
        double u1 = (-b + sqrt) / (2 * a);
        double u2 = (-b - sqrt) / (2 * a);

        double u;
        if (u1 > 0 && u2 > 0) u = Math.min(u1, u2);
        else if (u1 > 0) u = u1;
        else if (u2 > 0) u = u2;
        else return null;

        double T = Math.sqrt(u);
        double vx = dx / T;
        double vy = (dy - 0.5 * g * T * T) / T;

        return new Point2D(vx, vy);
    }
}