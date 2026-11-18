package at.htl.flowstate.Skills;

public enum NodeKind {
    START,   // the Rusty Sword, always owned
    STAT,    // repeatable stat node (Strength / Dexterity / Mana)
    SUBTREE, // grants a basic weapon/spell + passive
    SKILL    // leaf skill
}
