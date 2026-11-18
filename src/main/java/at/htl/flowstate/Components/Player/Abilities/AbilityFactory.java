package at.htl.flowstate.Components.Player.Abilities;

import at.htl.flowstate.Components.Player.LoadoutComponent;
import at.htl.flowstate.Skills.SkillType;
import com.almasb.fxgl.entity.Entity;

/** Creates the ability behind each hotbar attack. Toggles/instants are handled by LoadoutComponent. */
public final class AbilityFactory {
    private AbilityFactory() {}

    public static Ability create(SkillType type, Entity player, LoadoutComponent loadout) {
        return switch (type) {
            case RUSTY_SWORD -> new SwingWeaponAbility(player, loadout, MeleeSpec.RUSTY_SWORD);

            case SWORDS -> new SwingWeaponAbility(player, loadout, MeleeSpec.IRON_SWORD);
            case SHORTSWORD -> new ShortswordAbility(player, loadout);
            case DUAL_WIELDING -> new DualWieldAbility(player, loadout);
            case ZWEIHANDER -> new ZweihanderAbility(player, loadout);

            case FISTICUFFS -> new GloveAbility(player, loadout, GloveAbility.Glove.HAND_WRAPS);
            case LEATHER_GLOVES -> new GloveAbility(player, loadout, GloveAbility.Glove.LEATHER);
            case METAL_GLOVES -> new GloveAbility(player, loadout, GloveAbility.Glove.METAL);
            case SPIKE_GLOVES -> new GloveAbility(player, loadout, GloveAbility.Glove.SPIKE);

            case BLUNT -> new SwingWeaponAbility(player, loadout, MeleeSpec.CLUB);
            case HAMMER -> new HammerAbility(player, loadout);
            case MORNINGSTAR -> new MorningstarAbility(player, loadout);
            case SPRING_HAMMER -> new SwingWeaponAbility(player, loadout, MeleeSpec.SPRING_HAMMER);

            case BOW -> new BowAbility(player, loadout, BowAbility.Bow.BASIC);
            case SHORTBOW -> new BowAbility(player, loadout, BowAbility.Bow.SHORT);
            case BONE_BOW -> new BowAbility(player, loadout, BowAbility.Bow.BONE);
            case WAR_BOW -> new WarBowAbility(player, loadout);

            case CROSSBOW -> new CrossbowAbility(player, loadout, CrossbowAbility.Crossbow.BASIC);
            case DUAL_CROSSBOW -> new CrossbowAbility(player, loadout, CrossbowAbility.Crossbow.DUAL);
            case HEAVY_CROSSBOW -> new CrossbowAbility(player, loadout, CrossbowAbility.Crossbow.HEAVY);
            case POISON_CROSSBOW -> new CrossbowAbility(player, loadout, CrossbowAbility.Crossbow.POISON);

            case TRIDENT -> new TridentAbility(player, loadout, TridentAbility.Variant.BASIC);
            case RECALL_TRIDENT -> new TridentAbility(player, loadout, TridentAbility.Variant.RECALL);
            case ICE_TRIDENT -> new TridentAbility(player, loadout, TridentAbility.Variant.ICE);
            case HEAVY_TRIDENT -> new TridentAbility(player, loadout, TridentAbility.Variant.HEAVY);

            case ARCANE -> new SpellAbility(player, loadout, SpellAbility.Spell.ARCANE_BOLT);
            case MAGIC_MISSILE -> new SpellAbility(player, loadout, SpellAbility.Spell.MAGIC_MISSILE);
            case ELEMENTAL -> new SpellAbility(player, loadout, SpellAbility.Spell.SPARK);
            case FIRE_BALL -> new FireballAbility(player, loadout);
            case ICICLE -> new SpellAbility(player, loadout, SpellAbility.Spell.ICICLE);
            case POISON_DARTS -> new SpellAbility(player, loadout, SpellAbility.Spell.POISON_DARTS);

            default -> null; // stats, toggles and instants have no ability object
        };
    }
}
