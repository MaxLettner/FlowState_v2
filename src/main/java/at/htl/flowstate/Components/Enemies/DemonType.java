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
            "#b8ff6a", "#6a4c88", "#22a82e");

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
