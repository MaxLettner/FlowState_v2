package at.htl.flowstate.Components.Player.Abilities;

import at.htl.flowstate.Assets;
import javafx.geometry.Point2D;
import javafx.scene.image.ImageView;
import at.htl.flowstate.Combat.DamageKind;
import at.htl.flowstate.Combat.Hit;
import at.htl.flowstate.Components.Player.Attacks.HeldWeaponComponent;
import at.htl.flowstate.Components.Player.Attacks.ThrustComponent;
import at.htl.flowstate.Components.Player.LoadoutComponent;
import at.htl.flowstate.Skills.Branch;
import at.htl.flowstate.Skills.SkillType;
import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.physics.BoundingShape;
import com.almasb.fxgl.physics.HitBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/** Fisticuffs: punches, plus dodge (Leather) or block (Metal); Spike Gloves apply Bleed. */
public class GloveAbility extends Ability {
    public enum Glove {
        HAND_WRAPS(8, 0.3, 50, 8, "HandWraps.png", false, false, false),
        LEATHER(6, 0.2, 50, 10, "LeatherGlove.png", true, false, false),
        METAL(12, 0.4, 50, 12, "MetalGlove.png", false, true, false),
        SPIKE(25, 0.8, 45, 15, "SpikeGlove.png", false, false, true);

        final double damage, interval, reach, critPercent;
        final String texture;
        final boolean dodge, block, bleed;

        Glove(double damage, double interval, double reach, double critPercent, String texture,
              boolean dodge, boolean block, boolean bleed) {
            this.damage = damage;
            this.interval = interval;
            this.reach = reach;
            this.critPercent = critPercent;
            this.texture = texture;
            this.dodge = dodge;
            this.block = block;
            this.bleed = bleed;
        }
    }

    private static final double FIST_SIZE = 16;
    private static final double PUNCH_KNOCKBACK = 80;
    private static final double DODGE_INVULNERABILITY = 0.2;
    private static final double DODGE_COOLDOWN = 2.0;

    private final Glove glove;
    private double dodgeCooldown = 0;
    private boolean blocking = false;
    private Entity blockVisual;

    public GloveAbility(Entity player, LoadoutComponent loadout, Glove glove) {
        super(player, loadout, Branch.MELEE);
        this.glove = glove;
    }

    @Override
    public void primaryPress() {
        if (blocking || !ready()) return;

        Hit hit = Hit.of(glove.damage, DamageKind.MELEE)
                .family(SkillType.FISTICUFFS)
                .crit(glove.critPercent)
                .knockback(PUNCH_KNOCKBACK, facing());
        if (glove.bleed) hit.bleed(1);

        ImageView fist = Assets.sprite(glove.texture, 2);
        fist.setTranslateX(FIST_SIZE / 2 - fist.getImage().getWidth() / 2);
        fist.setTranslateY(FIST_SIZE / 2 - fist.getImage().getHeight() / 2);

        Entity punch = FXGL.entityBuilder()
                .bbox(new HitBox(BoundingShape.box(FIST_SIZE, FIST_SIZE)))
                .view(fist)
                .with(new ThrustComponent(hit, glove.reach, Math.min(0.18, glove.interval * 0.9), facing()))
                .zIndex(5)
                .buildAndAttach();
        punch.setScaleOrigin(new Point2D(FIST_SIZE / 2, FIST_SIZE / 2));
        punch.setScaleX(facing());
        cooldown(glove.interval);
    }

    @Override
    public void secondaryPress() {
        if (glove.dodge && dodgeCooldown <= 0) {
            movement().startDash();
            stats().grantInvulnerability(DODGE_INVULNERABILITY);
            dodgeCooldown = DODGE_COOLDOWN;
        }
        if (glove.block && !blocking) {
            blocking = true;
            blockVisual = HeldWeaponComponent.spawn(Assets.sprite("MetalGuard.png", 2), 20, 56, 26, 0, 0);
        }
    }

    @Override
    public void secondaryRelease() {
        stopBlocking();
    }

    @Override
    public void onDeselect() {
        stopBlocking();
    }

    private void stopBlocking() {
        blocking = false;
        if (blockVisual != null) {
            blockVisual.removeFromWorld();
            blockVisual = null;
        }
    }

    @Override
    public void update(double tpf) {
        if (dodgeCooldown > 0) dodgeCooldown -= tpf;
    }

    @Override
    public boolean isBlocking() {
        return blocking;
    }

    @Override
    public double getMoveSpeedMult() {
        return blocking ? 0.5 : 1;
    }

    @Override
    public String getStatusText() {
        if (blocking) return "blocking";
        if (glove.dodge && dodgeCooldown > 0) return String.format("dodge %.1fs", dodgeCooldown);
        return "";
    }
}
