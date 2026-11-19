package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Combat.Combat;
import at.htl.flowstate.Sfx;
import com.almasb.fxgl.core.math.FXGLMath;
import com.almasb.fxgl.entity.Entity;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;

/**
 * Void Caller: drifts at a distance and calls up a gravity well where you stand, which drags you toward its biting
 * core while the other demons close in. The cast is long and glows: freeze or stun it to stop it, or dash out of the
 * well once it opens. Void: shrugs off knockback, fire hurts it more.
 */
public class VoidCallerBehaviour extends GroundDemonBehaviour {
    private static final double SPEED = 95;
    private static final double TOO_CLOSE = 280, TOO_FAR = 620, MAX_CAST = 800;
    private static final double CONTACT_DAMAGE = 5;
    private static final double CAST_TIME = 1.15, RELEASE_HOLD = 0.4;
    private static final double WELL_TIME = 3.6;
    private static final Color TELL_VOID = Color.rgb(120, 130, 255, 0.95);

    private boolean casting, released;
    private double timer;
    private double cooldown = FXGLMath.random(1.2, 2.4);
    private Entity well;

    @Override
    protected void act(double tpf) {
        double t = dt(tpf);
        if (well != null && !well.isActive()) well = null;
        if (well == null) cooldown -= t;
        if (casting) {
            castStep(t);
            return;
        }
        double dx = dxToPlayer();
        double adx = Math.abs(dx);
        int toward = dx >= 0 ? 1 : -1;
        if (adx < TOO_CLOSE) walk(-toward, SPEED);
        else if (adx > TOO_FAR) walk(toward, SPEED * 1.2);
        else stop();
        if (adx <= TOO_FAR) face(toward);
        touch(CONTACT_DAMAGE);
        locomotion("idle", "walk", 8, "air");

        if (grounded && cooldown <= 0 && well == null && adx < MAX_CAST && Math.abs(dyToPlayer()) < 300) {
            casting = true;
            released = false;
            timer = 0;
            face(toward);
            sprite.setTelegraph(TELL_VOID);
            Sfx.VOID_CAST.at(entity.getCenter());
        }
    }

    private void castStep(double t) {
        stop();
        face(dirToPlayer());
        timer += t;
        if (timer < CAST_TIME) {
            sprite.show("cast", Math.min(4, (int) (timer / CAST_TIME * 5)));
            return;
        }
        if (!released) {
            released = true;
            sprite.setTelegraph(null);
            Point2D at = player.getCenter();
            // hang it just above the floor under the player, so it pulls along the ground
            double ground = Combat.groundYBelow(at.getX(), player.getY());
            if (!Double.isNaN(ground)) at = new Point2D(at.getX(), Math.min(at.getY(), ground - 70));
            well = GravityWellComponent.spawn(at, WELL_TIME, entity);
        }
        sprite.show("cast", 5);
        if (timer >= CAST_TIME + RELEASE_HOLD) {
            casting = false;
            cooldown = FXGLMath.random(2.5, 3.5);       // counted from when the well is gone
        }
    }

    @Override
    protected void interrupt() {
        if (casting && !released) Sfx.SHIELD_BREAK.at(entity.getCenter(), 0.4, 1.6);   // the cast fizzles
        casting = false;
        cooldown = Math.max(cooldown, 1.2);
    }

    @Override
    public void onDeath() {
        if (well != null && well.isActive()) well.removeFromWorld();
    }

    @Override
    protected void disabledPose() {
        sprite.loop("idle", 4);
    }
}
