package at.htl.flowstate.Components.Player.Abilities;

import at.htl.flowstate.Visuals.FrameSprite;
import at.htl.flowstate.Visuals.Frames;
import com.almasb.fxgl.core.math.FXGLMath;
import at.htl.flowstate.Assets;
import at.htl.flowstate.Combat.DamageKind;
import at.htl.flowstate.Combat.Hit;
import at.htl.flowstate.Components.Player.Attacks.HeldWeaponComponent;
import at.htl.flowstate.Components.Player.Attacks.PlayerProjectileComponent;
import at.htl.flowstate.Components.Player.LoadoutComponent;
import at.htl.flowstate.Skills.Branch;
import at.htl.flowstate.Skills.SkillType;
import com.almasb.fxgl.entity.Entity;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;

/** Basic Bow, Shortbow and Bone Bow: click to shoot an arcing arrow. */
public class BowAbility extends Ability {
    public enum Bow {
        BASIC("BasicBow.png", 16.5, 13, "Arrow.png", 14, 0.6, 1000, 900, 0, false),
        SHORT("ShortBow.png", 13.5, 11, "Arrow.png", 8, 0.25, 900, 500, 0, false),
        BONE("BoneBow.png", 16.5, 13, "BoneArrow.png", 15, 0.6, 1000, 900, 1, true);

        final String texture, arrow;
        final double gripX, gripY; // grip inside a native frame (tools/art/ranged.py)
        final double damage, interval, speed, range;
        final int pierce;
        final boolean shatter;

        Bow(String texture, double gripX, double gripY, String arrow, double damage, double interval, double speed,
            double range, int pierce, boolean shatter) {
            this.texture = texture;
            this.gripX = gripX;
            this.gripY = gripY;
            this.arrow = arrow;
            this.damage = damage;
            this.interval = interval;
            this.speed = speed;
            this.range = range;
            this.pierce = pierce;
            this.shatter = shatter;
        }
    }

    static final double ARROW_GRAVITY = 800;
    static final double EAGLE_EYE_SPEED = 1.25;   // Bow passive
    static final double EAGLE_EYE_GRAVITY = 0.7;
    private static final double SHARD_DAMAGE = 5;
    private static final double SHARD_SPEED = 600;
    private static final double SHARD_RANGE = 120;
    private static final double[] SHARD_ANGLES = {-25, 0, 25};

    private final Bow bow;

    public BowAbility(Entity player, LoadoutComponent loadout, Bow bow) {
        super(player, loadout, Branch.RANGED);
        this.bow = bow;
    }

    @Override
    public void primaryPress() {
        if (!ready()) return;
        PlayerProjectileComponent arrow = new PlayerProjectileComponent(
                aim().multiply(bow.speed * EAGLE_EYE_SPEED),
                pierce(bow.pierce),
                (e, i) -> Hit.of(bow.damage, DamageKind.RANGED).family(SkillType.BOW))
                .gravity(ARROW_GRAVITY * EAGLE_EYE_GRAVITY)
                .maxDistance(bow.range);
        if (bow.shatter) arrow.onStop(this::shatter);
        spawnProjectile(projectileSprite(bow.arrow, 28, 3), 28, 3, arrow);
        // full draw for a moment, then the string snaps back to rest
        Frames frames = Assets.frames(bow.texture, 3, 2);
        FrameSprite held = new FrameSprite(frames).play(frames, new int[]{2, 0}, 16, false, null);
        HeldWeaponComponent.spawnAimed(held, bow.gripX * 2, bow.gripY * 2, 22, 0.3);
        cooldown(bow.interval);
    }

    /** Bone Bow: the arrow splinters into 3 short-range bone shards. */
    private void shatter(Point2D position, Point2D velocity, Entity lastEnemy, boolean terrain) {
        Point2D base = velocity.magnitude() < 1e-3 ? new Point2D(facing(), 0) : velocity.normalize();
        if (terrain) base = base.multiply(-1);
        for (double angle : SHARD_ANGLES) {
            PlayerProjectileComponent shard = new PlayerProjectileComponent(
                    rotate(base, angle).multiply(SHARD_SPEED),
                    pierce(0),
                    (e, i) -> Hit.of(SHARD_DAMAGE, DamageKind.RANGED).family(SkillType.BOW))
                    .maxDistance(SHARD_RANGE)
                    .ignore(lastEnemy)
                    .terrainGrace(terrain ? 40 : 0);
            Frames shards = Assets.frames("BoneShard.png", 3, 2);
            FrameSprite view = new FrameSprite(shards);
            view.show(shards, FXGLMath.random(0, 2));
            view.setTranslateX(4 - shards.width() / 2);
            view.setTranslateY(1.5 - shards.height() / 2);
            spawnProjectile(position, view, 8, 3, shard);
        }
    }
}
