package at.htl.flowstate.Skills;

import java.util.Arrays;
import java.util.List;

import static at.htl.flowstate.Skills.Branch.*;
import static at.htl.flowstate.Skills.NodeKind.*;
import static at.htl.flowstate.Skills.SlotKind.*;

/** Every node of the skill tree (see concept_new/SkillTree.md). */
public enum SkillType {
    RUSTY_SWORD("Rusty Sword", "Rusty Sword", START, MELEE, null, ATTACK,
            "Your starting sword. Medium damage, medium speed."),

    // ----- stat nodes -----
    STRENGTH("Strength", null, STAT, MELEE, null, NONE,
            "+5% melee damage per rank (max 10). Ranks are required for Melee skills."),
    DEXTERITY("Dexterity", null, STAT, RANGED, null, NONE,
            "+5% ranged damage per rank (max 10). Ranks are required for Ranged skills."),
    MANA("Mana", null, STAT, MAGIC, null, NONE,
            "+10 max mana and +0.5%/s mana regen per rank (max 10). Ranks are required for Magic skills."),

    // ----- Melee -----
    SWORDS("Swords", "Iron Sword", SUBTREE, MELEE, null, ATTACK,
            "Grants the Iron Sword. Passive Keen Edge: sword crits apply 1 Bleed."),
    SHORTSWORD("Shortsword", "Shortsword", SKILL, MELEE, SWORDS, ATTACK,
            "3-hit combo. The third hit is a strong finisher with knockback."),
    DUAL_WIELDING("Dual Wielding", "Dual Wielding", SKILL, MELEE, SWORDS, ATTACK,
            "Two katanas that alternate very fast. Two swings can overlap."),
    ZWEIHANDER("Zweihander", "Zweihander", SKILL, MELEE, SWORDS, ATTACK,
            "Tap: slow, long swing. Hold: charge a heavy cleave."),

    FISTICUFFS("Fisticuffs", "Hand Wraps", SUBTREE, MELEE, null, ATTACK,
            "Grants Hand Wraps. Passive Combo Meter: chained glove hits raise crit chance (+2% each, max +20%). Glove crits deal x2 and stun."),
    LEATHER_GLOVES("Leather Gloves", "Leather Gloves", SKILL, MELEE, FISTICUFFS, ATTACK,
            "Very fast jabs. RMB: long dodge dash with brief invulnerability."),
    METAL_GLOVES("Metal Gloves", "Metal Gloves", SKILL, MELEE, FISTICUFFS, ATTACK,
            "Balanced punches. Hold RMB: block (-60% front melee damage, destroys projectiles)."),
    SPIKE_GLOVES("Spike Gloves", "Spike Gloves", SKILL, MELEE, FISTICUFFS, ATTACK,
            "Slow heavy punches that apply Bleed."),

    BLUNT("Blunt", "Club", SUBTREE, MELEE, null, ATTACK,
            "Grants the Club. Passive Concussion: blunt stuns last 30% longer, stunned enemies take +15% blunt damage."),
    HAMMER("Hammer", "Hammer", SKILL, MELEE, BLUNT, ATTACK,
            "Swing that stuns. Attack in mid-air: ground slam shockwave."),
    MORNINGSTAR("Morningstar", "Morningstar", SKILL, MELEE, BLUNT, ATTACK,
            "Tap: whip the chained flail. Hold: spin it around you, faster the longer you hold (longer with Strength)."),
    SPRING_HAMMER("Spring Hammer", "Spring Hammer", SKILL, MELEE, BLUNT, ATTACK,
            "Fast, huge knockback. Launched enemies damage what they crash into. Pogos three times as high."),

    // ----- Ranged -----
    BOW("Bow", "Basic Bow", SUBTREE, RANGED, null, ATTACK,
            "Grants the Basic Bow. Hold to draw: a full draw hits hardest and flies farthest. Passive Eagle Eye: arrows fly 25% faster and drop less."),
    SHORTBOW("Shortbow", "Shortbow", SKILL, RANGED, BOW, ATTACK,
            "Quick 0.4 s draw. Short range, low damage, no pierce."),
    BONE_BOW("Bone Bow", "Bone Bow", SKILL, RANGED, BOW, ATTACK,
            "Balanced bow, pierce 1 on a full draw. Arrows shatter into bone shards when they stop."),
    WAR_BOW("War Bow", "War Bow", SKILL, RANGED, BOW, ATTACK,
            "Hold to draw. Damage, speed and pierce (up to 3) grow with draw time."),

    CROSSBOW("Crossbow", "Basic Crossbow", SUBTREE, RANGED, null, ATTACK,
            "Grants the Basic Crossbow. Hold to crank a bolt in, click to fire it; it stays loaded. Passive Quick Reload: cranking -15%."),
    DUAL_CROSSBOW("Dual Crossbow", "Dual Crossbow", SKILL, RANGED, CROSSBOW, ATTACK,
            "Fires a burst of 2 bolts per load."),
    HEAVY_CROSSBOW("Heavy Crossbow", "Heavy Crossbow", SKILL, RANGED, CROSSBOW, ATTACK,
            "Straight rail bolt with infinite pierce. Pins the last enemy hit to the wall."),
    POISON_CROSSBOW("Poison Crossbow", "Poison Crossbow", SKILL, RANGED, CROSSBOW, ATTACK,
            "Pierces 2. Only the first enemy hit gets 2 Poison stacks."),

