package at.htl.flowstate.Components.Enemies.Bosses.Matriarch;

import at.htl.flowstate.Combat.DamageKind;
import at.htl.flowstate.Combat.Hit;
import at.htl.flowstate.Components.Enemies.DemonBehaviour;
import at.htl.flowstate.Components.Enemies.DemonType;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.core.math.FXGLMath;
import com.almasb.fxgl.dsl.components.HealthDoubleComponent;
import com.almasb.fxgl.entity.Entity;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;

/**
 * One of the strands the Void Matriarch hangs from: a tall thin target from above the screen down to a knot. Any
 * hit cuts into it; fire runs along the whole strand and burns it through in about a second and a half; a frozen
 * strand is brittle and the next hit shatters it. When it goes, she is told ({@link MatriarchBehaviour#anchorCut}).
 */
public class ThreadBehaviour extends DemonBehaviour {
    private static final double BURN_THROUGH = 55;      // extra damage per second while it burns
    private static final double FLASH = 0.1;
    private static final Color GLOW = Color.rgb(140, 77, 255, 0.4), CORE = Color.rgb(226, 210, 255);
    private static final Color FIRE = Color.rgb(255, 150, 60), ICE = Color.rgb(180, 230, 255), WHITE = Color.WHITE;

    private MatriarchBehaviour owner;
    private Line glow, core;
    private double lastHp = -1, flashLeft, time;
    private boolean released;

    void setOwner(MatriarchBehaviour owner) {
        this.owner = owner;
    }

    @Override
    public void onAdded() {
        super.onAdded();
        double w = DemonType.THREAD.width, h = DemonType.THREAD.height;
        glow = new Line(w / 2, 0, w / 2, h - 6);
        glow.setStroke(GLOW);
        glow.setStrokeWidth(7);
        core = new Line(w / 2, 0, w / 2, h - 6);
        core.setStroke(CORE);
        core.setStrokeWidth(2.2);
        entity.getViewComponent().addChild(new Group(glow, core));
    }

    @Override
    protected void think(double tpf) {
        physics.setVelocityX(0);
        physics.setVelocityY(0);
        time += tpf;
        if (stats.isBurning()) {
            stats.damage(BURN_THROUGH * tpf, DamageKind.DOT);
            if (FXGLMath.randomDouble() < tpf * 30) {
                double y = entity.getY() + FXGLMath.random(0.2, 1.0) * entity.getHeight();
                Particles.of(Particles.EMBER).count(1).angle(-90, 30).speed(30, 90)
                        .life(0.5).size(4).gravity(-60).at(new Point2D(entity.getCenter().getX(), y));
            }
            if (FXGLMath.randomDouble() < tpf * 4) Sfx.CRACKLE.at(entity.getCenter(), 0.5, 1.3);
        }
        look(tpf);
    }

    @Override
    protected void whileDisabled(double tpf) {
        physics.setVelocityX(0);
        physics.setVelocityY(0);
        look(tpf);
    }

    private void look(double tpf) {
        double hp = entity.getComponent(HealthDoubleComponent.class).getValue();
        if (lastHp >= 0 && hp < lastHp - 0.5) flashLeft = FLASH;
        lastHp = hp;
        flashLeft = Math.max(0, flashLeft - tpf);
        Color c = flashLeft > 0 ? WHITE : stats.isFrozen() ? ICE
                : stats.isBurning() ? FIRE.interpolate(CORE, 0.5 + 0.5 * Math.sin(time * 25)) : CORE;
        core.setStroke(c);
        // the closer to cut, the more it frays: the glow thins out
        double left = hp / DemonType.THREAD.hp;
        glow.setStrokeWidth(3 + 4 * left);
    }

    /** Frozen silk is brittle: whatever hits it next shatters it. */
    @Override
    public double guard(Hit h, Entity player) {
        if (stats.isFrozen()) return 20;
        return 1;
    }

    @Override
    public void onDeath() {
        snapFx();
        if (owner != null && !released) owner.anchorCut(entity);
    }

    /** Pulled up and away (she moves to a new spot): no cut, no damage. */
    void release() {
        released = true;
        Particles.of(Particles.VOID).count(6).angle(-90, 40).speed(60, 200).life(0.5).size(4).gravity(-200).at(bottom());
        entity.removeFromWorld();
    }

    private void snapFx() {
        Sfx.THREAD_SNAP.at(bottom());
        double x = entity.getCenter().getX();
        for (double y = Math.max(0, entity.getY()); y < entity.getBottomY(); y += 50) {
            Particles.of(Particles.VOID).count(2).angle(90, 50).speed(30, 140)
                    .life(0.7).size(4).gravity(400).at(new Point2D(x, y));
        }
    }

    private Point2D bottom() {
        return new Point2D(entity.getCenter().getX(), entity.getBottomY());
    }
}
