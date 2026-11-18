package at.htl.flowstate.Components.Player.Abilities;

import at.htl.flowstate.Assets;
import at.htl.flowstate.Visuals.FrameSprite;
import at.htl.flowstate.Visuals.Frames;
import at.htl.flowstate.Components.Player.Attacks.PlayerProjectileComponent;
import at.htl.flowstate.Components.Player.LoadoutComponent;
import at.htl.flowstate.Components.Player.PlayerMovementComponent;
import at.htl.flowstate.Components.Player.PlayerStatsComponent;
import at.htl.flowstate.Skills.Branch;
import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.physics.BoundingShape;
import com.almasb.fxgl.physics.HitBox;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/** Behaviour of one hotbar attack (weapon or attack spell). */
public abstract class Ability {
    protected final Entity player;
    protected final LoadoutComponent loadout;
    protected final Branch branch;

    protected Ability(Entity player, LoadoutComponent loadout, Branch branch) {
        this.player = player;
        this.loadout = loadout;
        this.branch = branch;
    }

    //-----Input hooks-----
    public void primaryPress() {}
    public void primaryRelease() {}
    public void secondaryPress() {}
    public void secondaryRelease() {}

    /** Called every frame, also while the ability is not selected (thrown tridents, slams). */
    public void update(double tpf) {}

    /** Called when another hotbar slot becomes the active attack. Cancel charges here. */
    public void onDeselect() {}

    //-----State for HUD / movement-----
    public double getChargeProgress() { return -1; }
    public double getMoveSpeedMult() { return 1; }
    public boolean isBlocking() { return false; }
    public String getStatusText() { return ""; }

    /**
     * Hotbar fill for an ability with its own reload instead of the branch cooldown:
     * 1 = empty, 0 = ready. -1 means "use the branch cooldown".
     */
    public double getSlotCooldown() { return -1; }
    /** Full length in seconds of {@link #getSlotCooldown}'s reload (for the ready flash). */
    public double getSlotCooldownTotal() { return 0; }

    //-----Helpers-----
    protected boolean ready() { return loadout.isReady(branch); }
    protected void cooldown(double seconds) { loadout.setCooldown(branch, seconds); }
    protected PlayerStatsComponent stats() { return player.getComponent(PlayerStatsComponent.class); }
    protected PlayerMovementComponent movement() { return player.getComponent(PlayerMovementComponent.class); }
    protected int facing() { return movement().getCurrentWatchDirection(); }

    /** Normalized direction from the player to the mouse cursor. */
    protected Point2D aim() {
        Point2D v = FXGL.getInput().getMousePositionWorld().subtract(player.getCenter());
        return v.magnitude() < 1e-3 ? new Point2D(facing(), 0) : v.normalize();
    }

    /** Applies the Piercing enchantment to a base pierce value (-1 = infinite stays infinite). */
    protected int pierce(int base) {
        return base < 0 ? base : base + loadout.getPierceBonus(false);
    }

    protected Entity spawnProjectile(Point2D at, Node view, double width, double height, PlayerProjectileComponent projectile) {
        Entity e = FXGL.entityBuilder()
                .at(at.subtract(width / 2, height / 2))
                .bbox(new HitBox(BoundingShape.box(width, height)))
                .view(view)
                .with(projectile)
                .zIndex(-1)
                .build();
        e.setRotationOrigin(new Point2D(width / 2, height / 2));
        FXGL.getGameWorld().addEntity(e);
        return e;
    }

    /**
     * A projectile sprite (pointing right) centred on a width x height hitbox.
     * (anchorX, anchorY) is the point of the native frame that sits on the hitbox centre, or NaN for the frame centre.
     */
    protected static Node projectileSprite(String texture, int frames, double fps, int scale,
                                           double width, double height, double anchorX, double anchorY) {
        Frames f = Assets.frames(texture, frames, scale);
        FrameSprite sprite = new FrameSprite(f);
        if (frames > 1 && fps > 0) sprite.loop(f, fps);
        double ax = Double.isNaN(anchorX) ? f.width() / 2 : (anchorX + 0.5) * scale;
        double ay = Double.isNaN(anchorY) ? f.height() / 2 : (anchorY + 0.5) * scale;
        sprite.setTranslateX(width / 2 - ax);
        sprite.setTranslateY(height / 2 - ay);
        return sprite;
    }

    protected static Node projectileSprite(String texture, double width, double height) {
        return projectileSprite(texture, 1, 0, 2, width, height, Double.NaN, Double.NaN);
    }

    protected Entity spawnProjectile(Node view, double width, double height, PlayerProjectileComponent projectile) {
        return spawnProjectile(player.getCenter(), view, width, height, projectile);
    }

    protected static Rectangle rect(double width, double height, Color color) {
        return new Rectangle(width, height, color);
    }

    protected static Point2D rotate(Point2D v, double degrees) {
        double r = Math.toRadians(degrees);
        double cos = Math.cos(r), sin = Math.sin(r);
        return new Point2D(v.getX() * cos - v.getY() * sin, v.getX() * sin + v.getY() * cos);
    }
}
