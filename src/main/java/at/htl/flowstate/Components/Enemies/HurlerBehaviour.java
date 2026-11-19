package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Assets;
import at.htl.flowstate.Components.Player.Helpers.DeleteAfterTimeComponent;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Visuals.FrameSprite;
import at.htl.flowstate.Visuals.Frames;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.core.math.FXGLMath;
import com.almasb.fxgl.dsl.components.OffscreenCleanComponent;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.physics.BoundingShape;
import com.almasb.fxgl.physics.HitBox;
import javafx.geometry.Point2D;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;

/**
 * Brimstone Hurler: keeps its distance (backs off when you come close) and lobs balls of magma that leave
 * a burning pool where they land. Close the gap; the pools can block a path for a moment.
 */
public class HurlerBehaviour extends GroundDemonBehaviour {
    private static final double SPEED = 150;
    private static final double CONTACT_DAMAGE = 5;
    private static final double BALL_DAMAGE = 14;
    private static final double BALL_SPEED = 720;
    private static final double TOO_CLOSE = 330, TOO_FAR = 650, MAX_THROW = 850;
    private static final double POOL_WIDTH = 84, POOL_TIME = 2.4;
    // throw timeline (s): reach back, hold, release, follow through
    private static final double T_BACK = 0.2, T_RELEASE = 0.5, T_FOLLOW = 0.68, T_END = 1.1;

    private boolean throwing;
    private boolean released;
    private double timer;
    private double cooldown = FXGLMath.random(0.6, 1.6);

    @Override
    protected void act(double tpf) {
        double t = dt(tpf);
        cooldown -= t;
        if (throwing) {
            throwStep(t);
            return;
        }
        double dx = dxToPlayer();
        double adx = Math.abs(dx);
        int toward = dx >= 0 ? 1 : -1;
        if (adx < TOO_CLOSE) {
            walk(-toward, SPEED);
        } else if (adx > TOO_FAR) {
            walk(toward, SPEED * 1.15);
        } else {
            stop();
        }
        if (adx <= TOO_FAR) face(toward);    // backs away facing you
        touch(CONTACT_DAMAGE);
        locomotion("idle", "walk", 11, "air");

        if (grounded && cooldown <= 0 && adx < MAX_THROW && adx > 120) {
            throwing = true;
            released = false;
            timer = 0;
            face(toward);
            sprite.setTelegraph(TELL_RANGED);
        }
    }

    private void throwStep(double t) {
        stop();
        timer += t;
        if (timer < T_BACK) sprite.show("throw", 0);
        else if (timer < T_RELEASE) sprite.show("throw", 1);
        else if (timer < T_FOLLOW) sprite.show("throw", 2);
        else if (timer < T_END - 0.2) sprite.show("throw", 3);
        else sprite.show("throw", 4);
        if (!released && timer >= T_RELEASE) {
            released = true;
            sprite.setTelegraph(null);
            launch();
        }
        if (timer >= T_END) {
            throwing = false;
            cooldown = FXGLMath.random(2.2, 3.0);
        }
    }

    private void launch() {
        Point2D from = entity.getCenter().add(facing * 14, -30);
        Point2D target = player.getCenter().add(FXGLMath.random(-20, 20), 10);
        Point2D vel = Ballistics.velocity(from, target, BALL_SPEED, Ballistics.GRAVITY);
        if (vel == null) return;
        Sfx.ENEMY_THROW.at(entity.getCenter(), 1, 0.8);

        Frames frames = Assets.frames("MagmaBall.png", 4, 2);
        FrameSprite view = new FrameSprite(frames).loop(frames, 12);
        view.setTranslateX(7 - frames.width() / 2);
        view.setTranslateY(7 - frames.height() / 2);
        entityBuilder()
                .at(from.subtract(7, 7))
                .view(view)
                .bbox(new HitBox(BoundingShape.box(14, 14)))
                .with(new BallisticProjectileComponent(vel.getX(), vel.getY()))
                .with(new EnemyProjectileComponent(BALL_DAMAGE, HurlerBehaviour::splash))
                .with(new DeleteAfterTimeComponent(8))
                .with(new OffscreenCleanComponent())
                .zIndex(12)
                .buildAndAttach();
    }

    private static void splash(Entity ball) {
        Point2D c = ball.getCenter();
        Sfx.MAGMA_SPLASH.at(c);
        Particles.burst(c, Particles.EMBER, 14, -90, 70, 120, 340, 0.6, 5, 700);
        MagmaPoolComponent.spawn(c.getX(), c.getY() - 10, POOL_WIDTH, POOL_TIME);
    }

    @Override
    protected void interrupt() {
        throwing = false;
        cooldown = Math.max(cooldown, 0.8);
    }

    @Override
    protected void disabledPose() {
        sprite.loop("idle", 6);
    }
}
