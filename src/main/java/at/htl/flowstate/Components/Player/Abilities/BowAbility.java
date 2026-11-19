package at.htl.flowstate.Components.Player.Abilities;

import at.htl.flowstate.Visuals.FrameSprite;
import at.htl.flowstate.Visuals.Frames;
import com.almasb.fxgl.core.math.FXGLMath;
import at.htl.flowstate.Assets;
import at.htl.flowstate.Combat.DamageKind;
import at.htl.flowstate.Combat.Hit;
import at.htl.flowstate.Components.Player.Attacks.PlayerProjectileComponent;
import at.htl.flowstate.Components.Player.LoadoutComponent;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Skills.SkillType;
import com.almasb.fxgl.entity.Entity;
import javafx.geometry.Point2D;

/**
 * Basic Bow, Shortbow and Bone Bow: hold to draw, release to shoot an arcing arrow.
 * A partial draw shoots a weaker arrow that is slower, so it drops sooner and doesn't fly as far.
 * Arrows fly until they hit an enemy or terrain, or leave the screen.
 */
public class BowAbility extends DrawnBowAbility {
    public enum Bow {
        BASIC("BasicBow.png", 33, 26, "Arrow.png", 14, 0.8, 1000, 0, false),
        SHORT("ShortBow.png", 27, 22, "Arrow.png", 8, 0.4, 900, 0, false),
        BONE("BoneBow.png", 33, 26, "BoneArrow.png", 15, 0.8, 1000, 1, true);

        final String texture, arrow;
        final double gripX, gripY; // grip inside a native frame (tools/art/ranged.py)
        final double damage, fullDraw, speed;   // stats at full draw
        final int pierce;
        final boolean shatter;

        Bow(String texture, double gripX, double gripY, String arrow, double damage, double fullDraw, double speed,
            int pierce, boolean shatter) {
            this.texture = texture;
            this.gripX = gripX;
            this.gripY = gripY;
            this.arrow = arrow;
            this.damage = damage;
            this.fullDraw = fullDraw;
            this.speed = speed;
            this.pierce = pierce;
            this.shatter = shatter;
        }
    }

    static final double ARROW_GRAVITY = 800;
    static final double EAGLE_EYE_SPEED = 1.25;   // Bow passive
    static final double EAGLE_EYE_GRAVITY = 0.7;
    // a partial draw: damage scales linearly (so tapping is never better than drawing), speed keeps a floor
    static final double MIN_POWER = 0.1;
    private static final double MIN_SPEED = 0.35;
    private static final double SHARD_DAMAGE = 5;
    private static final double SHARD_SPEED = 600;
    private static final double SHARD_RANGE = 120;
    private static final double[] SHARD_ANGLES = {-25, 0, 25};

    private final Bow bow;

    public BowAbility(Entity player, LoadoutComponent loadout, Bow bow) {
        super(player, loadout, bow.texture, bow.gripX, bow.gripY, 22);
        this.bow = bow;
    }

    @Override
    protected double fullDraw() {
        return bow.fullDraw;
    }

    @Override
    protected double twangPitch() {
        return bow == Bow.SHORT ? 1.25 : bow == Bow.BONE ? 0.9 : 1;
    }

    @Override
    protected void release(double drawTime) {
        double f = Math.min(drawTime / bow.fullDraw, 1);
        double damage = bow.damage * Math.max(MIN_POWER, f);
        double speed = bow.speed * (MIN_SPEED + (1 - MIN_SPEED) * f);
        int basePierce = f >= 1 ? bow.pierce : 0;   // only a full draw pierces
        PlayerProjectileComponent arrow = new PlayerProjectileComponent(
                aim().multiply(speed * EAGLE_EYE_SPEED),
                pierce(basePierce),
                (e, i) -> Hit.of(damage, DamageKind.RANGED).family(SkillType.BOW))
                .gravity(ARROW_GRAVITY * EAGLE_EYE_GRAVITY);
        if (bow.shatter) arrow.onStop(this::shatter);
        else arrow.terrainSound(Sfx.ARROW_THUNK, 1);
        spawnProjectile(projectileSprite(bow.arrow, 28, 3), 28, 3, arrow);
    }

    /** Bone Bow: the arrow splinters into 3 short-range bone shards. */
    private void shatter(Point2D position, Point2D velocity, Entity lastEnemy, boolean terrain) {
        Sfx.BONE_SHATTER.at(position);
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
