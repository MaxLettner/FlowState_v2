package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Combat.Combat;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Visuals.Impact;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.core.math.FXGLMath;
import com.almasb.fxgl.entity.Entity;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.StrokeLineCap;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;

/**
 * Watcher: a floating eye that keeps its distance at about your height. It traces a thin line onto you, the line
 * locks (stops following and flares), then a beam fires along it, stopped by terrain. Step out of the locked line,
 * put terrain between you, or stun it to cancel the shot. Kinematic body.
 */
public class WatcherBehaviour extends DemonBehaviour {
    private static final double KEEP_X = 420, KEEP_Y = -90;
    private static final double MAX_SPEED = 200;
    private static final double ACCEL = 2.5;
    private static final double AIM_TIME = 1.2, LOCK_TIME = 0.35, BEAM_TIME = 0.25;
    private static final double BEAM_DAMAGE = 18;
    private static final double MAX_RANGE = 1300;
    private static final double RAY_STEP = 6;
    private static final double CONTACT_DAMAGE = 4;
    private static final Color AIM = Color.rgb(255, 90, 60, 0.45);
    private static final Color LOCKED = Color.rgb(255, 220, 120, 0.9);
    private static final Color[] BEAM_SPARKS = {Color.WHITE, Color.web("#fff3a0"), Color.web("#ff8a2a")};

    private enum State { HOVER, AIM, LOCK, FIRE }

    private State state = State.HOVER;
    private double timer;
    private double cooldown = FXGLMath.random(1.0, 2.0);
    private double vx, vy;
    private double time = FXGLMath.random(0, 10);
    private final double side = FXGLMath.randomBoolean() ? 1 : -1;
    private Point2D aimDir = new Point2D(1, 0);
    private boolean beamHit;

    private Entity lineEntity;
    private Line line;
    private Line core;

    @Override
    protected void think(double tpf) {
        time += tpf;
        double t = dt(tpf);
        cooldown -= t;
        switch (state) {
            case HOVER -> {
                hover(tpf);
                look();
                if (cooldown <= 0 && entity.getCenter().distance(player.getCenter()) < 900) {
                    state = State.AIM;
                    timer = AIM_TIME;
                    showLine();
                    Sfx.WATCHER_CHARGE.at(entity.getCenter());
                }
            }
            case AIM -> {
                steer(0, 0, tpf);
                aimDir = toPlayer();
                look();
                updateLine(AIM, 2, false);
                if ((timer -= t) <= 0) {
                    state = State.LOCK;
                    timer = LOCK_TIME;
                    sprite.setTelegraph(Color.rgb(255, 200, 80, 0.9));
                }
            }
            case LOCK -> {
                steer(0, 0, tpf);
                sprite.loop("charge", 14);
                updateLine(LOCKED, 3, false);
                if ((timer -= t) <= 0) fire();
            }
            case FIRE -> {
                steer(0, 0, tpf);
                sprite.loop("charge", 20);
                double len = updateLine(Color.WHITE, 12, true);
                if (!beamHit && beamHitsPlayer(len)) beamHit = hurtPlayer(BEAM_DAMAGE);
                if ((timer -= tpf) <= 0) endAttack();
            }
        }
        if (state == State.HOVER) touch(CONTACT_DAMAGE);
        physics.setVelocityX(vx);
        physics.setVelocityY(vy);
    }

    private void hover(double tpf) {
        double sideNow = entity.getCenter().getX() >= player.getCenter().getX() ? 1 : -1;
        if (Math.abs(dxToPlayer()) > KEEP_X * 1.8) sideNow = side;
        Point2D target = player.getCenter().add(sideNow * KEEP_X + Math.sin(time * 0.9) * 50, KEEP_Y + Math.sin(time * 1.7) * 30);
        double ground = Combat.groundYBelow(target.getX(), player.getY());
        if (!Double.isNaN(ground)) target = new Point2D(target.getX(), Math.min(target.getY(), ground - 90));
        Point2D want = target.subtract(entity.getCenter()).multiply(1.5);
        if (want.magnitude() > MAX_SPEED) want = want.normalize().multiply(MAX_SPEED);
        steer(want.getX() * stats.getSpeedMult(), want.getY() * stats.getSpeedMult(), tpf);
    }

    private Point2D toPlayer() {
        Point2D to = player.getCenter().subtract(eye());
        return to.magnitude() < 1 ? new Point2D(1, 0) : to.normalize();
    }

    private Point2D eye() {
        return entity.getCenter().add(0, -8);
    }

