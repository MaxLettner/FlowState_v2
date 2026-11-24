package at.htl.flowstate.Components.Player.Abilities;

import at.htl.flowstate.Sfx;
import at.htl.flowstate.Skills.SkillType;

/**
 * What a hotbar skill does, as plain data (read from the same specs and enums the abilities use), so the Hollow Ember
 * can copy any skill the player carries. {@link #of} is null for stat nodes. Fields that don't apply are 0 / null.
 */
public record SkillMove(SkillType skill, Kind kind, String texture, double damage, double interval, double reach,
                        double knockback, double stun, double speed, double gravity, int count,
                        double burn, double chill, double poison, Shot shot, Sfx sound, double soundRate) {

    /** SWING: one-handed arc; SLAM: heavy weapon, arc plus a ground blow; FISTS: three-punch combo; SHOT: any projectile. */
    public enum Kind { SWING, SLAM, FISTS, SHOT, SHIELD, HOP, ENCHANT }

    /** A projectile's look: strip, frames, fps, hitbox, anchor (NaN = centre), pixel scale, drawn diagonally (tridents). */
    public record Shot(String texture, int frames, double fps, double width, double height, double anchorX,
                       double anchorY, int scale, boolean diagonal, boolean explodes) {}

    /** The skill's move, or null for nodes that are not usable (stats). */
    public static SkillMove of(SkillType type) {
        return switch (type) {
            case RUSTY_SWORD -> melee(type, MeleeSpec.RUSTY_SWORD);
            case SWORDS -> melee(type, MeleeSpec.IRON_SWORD);
            case SHORTSWORD -> melee(type, MeleeSpec.SHORTSWORD);
            case DUAL_WIELDING -> melee(type, MeleeSpec.DUAL_WIELDING);
            case ZWEIHANDER -> melee(type, MeleeSpec.ZWEIHANDER);
            case BLUNT -> melee(type, MeleeSpec.CLUB);
            case HAMMER -> melee(type, MeleeSpec.HAMMER);
            case MORNINGSTAR -> melee(type, MeleeSpec.MORNINGSTAR);
            case SPRING_HAMMER -> melee(type, MeleeSpec.SPRING_HAMMER);

            case FISTICUFFS -> fists(type, GloveAbility.Glove.HAND_WRAPS);
            case LEATHER_GLOVES -> fists(type, GloveAbility.Glove.LEATHER);
            case METAL_GLOVES -> fists(type, GloveAbility.Glove.METAL);
            case SPIKE_GLOVES -> fists(type, GloveAbility.Glove.SPIKE);

            case BOW -> bow(type, BowAbility.Bow.BASIC);
            case SHORTBOW -> bow(type, BowAbility.Bow.SHORT);
            case BONE_BOW -> bow(type, BowAbility.Bow.BONE);
            case WAR_BOW -> shot(type, 20, 1.0, 1500, 0, BowAbility.ARROW_GRAVITY * BowAbility.EAGLE_EYE_GRAVITY, 1,
                    plain("WarArrow.png", 28, 3), Sfx.BOW_RELEASE, 0.7);

            case CROSSBOW -> crossbow(type, CrossbowAbility.Crossbow.BASIC);
            case DUAL_CROSSBOW -> crossbow(type, CrossbowAbility.Crossbow.DUAL);
            case HEAVY_CROSSBOW -> crossbow(type, CrossbowAbility.Crossbow.HEAVY);
            case POISON_CROSSBOW -> crossbow(type, CrossbowAbility.Crossbow.POISON);

            case TRIDENT -> trident(type, TridentAbility.Variant.BASIC);
            case RECALL_TRIDENT -> trident(type, TridentAbility.Variant.RECALL);
            case ICE_TRIDENT -> trident(type, TridentAbility.Variant.ICE);
            case HEAVY_TRIDENT -> trident(type, TridentAbility.Variant.HEAVY);

            case ARCANE -> spell(type, SpellAbility.Spell.ARCANE_BOLT, 0, 0, 0, Sfx.CAST_ARCANE, 0.7);
            case MAGIC_MISSILE -> spell(type, SpellAbility.Spell.MAGIC_MISSILE, 0, 0, 0, Sfx.CAST_ARCANE, 0.9);
            case ELEMENTAL -> spell(type, SpellAbility.Spell.SPARK, 2.0, 0, 0, Sfx.CAST_SPARK, 0.7);
            case ICICLE -> spell(type, SpellAbility.Spell.ICICLE, 0, 2.5, 0, Sfx.CAST_ICICLE, 0.7);
            case POISON_DARTS -> spell(type, SpellAbility.Spell.POISON_DARTS, 0, 0, 3, Sfx.CAST_POISON, 0.7);
            case FIRE_BALL -> {
                Shot s = new Shot("FireBall.png", 4, 14, 30, 30, 19, 8, 3, false, true);
                yield new SkillMove(type, Kind.SHOT, null, 30, 2.4, 0, 0, 0, 800, 0, 1, 2.5, 0, 0, s, Sfx.CAST_FIREBALL, 0.7);
            }

            case MANA_SHIELD -> other(type, Kind.SHIELD);
            case LEVITATION -> other(type, Kind.HOP);
            case ENCHANTING, IMBUE, LIFE_STEAL, PIERCING -> other(type, Kind.ENCHANT);
            default -> null;       // Strength, Dexterity, Mana
        };
    }

    private static SkillMove melee(SkillType type, MeleeSpec s) {
        boolean heavy = s.width() >= 20;
        return new SkillMove(type, heavy ? Kind.SLAM : Kind.SWING, s.texture(), s.damage(), s.interval(), s.reach(),
                s.knockback(), s.stun(), 0, 0, 1, 0, 0, 0, null, heavy ? Sfx.SWING_HEAVY : Sfx.SWING_LIGHT, 0.6);
    }

    private static SkillMove fists(SkillType type, GloveAbility.Glove g) {
        return new SkillMove(type, Kind.FISTS, g.texture, g.damage, g.interval, g.reach, 250, 0, 0, 0, 3,
                0, 0, 0, null, Sfx.PUNCH, 0.6);
    }

    private static SkillMove bow(SkillType type, BowAbility.Bow b) {
        return shot(type, b.damage, b.fullDraw, b.speed * BowAbility.EAGLE_EYE_SPEED, 0,
                BowAbility.ARROW_GRAVITY * BowAbility.EAGLE_EYE_GRAVITY, 1, plain(b.arrow, 28, 3), Sfx.BOW_RELEASE, 0.7);
    }

    private static SkillMove crossbow(SkillType type, CrossbowAbility.Crossbow c) {
        return shot(type, c.damage, c.reload, c.speed, c == CrossbowAbility.Crossbow.POISON ? 3 : 0, c.gravity, c.burst,
                plain(c.bolt, c.boltLength, c.boltThickness), Sfx.CROSSBOW_FIRE, c == CrossbowAbility.Crossbow.HEAVY ? 0.5 : 0.7);
    }

    private static SkillMove trident(SkillType type, TridentAbility.Variant v) {
        Shot s = new Shot(v.texture, 1, 0, v.width, v.height, Double.NaN, Double.NaN, 2, true, false);
        return new SkillMove(type, Kind.SHOT, null, v.throwDamage, 1.5, 0, 0, 0, v.throwSpeed, 0, 1, 0,
                v == TridentAbility.Variant.ICE ? 1.5 : 0, 0, s, Sfx.THROW, v == TridentAbility.Variant.HEAVY ? 0.55 : 0.75);
    }

    private static SkillMove spell(SkillType type, SpellAbility.Spell sp, double burn, double chill, double poison, Sfx sound, double rate) {
        Shot s = new Shot(sp.texture, sp.frames, sp.fps, sp.width, sp.height, sp.anchorX, sp.anchorY, 2, false, false);
        return new SkillMove(type, Kind.SHOT, null, sp.damage, Math.max(1.0, sp.interval * 2), 0, 0, 0, sp.speed, 0, sp.count,
                burn, chill, poison, s, sound, rate);
    }

    private static SkillMove shot(SkillType type, double damage, double interval, double speed, double poison, double gravity,
                                  int count, Shot s, Sfx sound, double rate) {
        return new SkillMove(type, Kind.SHOT, null, damage, Math.max(1.0, interval), 0, 0, 0, speed, gravity, count,
                0, 0, poison, s, sound, rate);
    }

    private static Shot plain(String texture, double w, double h) {
        return new Shot(texture, 1, 0, w, h, Double.NaN, Double.NaN, 2, false, false);
    }

    private static SkillMove other(SkillType type, Kind kind) {
        return new SkillMove(type, kind, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null, null, 1);
    }
}
