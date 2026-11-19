package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Combat.Combat;
import at.htl.flowstate.Components.Player.PlayerStatsComponent;
import at.htl.flowstate.Game;
import at.htl.flowstate.Sfx;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;

import java.util.function.Consumer;

/**
 * Damages the player on contact and is destroyed by terrain. Swung weapons deflect anything carrying
 * this component (MeleeSwingComponent).
 */
public class EnemyProjectileComponent extends Component {
    private final Entity player;
    private final double damage;
    private final Consumer<Entity> onImpact;

    public EnemyProjectileComponent(double damage) {
        this(damage, null);
    }

    /** {@code onImpact} runs (with the projectile) when it hits the player or terrain, instead of the thunk. */
    public EnemyProjectileComponent(double damage, Consumer<Entity> onImpact) {
        this.player = Game.getPlayer();
        this.damage = damage;
        this.onImpact = onImpact;
    }

    @Override
    public void onUpdate(double tpf) {
        if (entity.isColliding(player)) {
            // blocked projectiles (Metal Gloves) are destroyed without dealing damage
            player.getComponent(PlayerStatsComponent.class).takeDamage(damage, entity, true);
            if (onImpact != null) onImpact.accept(entity);
            entity.removeFromWorld();
            return;
        }
        if (Combat.touchesTerrain(entity)) {
            if (onImpact != null) onImpact.accept(entity);
            else Sfx.ARROW_THUNK.at(entity.getCenter(), 0.6, 0.85);
            entity.removeFromWorld();
        }
    }
}
