package at.htl.flowstate.Components.Player.Abilities;

import at.htl.flowstate.Assets;
import at.htl.flowstate.Combat.DamageKind;
import at.htl.flowstate.Combat.Hit;
import at.htl.flowstate.Components.Enemies.EnemyStatsComponent;
import at.htl.flowstate.Components.Player.Attacks.BlastComponent;
import at.htl.flowstate.Components.Player.Attacks.ThrownTridentComponent;
import at.htl.flowstate.Components.Player.Attacks.ThrustComponent;
import at.htl.flowstate.Components.Player.LoadoutComponent;
import at.htl.flowstate.Skills.Branch;
import at.htl.flowstate.Skills.SkillType;
import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.physics.BoundingShape;
import com.almasb.fxgl.physics.HitBox;
import at.htl.flowstate.Visuals.WeaponArt;
import javafx.scene.image.ImageView;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;

/** All tridents: LMB stab, RMB throw (Recall: RMB again recalls, Heavy: hold RMB to wind up). */
public class TridentAbility extends Ability implements ThrownTridentComponent.Owner {
    public enum Variant {
        BASIC("BasicTrident.png", 30, 30, 15, 0.6, 25, 1300, 1),
        RECALL("RecallTrident.png", 30, 30, 18, 0.6, 22, 1300, 0),
        ICE("IceTrident.png", 10, 30, 15, 0.6, 20, 1300, 1),
        HEAVY("HeavyTrident.png", 30, 30, 35, 1.2, 45, 1000, -1);

        final String texture;
        final double width, height;
        final double stabDamage, stabInterval, throwDamage, throwSpeed;
        final int pierce;

        Variant(String texture, double width, double height,
                double stabDamage, double stabInterval, double throwDamage, double throwSpeed, int pierce) {
            this.texture = texture;
            this.width = width;
            this.height = height;
            this.stabDamage = stabDamage;
            this.stabInterval = stabInterval;
            this.throwDamage = throwDamage;
            this.throwSpeed = throwSpeed;
            this.pierce = pierce;
        }
    }

    private static final double STAB_REACH = 120;
    private static final double STAB_WIDTH = 70;
    private static final double STAB_HEIGHT = 12;
    private static final double STAB_KNOCKBACK = 150;
    private static final double THROW_COOLDOWN = 0.3;
    private static final double ICE_STAB_SLOW = 2.0;
    private static final double ICE_FREEZE = 1.5;
    private static final double ICE_FREEZE_RADIUS = 150;
    private static final double RECALL_RETURN_DAMAGE = 15;
    private static final double HEAVY_MIN_WINDUP = 0.6;
    private static final double HEAVY_FULL_WINDUP = 1.5;

    private final Variant variant;
    private ThrownTridentComponent thrown;
    private boolean reunion = false; // Trident passive: next stab after pickup deals double damage
    private boolean windingUp = false;
    private double windupTime = 0;

    public TridentAbility(Entity player, LoadoutComponent loadout, Variant variant) {
        super(player, loadout, Branch.RANGED);
        this.variant = variant;
    }

    //-----Stab-----
    @Override
    public void primaryPress() {
        if (thrown != null || windingUp || !ready()) return;

        double damage = variant.stabDamage * (reunion ? 2 : 1);
        reunion = false;
        Hit hit = Hit.of(damage, DamageKind.RANGED).family(SkillType.TRIDENT).knockback(STAB_KNOCKBACK, facing());
        if (variant == Variant.ICE) hit.slow(ICE_STAB_SLOW);

        ImageView tex = WeaponArt.tipAt(variant.texture, STAB_WIDTH + 10, STAB_HEIGHT / 2, 1);

        Entity stab = FXGL.entityBuilder()
                .bbox(new HitBox(BoundingShape.box(STAB_WIDTH, STAB_HEIGHT)))
                .view(tex)
                .with(new ThrustComponent(hit, STAB_REACH - STAB_WIDTH, 0.25, facing()))
                .zIndex(5)
                .buildAndAttach();
        stab.setScaleOrigin(new Point2D(STAB_WIDTH / 2, STAB_HEIGHT / 2));
        stab.setScaleX(facing());
        sprite().playAttack();
        cooldown(variant.stabInterval);
    }

    //-----Throw-----
    @Override
    public void secondaryPress() {
        if (thrown != null) {
            if (variant == Variant.RECALL) thrown.recall();
            return;
        }
        if (variant == Variant.HEAVY) {
            windingUp = true;
            windupTime = 0;
        } else {
            throwTrident(variant.throwDamage, variant.throwSpeed);
        }
    }

    @Override
    public void secondaryRelease() {
        if (!windingUp) return;
        windingUp = false;
        if (windupTime < HEAVY_MIN_WINDUP) return; // released too early: cancelled
        double f = Math.min((windupTime - HEAVY_MIN_WINDUP) / (HEAVY_FULL_WINDUP - HEAVY_MIN_WINDUP), 1);
        throwTrident(45 + 45 * f, 1000 + 600 * f);
    }

    private void throwTrident(double damage, double speed) {
        int pierce = variant.pierce < 0 ? -1
                : variant.pierce + loadout.getPierceBonus(variant == Variant.RECALL);
        Hit throwHit = Hit.of(damage, DamageKind.RANGED).family(SkillType.TRIDENT);
        Hit returnHit = Hit.of(RECALL_RETURN_DAMAGE, DamageKind.RANGED).family(SkillType.TRIDENT);

        ImageView tex = WeaponArt.centred(variant.texture, variant.width / 2, variant.height / 2, 1);

        thrown = new ThrownTridentComponent(this, aim().multiply(speed), pierce, throwHit, returnHit);
        FXGL.entityBuilder()
                .at(player.getCenter())
                .bbox(new HitBox(BoundingShape.box(variant.width, variant.height)))
                .view(tex)
                .with(thrown)
                .zIndex(-1)
                .buildAndAttach();
        sprite().playShoot();
        cooldown(THROW_COOLDOWN);
    }

    @Override
    public void onLanded(Point2D position) {
        if (variant != Variant.ICE) return;
        BlastComponent.spawn(position, ICE_FREEZE_RADIUS, 0.15, 0.3, BlastComponent.Style.ICE,
                e -> e.getComponent(EnemyStatsComponent.class).freeze(ICE_FREEZE));
    }

    @Override
    public void onPickedUp() {
        thrown = null;
        reunion = true;
    }

    @Override
    public void update(double tpf) {
        if (windingUp) windupTime += tpf;
    }

    @Override
    public void onDeselect() {
        windingUp = false;
    }

    @Override
    public double getChargeProgress() {
        return windingUp ? Math.min(windupTime / HEAVY_FULL_WINDUP, 1) : -1;
    }

    @Override
    public double getMoveSpeedMult() {
        return windingUp ? 0.5 : 1;
    }
}