    TRIDENT("Trident", "Trident", SUBTREE, RANGED, null, ATTACK,
            "Grants the Trident (LMB stab, RMB throw, walk over it to pick it up). Passive Reunion: first stab after a pickup deals double damage."),
    RECALL_TRIDENT("Recall Trident", "Recall Trident", SKILL, RANGED, TRIDENT, ATTACK,
            "Press RMB again to recall it. It hits every enemy on the way back."),
    ICE_TRIDENT("Ice Trident", "Ice Trident", SKILL, RANGED, TRIDENT, ATTACK,
            "Stab slows. Throw freezes an area where it lands. Frozen enemies shatter."),
    HEAVY_TRIDENT("Heavy Trident", "Heavy Trident", SKILL, RANGED, TRIDENT, ATTACK,
            "Slow, hard stabs. Hold RMB to wind up a straight throw with infinite pierce."),

    // ----- Magic -----
    ARCANE("Arcane", "Arcane Bolt", SUBTREE, MAGIC, null, ATTACK,
            "Grants Arcane Bolt. Passive Arcane Flow: +50% mana regen after 2 s without casting."),
    MAGIC_MISSILE("Magic Missile", "Magic Missile", SKILL, MAGIC, ARCANE, ATTACK,
            "Cheap missile that homes in on the enemy nearest to your cursor."),
    MANA_SHIELD("Mana Shield", "Mana Shield", SKILL, MAGIC, ARCANE, TOGGLE,
            "Toggle: 70% of damage is taken from mana instead of HP. Breaks at 0 mana."),
    LEVITATION("Levitation", "Levitation", SKILL, MAGIC, ARCANE, INSTANT,
            "25 mana: fly for 3 s (W/S), then glide down. 10 s cooldown."),

    ELEMENTAL("Elemental", "Spark", SUBTREE, MAGIC, null, ATTACK,
            "Grants Spark (applies a short Burn). Passive Elemental Mastery: Burn, Slow, Freeze and Poison last 25% longer."),
    FIRE_BALL("Fire Ball", "Fire Ball", SKILL, MAGIC, ELEMENTAL, ATTACK,
            "Hold to charge. Explodes and leaves burning ground. Longer charge = bigger, longer, costlier."),
    ICICLE("Icicle", "Icicle", SKILL, MAGIC, ELEMENTAL, ATTACK,
            "Slows. Hitting a slowed or frozen enemy shatters it for bonus damage."),
    POISON_DARTS("Poison Darts", "Poison Darts", SKILL, MAGIC, ELEMENTAL, ATTACK,
            "3 darts in a fan. Each hit adds 1 Poison stack."),

    ENCHANTING("Enchanting", "Sharpen", SUBTREE, MAGIC, null, TOGGLE,
            "Grants Sharpen (+15% weapon damage). Passive Efficient Enchanting: enchantments drain 25% less mana."),
    IMBUE("Imbue", "Imbue", SKILL, MAGIC, ENCHANTING, TOGGLE,
            "Weapon hits apply an element (Fire/Ice/Poison) from your owned Elemental skills. Press again to cycle."),
    LIFE_STEAL("Life Steal", "Life Steal", SKILL, MAGIC, ENCHANTING, TOGGLE,
            "Every kill heals 8% of your max HP."),
    PIERCING("Piercing", "Piercing", SKILL, MAGIC, ENCHANTING, TOGGLE,
            "All projectiles get +2 pierce (Recall Trident +1).");

    private final String displayName;
    private final String slotName;
    private final NodeKind kind;
    private final Branch branch;
    private final SkillType parent;
    private final SlotKind slotKind;
    private final String description;

    SkillType(String displayName, String slotName, NodeKind kind, Branch branch, SkillType parent,
              SlotKind slotKind, String description) {
        this.displayName = displayName;
        this.slotName = slotName;
        this.kind = kind;
        this.branch = branch;
        this.parent = parent;
        this.slotKind = slotKind;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getSlotName() { return slotName; }
    public NodeKind getKind() { return kind; }
    public Branch getBranch() { return branch; }
    public SkillType getParent() { return parent; }
    public SlotKind getSlotKind() { return slotKind; }
    public String getDescription() { return description; }
    /** Generated by tools/art/icons.py: 24x24, meant to be shown at x2. */
    public String getIconName() { return "Icon_" + name() + ".png"; }

    public boolean isUsable() {
        return slotKind != SlotKind.NONE;
    }

    public boolean isEnchantment() {
        return this == ENCHANTING || this == IMBUE || this == LIFE_STEAL || this == PIERCING;
    }

    public static List<SkillType> subtreesOf(Branch branch) {
        return Arrays.stream(values()).filter(t -> t.kind == SUBTREE && t.branch == branch).toList();
    }

    public static List<SkillType> skillsOf(SkillType subtree) {
        return Arrays.stream(values()).filter(t -> t.parent == subtree).toList();
    }
}
