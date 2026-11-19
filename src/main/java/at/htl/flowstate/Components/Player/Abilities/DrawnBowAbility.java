package at.htl.flowstate.Components.Player.Abilities;

import at.htl.flowstate.Assets;
import at.htl.flowstate.Components.Player.Attacks.HeldWeaponComponent;
import at.htl.flowstate.Components.Player.LoadoutComponent;
import at.htl.flowstate.Skills.Branch;
import at.htl.flowstate.Visuals.FrameSprite;
import at.htl.flowstate.Visuals.Frames;
import com.almasb.fxgl.entity.Entity;

/**
 * Every bow: hold to draw, release to shoot. There is no cooldown; the draw is the rate limit.
 * A partial draw shoots a slower, weaker arrow that doesn't fly as far. The player is slowed while drawing.
 */
public abstract class DrawnBowAbility extends Ability {
    private static final double DRAW_MOVE_MULT = 0.5;
    private static final double HALF_FRAME_AT = 0.2;   // draw fraction where the sprite switches to half drawn
    private static final double RELEASE_SHOW = 0.2;    // seconds the bow stays out after the shot
    private static final int SCALE = 4;                // pixel upscale of the held bow sprite

    private final String texture;
    private final double gripX, gripY, offset;
    private boolean drawing = false;
    private double drawTime = 0;
    private Entity heldBow;
    private Frames bowFrames;
    private FrameSprite bowSprite;

    /** @param gripX grip inside a native frame (tools/art/ranged.py), @param offset distance from the body */
    protected DrawnBowAbility(Entity player, LoadoutComponent loadout, String texture, double gripX, double gripY, double offset) {
        super(player, loadout, Branch.RANGED);
        this.texture = texture;
        this.gripX = gripX;
        this.gripY = gripY;
        this.offset = offset;
    }

    /** Seconds for a full draw. */
    protected abstract double fullDraw();

    /** Shoots the arrow. {@code drawTime} is how long the bow was drawn, uncapped. */
    protected abstract void release(double drawTime);

    @Override
    public void primaryPress() {
        if (!ready() || drawing) return;
        drawing = true;
        drawTime = 0;
        bowFrames = Assets.frames(texture, 3, SCALE);
        bowSprite = new FrameSprite(bowFrames);
        heldBow = HeldWeaponComponent.spawnAimed(bowSprite, gripX * SCALE, gripY * SCALE, offset, 0);
    }

    @Override
    public void update(double tpf) {
        if (!drawing) return;
        drawTime += tpf;
        double f = drawFraction();
        // rest -> half -> full draw as the charge builds
        bowSprite.show(bowFrames, f < HALF_FRAME_AT ? 0 : f < 1 ? 1 : 2);
    }

    @Override
    public void primaryRelease() {
        if (!drawing) return;
        stopDrawing();
        release(drawTime);
        sprite().playShoot();
    }

    @Override
    public void onDeselect() {
        stopDrawing();
    }

    private void stopDrawing() {
        drawing = false;
        if (heldBow != null) {
            // show the released (rest) frame briefly before putting the bow away
            bowSprite.show(bowFrames, 0);
            heldBow.getComponent(HeldWeaponComponent.class).setLifetime(RELEASE_SHOW);
            heldBow = null;
        }
    }

    private double drawFraction() {
        return Math.min(drawTime / fullDraw(), 1);
    }

    @Override
    public double getChargeProgress() {
        return drawing ? drawFraction() : -1;
    }

    @Override
    public double getMoveSpeedMult() {
        return drawing ? DRAW_MOVE_MULT : 1;
    }
}
