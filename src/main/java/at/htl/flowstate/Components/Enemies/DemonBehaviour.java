package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Combat.Hit;
import at.htl.flowstate.Components.Player.PlayerStatsComponent;
import at.htl.flowstate.Components.SpriteComponents.DemonSpriteComponent;
import at.htl.flowstate.Game;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import com.almasb.fxgl.physics.PhysicsComponent;
import javafx.scene.paint.Color;

/**
 * Base of every demon's AI. Each frame it either {@link #think thinks} (moves and attacks) or, while
 * stunned, frozen, pinned or knocked back, is {@link #whileDisabled disabled}; being disabled
 * {@link #interrupt interrupts} whatever attack was winding up, so a stun is always an answer.
 * Slow scales movement and the attack timers alike ({@link #dt}).
 */
public abstract class DemonBehaviour extends Component {
    /** Telegraph colours: melee lunge/slam, ranged throw, spell. */
    protected static final Color TELL_MELEE = Color.rgb(255, 70, 40, 0.9);
    protected static final Color TELL_RANGED = Color.rgb(255, 170, 40, 0.9);
    protected static final Color TELL_SPELL = Color.rgb(120, 255, 80, 0.9);

    /** Below the deepest floor (LevelGeneration.DEEP_FLOOR): a demon down here is gone for good. */
    private static final double ABYSS_Y = 1600;

    protected final Entity player;
    protected PhysicsComponent physics;
    protected EnemyStatsComponent stats;
    protected DemonSpriteComponent sprite;
    private boolean wasDisabled;

    protected DemonBehaviour() {
        player = Game.getPlayer();
    }

    @Override
    public void onAdded() {
        physics = entity.getComponent(PhysicsComponent.class);
        physics.setOnPhysicsInitialized(() -> physics.getBody().setFixedRotation(true));
    }

    @Override
    public final void onUpdate(double tpf) {
        // looked up here, not in onAdded: FXGL calls onAdded before the entity's later components exist
        if (stats == null) {
            stats = entity.getComponent(EnemyStatsComponent.class);
            sprite = entity.getComponent(DemonSpriteComponent.class);
            onReady();
        }
        if (stats.isDead()) return;
        if (entity.getY() > ABYSS_Y) {          // fell into a pit (a leaping imp, a knocked-back hound)
            entity.removeFromWorld();
            return;
        }
        boolean disabled = stats.isStunned() || stats.isKnockedBack();
        if (disabled) {
            if (!wasDisabled) {
                sprite.setTelegraph(null);
                interrupt();
            }
            whileDisabled(tpf);
        } else {
            think(tpf);
        }
        wasDisabled = disabled;
    }

    /** Components are resolved; called once before the first think. */
    protected void onReady() {}

    /** Moving and attacking. */
    protected abstract void think(double tpf);

    /** Stunned / frozen / pinned / knocked back. */
    protected abstract void whileDisabled(double tpf);

    /** A wind-up or attack was cut short by a disable: go back to the neutral state. */
    protected void interrupt() {}

    /** Called by the stats right before the entity is removed. */
    public void onDeath() {}

    /**
     * How much of a player hit gets through (1 = all, 0 = blocked): Combat.hit asks before applying it.
     * DoT ticks never come here.
     */
    public double guard(Hit h, Entity player) {
        return 1;
    }

    /** Whether the hit comes from the side the demon faces (from the attacker's x, else the player's). */
    protected boolean fromFront(Hit h) {
        double x = Double.isNaN(h.getSourceX()) ? player.getCenter().getX() : h.getSourceX();
        return (x >= entity.getCenter().getX() ? 1 : -1) == sprite.getFacing();
    }

    /** Damages the player (and applies this demon's elite effect). @return true if damage was dealt */
    protected boolean hurtPlayer(double damage) {
        boolean hurt = player.getComponent(PlayerStatsComponent.class).takeDamage(damage, entity, false);
        if (hurt) EliteComponent.onHit(entity);
        return hurt;
    }

    //-----helpers-----
    /** Frame time scaled by Slow, for attack timers. */
    protected double dt(double tpf) {
        return tpf * stats.getSpeedMult();
    }

    /** Hurts the player if they overlap. @return true if damage was dealt */
    protected boolean touch(double damage) {
        if (damage <= 0 || !entity.isColliding(player)) return false;
        return hurtPlayer(damage);
    }

    protected double dxToPlayer() {
        return player.getCenter().getX() - entity.getCenter().getX();
    }

    protected double dyToPlayer() {
        return player.getCenter().getY() - entity.getCenter().getY();
    }

    protected int dirToPlayer() {
        return dxToPlayer() >= 0 ? 1 : -1;
    }
}
