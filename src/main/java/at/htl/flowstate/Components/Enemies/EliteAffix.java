package at.htl.flowstate.Components.Enemies;

import javafx.scene.paint.Color;

/** What makes an elite demon special (EliteComponent). */
public enum EliteAffix {
    /** Sets you on fire when it hits; can't be burned. */
    INFERNAL("Infernal", Color.rgb(255, 120, 30, 0.85), "#ffd25a", "#ff8a2a", "#c2300f"),
    /** Chills you (slower) when it hits; can't be frozen or slowed. */
    FROSTBOUND("Frostbound", Color.rgb(140, 210, 255, 0.85), "#ffffff", "#a2e0f7", "#62b6df"),
    /** Heals itself when it hits you. */
    VAMPIRIC("Vampiric", Color.rgb(230, 40, 70, 0.85), "#ff8a8a", "#e63946", "#7a1020"),
    /** Moves and attacks a third faster. */
    SWIFT("Swift", Color.rgb(250, 240, 160, 0.85), "#fffbe0", "#fff3a0", "#e8d070");

    public final String label;
    /** The aura glow around the sprite. */
    public final Color aura;
    /** Particle colours drifting off it. */
    public final Color[] motes;

    EliteAffix(String label, Color aura, String... motes) {
        this.label = label;
        this.aura = aura;
        this.motes = new Color[motes.length];
        for (int i = 0; i < motes.length; i++) this.motes[i] = Color.web(motes[i]);
    }
}
