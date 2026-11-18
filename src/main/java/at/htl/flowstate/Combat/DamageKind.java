package at.htl.flowstate.Combat;

public enum DamageKind {
    MELEE,  // scaled by Strength, counts as a weapon hit
    RANGED, // scaled by Dexterity, counts as a weapon hit
    SPELL,
    DOT,    // burn / poison / bleed ticks (never shatter a freeze)
    OTHER   // collisions etc.
}
