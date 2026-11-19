package at.htl.flowstate.Components.Enemies;

import javafx.scene.paint.Color;

/**
 * Per-demon data: hitbox, sprite rig (must match tools/art/demons.py), toughness and death look.
 * Sprites are {@code <prefix>_<anim>.png} strips of {@code frameW x frameH} frames shown at x2; the anchor is
 * the art pixel that sits on the bottom centre of the hitbox (the centre for flyers).
 */
public enum DemonType {
    //        prefix         hp   hitbox    frame    anchor  fly    xp    knock stun  death pitch, splat colours
    IMP("DemonImp", 34, 28, 40, 40, 36, 20, 35, false, 0.4, 1.3, 1.0, 1.45,
            "#ff8a6a", "#e0473a", "#7a1826"),
    HOUND("DemonHound", 70, 70, 44, 64, 40, 32, 39, false, 1.0, 1.0, 1.0, 1.1,
            "#ff9a3d", "#7c3f3a", "#e8dcc0"),
    BRUTE("DemonBrute", 520, 128, 240, 160, 168, 80, 165, false, 5.0, 0.45, 0.5, 0.6,
            "#c2abd0", "#624a7a", "#ff8a2a"),
    HURLER("DemonHurler", 80, 46, 76, 56, 52, 28, 51, false, 1.2, 0.9, 1.0, 1.0,
            "#f4e690", "#93782c", "#ff8a2a"),
    BAT("DemonBat", 18, 30, 22, 48, 44, 24, 22, true, 0.34, 1.4, 1.0, 1.7,
            "#ff8a2a", "#4e3e3e", "#ad3e2a"),
    CULTIST("DemonCultist", 90, 38, 86, 48, 60, 24, 59, false, 1.6, 0.9, 1.0, 1.2,
            "#b8ff6a", "#6a4c88", "#22a82e"),
    // wave 2
    WARDEN("DemonWarden", 150, 46, 100, 64, 60, 32, 59, false, 2.0, 0.8, 1.0, 0.85,
            "#a9b2c4", "#555e75", "#d23e34"),
    BLOATER("DemonBloater", 45, 56, 66, 52, 48, 26, 47, false, 0.6, 1.4, 1.0, 0.8,
            "#786a50", "#352c20", "#ff8a2a"),
    GARGOYLE("DemonGargoyle", 95, 50, 50, 56, 48, 28, 24, true, 1.6, 0.8, 1.0, 0.9,
            "#9a958c", "#55524d", "#ff8a2a"),
    WATCHER("DemonWatcher", 60, 40, 40, 48, 48, 24, 24, true, 1.4, 1.2, 1.0, 1.3,
            "#f2d8ca", "#a05e76", "#e0601a"),
    GATEKEEPER("DemonGatekeeper", 200, 50, 130, 56, 72, 28, 71, false, 3.0, 0.4, 0.8, 0.75,
            "#ff7aa8", "#64323e", "#555966"),
    PRIEST("DemonPriest", 85, 40, 84, 48, 56, 24, 55, false, 1.6, 1.0, 1.0, 1.15,
            "#d08cff", "#82283e", "#ece2c6"),
    LORD("DemonLord", 1600, 150, 320, 352, 280, 176, 269, false, 20, 0.0, 0.3, 0.5,
            "#ff8a2a", "#c04a3e", "#fff6c0");

    public final String prefix;
    public final double hp;
    /** Hitbox in world px. */
    public final double width, height;
    /** Art frame in native px and the anchor pixel (bottom centre of the feet, or the centre for flyers). */
    public final int frameW, frameH, anchorX, anchorY;
    public final boolean flying;
    /** Experience for the kill (a level costs 3+ at the start). */
    public final double xp;
    /** Multiplies knockback strength and stun durations it receives (the Brute shrugs off half). */
    public final double knockbackMult, stunMult;
    public final double deathPitch;
    public final Color[] splat;

    DemonType(String prefix, double hp, double width, double height, int frameW, int frameH, int anchorX, int anchorY,
              boolean flying, double xp, double knockbackMult, double stunMult, double deathPitch, String... splat) {
        this.prefix = prefix;
        this.hp = hp;
        this.width = width;
        this.height = height;
        this.frameW = frameW;
        this.frameH = frameH;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.flying = flying;
        this.xp = xp;
        this.knockbackMult = knockbackMult;
        this.stunMult = stunMult;
        this.deathPitch = deathPitch;
        this.splat = new Color[splat.length];
        for (int i = 0; i < splat.length; i++) this.splat[i] = Color.web(splat[i]);
    }

    /** Spawn name in {@link at.htl.flowstate.Factories.EnemyFactory}. */
    public String spawnName() {
        return name().toLowerCase();
    }
}
