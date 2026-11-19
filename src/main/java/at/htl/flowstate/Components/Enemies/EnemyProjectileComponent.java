package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Combat.Combat;
import at.htl.flowstate.Components.Player.PlayerStatsComponent;
import at.htl.flowstate.Game;
import at.htl.flowstate.Sfx;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;

public class EnemyProjectileComponent extends Component {
    private final Entity player;
    private final double damage;

    public EnemyProjectileComponent(double damage) {
        this.player = Game.getPlayer();
        this.damage = damage;
    }

    @Override
    public void onUpdate(double tpf) {
        if (entity.isColliding(player)) {
            // blocked projectiles (Metal Gloves) are destroyed without dealing damage
            player.getComponent(PlayerStatsComponent.class).takeDamage(damage, entity, true);
            entity.removeFromWorld();
            return;
        }
        if (Combat.touchesTerrain(entity)) {
            Sfx.ARROW_THUNK.at(entity.getCenter(), 0.6, 0.85);
            entity.removeFromWorld();
        }
    }
}
