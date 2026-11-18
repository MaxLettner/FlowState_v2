package at.htl.flowstate.Skills;

public enum SlotKind {
    NONE,    // cannot be put on the hotbar
    ATTACK,  // becomes the active attack (LMB / RMB)
    TOGGLE,  // switches on/off when its hotbar key is pressed
    INSTANT  // fires once when its hotbar key is pressed
}
