package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Components.Player.PlayerStatsComponent;
import at.htl.flowstate.Components.SpriteComponents.DemonSpriteComponent;
import at.htl.flowstate.Game;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.core.math.FXGLMath;
import com.almasb.fxgl.dsl.components.HealthDoubleComponent;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import javafx.geometry.Point2D;

/**
 * Elite demon: more health and experience, a coloured aura and one {@link EliteAffix}. The immunities and the
 * speed are read by EnemyStatsComponent; the on-hit effects run through {@link #onHit} whenever a demon (or one
 * of its projectiles) damages the player.
 */
public class EliteComponent extends Component {
    public static final double HP_MULT = 1.8;
    public static final double XP_MULT = 2.0;
    public static final double SWIFT_MULT = 1.35;
    private static final double BURN_TIME = 3.0;
    private static final double CHILL_TIME = 2.5;
    private static final double VAMPIRE_HEAL = 0.15;     // of its max health per hit
    private static final double MOTE_INTERVAL = 0.1;

    private final EliteAffix affix;
    private boolean ready;
    private double moteTimer;

    public EliteComponent(EliteAffix affix) {
        this.affix = affix;
    }

    public static EliteAffix random() {
        EliteAffix[] all = EliteAffix.values();
        return all[FXGLMath.random(0, all.length - 1)];
    }

    public EliteAffix getAffix() {
        return affix;
    }

    @Override
    public void onUpdate(double tpf) {
        if (!ready) {
            // not in onAdded: the health and sprite components are added after this one
            ready = true;
            HealthDoubleComponent hp = entity.getComponent(HealthDoubleComponent.class);
            hp.setMaxValue(hp.getMaxValue() * HP_MULT);
            hp.restoreFully();
            entity.getComponent(DemonSpriteComponent.class).setAura(affix.aura);
        }
        moteTimer -= tpf;
        if (moteTimer <= 0) {
            moteTimer = MOTE_INTERVAL;
            Point2D at = entity.getCenter().add(FXGLMath.random(-entity.getWidth() / 2, entity.getWidth() / 2),
                    FXGLMath.random(-entity.getHeight() / 3, entity.getHeight() / 2));
            Particles.burst(at, affix.motes, 1, -90, 25, 15, 50, 0.7, 4, -50);
        }
    }

    /** Called after {@code source} (a demon, or the demon that fired a projectile) damaged the player. */
    public static void onHit(Entity source) {
        if (source == null || !source.isActive()) return;
        EliteComponent elite = source.getComponentOptional(EliteComponent.class).orElse(null);
        if (elite == null) return;
        PlayerStatsComponent player = Game.getPlayer().getComponent(PlayerStatsComponent.class);
        switch (elite.affix) {
            case INFERNAL -> player.burn(BURN_TIME);
            case FROSTBOUND -> player.chill(CHILL_TIME);
            case VAMPIRIC -> {
                HealthDoubleComponent hp = source.getComponent(HealthDoubleComponent.class);
                hp.restore(hp.getMaxValue() * VAMPIRE_HEAL);
                Particles.burst(source.getCenter(), elite.affix.motes, 10, -90, 180, 40, 140, 0.5, 5, -30);
            }
            case SWIFT -> { }
        }
    }
}
