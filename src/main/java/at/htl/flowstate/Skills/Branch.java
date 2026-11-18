package at.htl.flowstate.Skills;

public enum Branch {
    MELEE("Melee"),
    RANGED("Ranged"),
    MAGIC("Magic");

    private final String displayName;

    Branch(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
