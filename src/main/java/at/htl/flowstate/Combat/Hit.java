package at.htl.flowstate.Combat;

import at.htl.flowstate.Skills.SkillType;

/** Describes one hit on an enemy. Built fluently, applied by {@link Combat#hit}. */
public final class Hit {
    double damage;
    final DamageKind kind;
    SkillType family;
    double critChance;
    double knockback;
    int knockDir;
    double sourceX = Double.NaN;
    boolean launch;
    double stun, slow, freeze, burn;
    int poisonStacks, bleedStacks;
    boolean shatterSlowed;
    /** Scales the freeze frames and camera shake of this hit (1 = normal, 0 = none). */
    double impact = 1;

    private Hit(double damage, DamageKind kind) {
        this.damage = damage;
        this.kind = kind;
    }

    public static Hit of(double damage, DamageKind kind) {
        return new Hit(damage, kind);
    }

    /** The weapon family (sub-tree) this hit belongs to, used for passives. */
    public Hit family(SkillType family) { this.family = family; return this; }
    public Hit crit(double percent) { this.critChance = percent / 100.0; return this; }
    public Hit knockback(double strength, int direction) { this.knockback = strength; this.knockDir = direction; return this; }
    /** Knockback pointing away from the given x coordinate. */
    public Hit knockbackFrom(double strength, double sourceX) { this.knockback = strength; this.knockDir = 0; this.sourceX = sourceX; return this; }
    /** Launched enemies damage other enemies they crash into (Spring Hammer). */
    public Hit launch() { this.launch = true; return this; }
    public Hit stun(double seconds) { this.stun = seconds; return this; }
    public Hit slow(double seconds) { this.slow = seconds; return this; }
    public Hit freeze(double seconds) { this.freeze = seconds; return this; }
    public Hit burn(double seconds) { this.burn = seconds; return this; }
    public Hit poison(int stacks) { this.poisonStacks = stacks; return this; }
    public Hit bleed(int stacks) { this.bleedStacks = stacks; return this; }
    /** Icicle: hitting a slowed enemy removes the slow and deals bonus damage. */
    public Hit shatterSlowed() { this.shatterSlowed = true; return this; }
    public Hit impact(double mult) { this.impact = mult; return this; }

    public Hit withDamage(double newDamage) {
        Hit h = copy();
        h.damage = newDamage;
        return h;
    }

    public Hit copy() {
        Hit h = new Hit(damage, kind);
        h.family = family; h.critChance = critChance; h.knockback = knockback; h.knockDir = knockDir;
        h.sourceX = sourceX; h.launch = launch; h.stun = stun; h.slow = slow; h.freeze = freeze;
        h.burn = burn; h.poisonStacks = poisonStacks; h.bleedStacks = bleedStacks; h.shatterSlowed = shatterSlowed;
        h.impact = impact;
        return h;
    }

    public double getDamage() { return damage; }
    public double getKnockback() { return knockback; }
    public double getImpact() { return impact; }
    public DamageKind getKind() { return kind; }
    public SkillType getFamily() { return family; }
    /** The x the hit came from (NaN = from the player). */
    public double getSourceX() { return sourceX; }
}
