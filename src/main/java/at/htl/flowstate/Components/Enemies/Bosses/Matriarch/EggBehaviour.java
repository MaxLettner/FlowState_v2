package at.htl.flowstate.Components.Enemies.Bosses.Matriarch;

import at.htl.flowstate.Components.Enemies.DemonBehaviour;
import at.htl.flowstate.Components.Enemies.DemonType;
import at.htl.flowstate.Components.Enemies.EnemyStatsComponent;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.core.math.FXGLMath;
import com.almasb.fxgl.entity.SpawnData;
import javafx.geometry.Point2D;

import static com.almasb.fxgl.dsl.FXGL.spawn;

/**
 * A silk egg sac the Void Matriarch drops. It lands wherever it falls (a ledge, a bridge, or down into the void) and
 * pulses faster and faster until it tears open and two void mites skitter out. Break it first.
 */
public class EggBehaviour extends DemonBehaviour {
    private static final double HATCH_TIME = 3.0;
    private static final int MITES = 2;
    /** The arena never holds more mites than this. */
    static final int MAX_MITES = 6;

    private double timer;

    @Override
    protected void think(double tpf) {
        physics.setVelocityX(physics.getVelocityX() * 0.9);
        timer += dt(tpf);
        double k = Math.min(1, timer / HATCH_TIME);
        sprite.loop("idle", 4 + 10 * k);
        if (k > 0.7 && FXGLMath.randomDouble() < tpf * 10) sprite.flash();
        if (timer >= HATCH_TIME) hatch();
    }

    @Override
    protected void whileDisabled(double tpf) {
        physics.setVelocityX(0);
    }

    private void hatch() {
        Point2D c = entity.getCenter();
        Sfx.EGG_HATCH.at(c);
        Particles.of(Particles.VOID).count(16).angle(-90, 100).speed(60, 260).life(0.6).size(5).gravity(400).at(c);
        int room = MAX_MITES - MatriarchBehaviour.mites();
        for (int i = 0; i < Math.min(MITES, room); i++) {
            DemonType m = DemonType.MITE;
            var e = spawn(m.spawnName(), new SpawnData(c.getX() - m.width / 2 + (i - 0.5) * 20, entity.getBottomY() - m.height - 2));
            e.getComponent(EnemyStatsComponent.class).markSummoned();
        }
        entity.removeFromWorld();
    }
}