    /** The pupil follows the player: one of 8 look frames, mirrored when the sprite faces left. */
    private void look() {
        Point2D d = toPlayer();
        sprite.setFacing(d.getX() >= 0 ? 1 : -1);
        double a = Math.atan2(d.getY(), Math.abs(d.getX()));
        int frame = Math.floorMod((int) Math.round(a / (Math.PI / 4)), 8);
        sprite.show("look", frame);
    }

    /**
     * The line lives in its own entity that sits at the eye and draws relative to it. (Placed at the world origin
     * it would be removed by the cleanup of entities far behind the camera as soon as the run passes x = 3500.)
     */
    private void showLine() {
        removeLine();
        line = new Line();
        line.setStrokeLineCap(StrokeLineCap.ROUND);
        core = new Line();
        core.setStrokeLineCap(StrokeLineCap.ROUND);
        core.setVisible(false);
        Group g = new Group(line, core);
        lineEntity = entityBuilder().at(eye()).view(g).zIndex(14).buildAndAttach();
    }

    /** Points the line along aimDir up to the first terrain; @return its length */
    private double updateLine(Color color, double width, boolean beam) {
        if (lineEntity == null || !lineEntity.isActive()) showLine();
        Point2D from = eye();
        double len = rayLength(from, aimDir);
        Point2D to = aimDir.multiply(len);                       // relative to the line entity at the eye
        lineEntity.setPosition(from);
        for (Line l : new Line[]{line, core}) {
            l.setStartX(0);
            l.setStartY(0);
            l.setEndX(to.getX());
            l.setEndY(to.getY());
        }
        line.setStroke(beam ? Color.rgb(255, 140, 40, 0.9) : color);
        line.setStrokeWidth(width);
        line.getStrokeDashArray().setAll(beam ? new Double[0] : new Double[]{10.0, 8.0});
        line.setEffect(beam ? new DropShadow(18, Color.rgb(255, 120, 30, 0.9)) : null);
        core.setVisible(beam);
        core.setStroke(color);
        core.setStrokeWidth(width * 0.4);
        return len;
    }

    private static double rayLength(Point2D from, Point2D dir) {
        for (double d = RAY_STEP; d < MAX_RANGE; d += RAY_STEP) {
            Point2D p = from.add(dir.multiply(d));
            for (Entity plat : Combat.platforms()) {
                if (p.getX() >= plat.getX() && p.getX() <= plat.getRightX() && p.getY() >= plat.getY() && p.getY() <= plat.getBottomY()) {
                    return d;
                }
            }
        }
        return MAX_RANGE;
    }

    private boolean beamHitsPlayer(double len) {
        Point2D from = eye();
        double pad = 8;
        for (double d = 0; d <= len; d += RAY_STEP) {
            Point2D p = from.add(aimDir.multiply(d));
            if (p.getX() >= player.getX() - pad && p.getX() <= player.getRightX() + pad
                    && p.getY() >= player.getY() - pad && p.getY() <= player.getBottomY() + pad) return true;
        }
        return false;
    }

    private void fire() {
        state = State.FIRE;
        timer = BEAM_TIME;
        beamHit = false;
        sprite.setTelegraph(null);
        Sfx.BEAM_FIRE.at(entity.getCenter());
        Impact.shake(4);
        Point2D end = eye().add(aimDir.multiply(rayLength(eye(), aimDir)));
        Particles.burst(end, BEAM_SPARKS, 14, Math.toDegrees(Math.atan2(-aimDir.getY(), -aimDir.getX())), 60, 100, 320, 0.4, 5, 300);
    }

    private void endAttack() {
        state = State.HOVER;
        cooldown = FXGLMath.random(2.0, 2.8);
        removeLine();
    }

    private void removeLine() {
        if (lineEntity != null && lineEntity.isActive()) lineEntity.removeFromWorld();
        lineEntity = null;
        line = null;
        core = null;
    }

    private void steer(double wx, double wy, double tpf) {
        double k = Math.min(1, ACCEL * tpf);
        vx += (wx - vx) * k;
        vy += (wy - vy) * k;
    }

    @Override
    protected void whileDisabled(double tpf) {
        if (stats.isKnockedBack()) {
            physics.setVelocityY(physics.getVelocityY() * Math.max(0, 1 - tpf * 6));
            vx = physics.getVelocityX();
            vy = physics.getVelocityY();
            return;
        }
        vx = 0;
        vy = 0;
        physics.setVelocityX(0);
        physics.setVelocityY(0);
        sprite.loop("idle", 4);
    }

    @Override
    protected void interrupt() {
        state = State.HOVER;
        cooldown = Math.max(cooldown, 1.2);
        removeLine();
    }

    @Override
    public void onDeath() {
        removeLine();
    }

    @Override
    public void onRemoved() {
        removeLine();
    }
}
