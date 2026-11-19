package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Combat.Element;

/**
 * What a demon is made of: the elemental matchups that give the elemental skills a reason to exist.
 * <ul>
 *   <li>{@link #FLESH}: no strengths or weaknesses.</li>
 *   <li>{@link #FIRE}: can't be burned, takes double damage from ice hits and a freeze shatters for x3 instead of x2.</li>
 *   <li>{@link #VOID}: half knockback, takes 1.5x damage from fire hits and burn.</li>
 * </ul>
 */
public enum DemonNature {
    FLESH(null, 1),
    FIRE(Element.ICE, 2.0),
    VOID(Element.FIRE, 1.5);

    /** Freeze shatter multiplier for {@link #FIRE}; everything else shatters for x2. */
    public static final double FIRE_SHATTER = 3.0, SHATTER = 2.0;
    /** Knockback taken by {@link #VOID} demons (on top of the type's own knockback multiplier). */
    public static final double VOID_KNOCKBACK = 0.5;

    /** The element this nature is weak to (null: none) and the damage multiplier for it. */
    public final Element weakness;
    public final double weaknessMult;

    DemonNature(Element weakness, double weaknessMult) {
        this.weakness = weakness;
        this.weaknessMult = weaknessMult;
    }

    public boolean burnImmune() {
        return this == FIRE;
    }

    /** Damage multiplier for a hit of this element (null = not elemental). */
    public double multiplier(Element element) {
        return element != null && element == weakness ? weaknessMult : 1;
    }
}
