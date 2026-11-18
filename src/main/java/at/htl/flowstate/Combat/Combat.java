package at.htl.flowstate.Combat;

import at.htl.flowstate.Components.Enemies.EnemyStatsComponent;
import at.htl.flowstate.Components.Identifier.EnemyIdentifierComponent;
import at.htl.flowstate.Components.Identifier.PlatformIdentifierComponent;
import at.htl.flowstate.Components.Player.LoadoutComponent;
import at.htl.flowstate.Components.Player.PlayerStatsComponent;
import at.htl.flowstate.Game;
import at.htl.flowstate.Skills.SkillType;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.entity.Entity;
import javafx.geometry.Point2D;

import java.util.List;

import static com.almasb.fxgl.dsl.FXGL.getGameWorld;

/** Central damage pipeline and world queries shared by all attacks. */
public final class Combat {
    private static final double CRIT_MULT = 2.0;
    private static final double CONCUSSION_DAMAGE_MULT = 1.15;
    private static final double CONCUSSION_STUN_MULT = 1.3;
    private static final double ICICLE_SHATTER_BONUS = 15;
    private static final double FIST_CRIT_STUN = 0.5;
    private static final double IMBUE_BURN = 2.0;
    private static final double IMBUE_SLOW = 1.5;

    private Combat() {}

    //-----World queries-----
    public static List<Entity> enemies() {
        return getGameWorld().getEntitiesByComponent(EnemyIdentifierComponent.class);
    }

    public static List<Entity> platforms() {
        return getGameWorld().getEntitiesByComponent(PlatformIdentifierComponent.class);
    }

    public static boolean touchesTerrain(Entity e) {
        for (Entity p : platforms()) if (e.isColliding(p)) return true;
        return false;
    }

    public static List<Entity> enemiesInRadius(Point2D center, double radius) {
        return enemies().stream().filter(e -> e.getCenter().distance(center) <= radius).toList();
    }

    public static Entity nearestEnemy(Point2D point, double maxDistance) {
        Entity best = null;
        double bestDist = maxDistance;
        for (Entity e : enemies()) {
            double d = e.getCenter().distance(point);
            if (d <= bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    /** Top of the highest platform below (x, y), or NaN when there is none (pit). */
    public static double groundYBelow(double x, double y) {
        double best = Double.NaN;
        for (Entity p : platforms()) {
            if (x < p.getX() || x > p.getX() + p.getWidth()) continue;
            if (p.getY() < y - 40) continue;
            if (Double.isNaN(best) || p.getY() < best) best = p.getY();
        }
        return best;
    }

    //-----Damage-----
    public static void hit(Entity enemy, Hit h) {
        if (enemy == null || !enemy.isActive()) return;
        EnemyStatsComponent stats = enemy.getComponentOptional(EnemyStatsComponent.class).orElse(null);
        if (stats == null || stats.isDead()) return;

        Entity player = Game.getPlayer();
        PlayerStatsComponent playerStats = player.getComponent(PlayerStatsComponent.class);
        LoadoutComponent loadout = player.getComponent(LoadoutComponent.class);

        boolean weaponHit = h.kind == DamageKind.MELEE || h.kind == DamageKind.RANGED;
        boolean fist = h.family == SkillType.FISTICUFFS;
        boolean blunt = h.family == SkillType.BLUNT;

        double damage = h.damage;
        if (h.kind == DamageKind.MELEE) damage *= playerStats.getMeleeMultiplier();
        if (h.kind == DamageKind.RANGED) damage *= playerStats.getRangedMultiplier();
        if (weaponHit && loadout.getActiveEnchant() == SkillType.ENCHANTING) damage *= LoadoutComponent.SHARPEN_MULT;
        if (blunt && stats.isStunned()) damage *= CONCUSSION_DAMAGE_MULT;

        double critChance = h.critChance + (fist ? loadout.getComboBonus() : 0);
        boolean crit = critChance > 0 && Math.random() < critChance;
        if (crit) damage *= CRIT_MULT;

        boolean shatteredSlow = false;
        if (h.shatterSlowed && stats.isSlowed() && !stats.isFrozen()) {
            damage += ICICLE_SHATTER_BONUS;
            stats.clearSlow();
            shatteredSlow = true;
        }

        stats.damage(damage, h.kind);
        if (h.kind != DamageKind.DOT) {
            int sparkDir = enemy.getCenter().getX() >= player.getCenter().getX() ? 1 : -1;
            Particles.hit(enemy.getCenter().subtract(sparkDir * 12, 0), sparkDir);
            if (crit) Particles.burst(enemy.getCenter(), Particles.SPARK, 8, -90, 180, 200, 420, 0.3, 6, 200);
        }
        if (fist) loadout.registerComboHit();
        if (stats.isDead()) return;

        if (crit && h.family == SkillType.SWORDS) stats.addBleed(1);
        if (crit && fist) stats.stun(FIST_CRIT_STUN);
        if (h.stun > 0) stats.stun(blunt ? h.stun * CONCUSSION_STUN_MULT : h.stun);
        if (h.freeze > 0) stats.freeze(h.freeze);
        if (h.slow > 0 && !shatteredSlow) stats.slow(h.slow);
        if (h.burn > 0) stats.burn(h.burn);
        if (h.poisonStacks > 0) stats.addPoison(h.poisonStacks);
        if (h.bleedStacks > 0) stats.addBleed(h.bleedStacks);

        if (weaponHit && loadout.getActiveEnchant() == SkillType.IMBUE && loadout.getImbueElement() != null) {
            switch (loadout.getImbueElement()) {
                case FIRE -> stats.burn(IMBUE_BURN);
                case ICE -> stats.slow(IMBUE_SLOW);
                case POISON -> stats.addPoison(1);
            }
        }

        if (h.knockback > 0) {
            int dir = h.knockDir;
            if (dir == 0) {
                double sourceX = Double.isNaN(h.sourceX) ? player.getCenter().getX() : h.sourceX;
                dir = enemy.getCenter().getX() >= sourceX ? 1 : -1;
            }
            stats.applyKnockback(h.knockback, dir, h.launch);
        }
    }
}
