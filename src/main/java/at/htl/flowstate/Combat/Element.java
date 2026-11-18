package at.htl.flowstate.Combat;

public enum Element {
    FIRE("Fire"),
    ICE("Ice"),
    POISON("Poison");

    private final String displayName;

    Element(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
