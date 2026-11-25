package at.htl.flowstate.Combat;

import at.htl.flowstate.Components.Enemies.DemonBehaviour;
import at.htl.flowstate.Components.Enemies.EnemyStatsComponent;
import at.htl.flowstate.Components.Identifier.EnemyIdentifierComponent;
import at.htl.flowstate.Components.Identifier.PlatformIdentifierComponent;
import at.htl.flowstate.Components.Player.LoadoutComponent;
import at.htl.flowstate.Components.Player.PlayerStatsComponent;
import at.htl.flowstate.Game;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Skills.SkillType;
import at.htl.flowstate.Visuals.Impact;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.core.math.FXGLMath;
import com.almasb.fxgl.entity.Entity;
import javafx.geometry.Point2D;

import com.almasb.fxgl.entity.EntityWorldListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

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

    // freeze frames & shake for melee hits: base + per damage point, capped
    private static final double HITSTOP_BASE = 0.03;
    private static final double HITSTOP_PER_DAMAGE = 0.0014;
    private static final double HITSTOP_CRIT = 0.025;
    private static final double HITSTOP_KILL = 0.035;
    private static final double HITSTOP_MAX = 0.13;
    private static final double SHAKE_BASE = 2;
    private static final double SHAKE_PER_DAMAGE = 0.12;

    private Combat() {}

    /** What a hit did. {@link #NONE} when the target was already dead or invalid. */
    public record Result(boolean landed, double damage, boolean crit, boolean killed) {
        public static final Result NONE = new Result(false, 0, false, false);
    }

    // Registries kept in sync by a world listener, so queries don't scan every entity (particles included)
    // each call. Copy-on-write: callers may kill enemies while iterating, and adds/removes are rare.
    private static final List<Entity> ENEMIES = new CopyOnWriteArrayList<>();
    private static final List<Entity> PLATFORMS = new CopyOnWriteArrayList<>();
    private static final List<Entity> ENEMIES_VIEW = Collections.unmodifiableList(ENEMIES);
    private static final List<Entity> PLATFORMS_VIEW = Collections.unmodifiableList(PLATFORMS);

    /** Call once from initGame, before any entity is spawned. */
    public static void init() {
        ENEMIES.clear();
        PLATFORMS.clear();
        getGameWorld().addWorldListener(new EntityWorldListener() {
            @Override
            public void onEntityAdded(Entity e) {
                if (e.hasComponent(EnemyIdentifierComponent.class)) ENEMIES.add(e);
                if (e.hasComponent(PlatformIdentifierComponent.class)) PLATFORMS.add(e);
            }

            @Override
            public void onEntityRemoved(Entity e) {
                ENEMIES.remove(e);
                PLATFORMS.remove(e);
            }
        });
    }

    //-----World queries-----
    public static List<Entity> enemies() {
        return ENEMIES_VIEW;
    }

    public static List<Entity> platforms() {
        return PLATFORMS_VIEW;
    }

    public static boolean touchesTerrain(Entity e) {
        for (Entity p : platforms()) if (e.isColliding(p)) return true;
        return false;
    }

    public static List<Entity> enemiesInRadius(Point2D center, double radius) {
        List<Entity> result = new ArrayList<>();
        for (Entity e : ENEMIES) if (e.getCenter().distance(center) <= radius) result.add(e);
        return result;
    }

    public static Entity nearestEnemy(Point2D point, double maxDistance) {
        Entity best = null;
        double bestDist = maxDistance;
        for (Entity e : enemies()) {
            DemonBehaviour ai = DemonBehaviour.of(e);
            if (ai != null && ai.isLurking()) continue;
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
    public static Result hit(Entity enemy, Hit h) {
        if (enemy == null || !enemy.isActive()) return Result.NONE;
        EnemyStatsComponent stats = enemy.getComponentOptional(EnemyStatsComponent.class).orElse(null);
        if (stats == null || stats.isDead()) return Result.NONE;

        Entity player = Game.getPlayer();
        // stone skin (Gargoyle) and shields (Chainwarden): the demon decides how much of the hit gets through
        double through = stats.isInvulnerable() ? 0 : 1;
        if (through > 0 && h.kind != DamageKind.DOT) {
            DemonBehaviour ai = DemonBehaviour.of(enemy);
            if (ai != null) through = ai.guardHit(h, player);
        }
        if (through <= 0) {
            blocked(enemy, player);
            return Result.NONE;
        }
        PlayerStatsComponent playerStats = player.getComponent(PlayerStatsComponent.class);
        LoadoutComponent loadout = player.getComponent(LoadoutComponent.class);

        boolean weaponHit = h.kind == DamageKind.MELEE || h.kind == DamageKind.RANGED;
        boolean fist = h.family == SkillType.FISTICUFFS;
        boolean blunt = h.family == SkillType.BLUNT;

        double damage = h.damage * through;
        if (h.kind == DamageKind.MELEE) damage *= playerStats.getMeleeMultiplier();
        if (h.kind == DamageKind.RANGED) damage *= playerStats.getRangedMultiplier();
        if (weaponHit && loadout.getActiveEnchant() == SkillType.ENCHANTING) damage *= LoadoutComponent.SHARPEN_MULT;
        if (blunt && stats.isStunned()) damage *= CONCUSSION_DAMAGE_MULT;

        double critChance = h.critChance + (fist ? loadout.getComboBonus() : 0);
        boolean crit = critChance > 0 && FXGLMath.randomDouble() < critChance;
        if (crit) damage *= CRIT_MULT;

        // elemental matchups: fire demons hate ice, void demons hate fire
        Element element = element(h, weaponHit, loadout);
        double weak = stats.getType().nature.multiplier(element);
        damage *= weak;

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
            if (crit) Particles.of(Particles.SPARK).count(8).angle(-90, 180).speed(200, 420)
                    .life(0.3).size(6).gravity(200).at(enemy.getCenter());
        }
        if (h.kind != DamageKind.DOT) hitSound(enemy, h, damage, crit, shatteredSlow);
        if (weak > 1 && h.kind != DamageKind.DOT) weakness(enemy, element);
        if (fist) loadout.registerComboHit();
        if (h.kind == DamageKind.MELEE) meleeImpact(enemy, damage, crit, stats.isDead(), h.impact);
        if (stats.isDead()) return new Result(true, damage, crit, true);

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
        return new Result(true, damage, crit, false);
    }

    /**
     * The element a hit carries: ice for freezing/slowing hits (Icicle, Ice Trident) and ice-imbued weapons,
     * fire for burning hits (Spark, Fire Ball) and fire-imbued weapons; null for the rest.
     */
    public static Element element(Hit h, boolean weaponHit, LoadoutComponent loadout) {
        if (h.freeze > 0 || h.slow > 0 || h.shatterSlowed || h.family == SkillType.ICICLE || h.family == SkillType.ICE_TRIDENT) {
            return Element.ICE;
        }
        if (h.burn > 0 || h.family == SkillType.FIRE_BALL) return Element.FIRE;
        if (weaponHit && loadout.getActiveEnchant() == SkillType.IMBUE) {
            Element imbued = loadout.getImbueElement();
            if (imbued == Element.ICE || imbued == Element.FIRE) return imbued;
        }
        return null;
    }

    /** A hit on a weakness: a burst in the element's colour and a bright ring, so the matchup is learnt. */
    private static void weakness(Entity enemy, Element element) {
        Point2D c = enemy.getCenter();
        Particles.of(element == Element.ICE ? Particles.ICE : Particles.EMBER).count(14).angle(-90, 180).speed(160, 380)
                .life(0.45).size(5).gravity(250).at(c);
        Sfx.CRIT.at(c, 0.8, element == Element.ICE ? 1.35 : 0.85);
    }

    /** Sparks and a clang off a shield or stone skin; melee still gets a small freeze so it feels solid. */
    private static void blocked(Entity enemy, Entity player) {
        int dir = enemy.getCenter().getX() >= player.getCenter().getX() ? 1 : -1;
        Point2D at = enemy.getCenter().subtract(dir * enemy.getWidth() * 0.4, 0);
        Particles.of(Particles.SPARK).count(6).angle(dir >= 0 ? 180 : 0, 50).speed(120, 300).life(0.2).size(3).gravity(400).at(at);
        Sfx.BLOCK_HIT.at(at, 0.8, 1.15);
        Impact.hitStop(0.03);
    }

    /** The impact sound of a hit by what dealt it; heavier hits sound deeper. Kills are voiced by the enemy. */
    private static void hitSound(Entity enemy, Hit h, double damage, boolean crit, boolean shatteredSlow) {
        Point2D at = enemy.getCenter();
        double rate = Math.max(0.75, Math.min(1.15, 1.15 - damage / 160));
        SkillType f = h.family;
        Sfx sound = switch (h.kind) {
            case MELEE -> f == SkillType.FISTICUFFS ? Sfx.HIT_PUNCH : f == SkillType.BLUNT ? Sfx.HIT_BLUNT : Sfx.HIT_SLASH;
            case RANGED -> f == SkillType.TRIDENT ? Sfx.HIT_SLASH : Sfx.HIT_ARROW;
            case SPELL -> f == SkillType.FIRE_BALL ? null : Sfx.HIT_MAGIC;   // the explosion is loud enough
            case OTHER -> Sfx.HIT_BLUNT;
            case DOT -> null;
        };
        if (sound != null) sound.at(at, 1, rate);
        if (h.launch) Sfx.BOUNCE.at(at);
        if (crit) Sfx.CRIT.at(at);
        if (shatteredSlow) Sfx.SHATTER.at(at, 0.7, 1.2);
    }

    /** Freeze frames, camera shake and a trembling target, scaled by how hard the hit was. */
    private static void meleeImpact(Entity enemy, double damage, boolean crit, boolean killed, double mult) {
        if (mult <= 0) return;
        double stop = HITSTOP_BASE + HITSTOP_PER_DAMAGE * damage;
        if (crit) stop += HITSTOP_CRIT;
        if (killed) stop += HITSTOP_KILL;
        Impact.hitStop(Math.min(HITSTOP_MAX, stop * mult));

        double shake = SHAKE_BASE + SHAKE_PER_DAMAGE * damage;
        if (crit) shake *= 1.4;
        if (killed) shake += 3;
        Impact.shake(shake * mult);
        if (!killed) Impact.tremble(enemy);
    }
}
