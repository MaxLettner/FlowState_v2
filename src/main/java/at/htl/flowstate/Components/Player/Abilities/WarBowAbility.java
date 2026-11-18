package at.htl.flowstate.Components.Player.Abilities;

import at.htl.flowstate.Visuals.FrameSprite;
import at.htl.flowstate.Visuals.Frames;
import at.htl.flowstate.Assets;
import at.htl.flowstate.Combat.DamageKind;
import at.htl.flowstate.Combat.Hit;
import at.htl.flowstate.Components.Player.Attacks.HeldWeaponComponent;
import at.htl.flowstate.Components.Player.Attacks.PlayerProjectileComponent;
import at.htl.flowstate.Components.Player.LoadoutComponent;
import at.htl.flowstate.Skills.Branch;
import at.htl.flowstate.Skills.SkillType;
import com.almasb.fxgl.entity.Entity;
import javafx.scene.paint.Color;

/** Hold to draw; damage, speed and pierce scale with draw time (0.3 s min, 1.5 s full). */
public class WarBowAbility extends Ability {
    private static final double MIN_DRAW = 0.3;
    private static final double FULL_DRAW = 1.5;
    private static final double RELEASE_COOLDOWN = 0.5;

    private boolean drawing = false;
    private double drawTime = 0;
    private Entity heldBow;
    private Frames bowFrames;
    private FrameSprite bowSprite;

    public WarBowAbility(Entity player, LoadoutComponent loadout) {
        super(player, loadout, Branch.RANGED);
    }

    @Override
    public void primaryPress() {
        if (!ready() || drawing) return;
        drawing = true;
        drawTime = 0;
        bowFrames = Assets.frames("WarBow.png", 3, 2);
        bowSprite = new FrameSprite(bowFrames);
        heldBow = HeldWeaponComponent.spawnAimed(bowSprite, 20.5 * 2, 16 * 2, 24, 0);
    }

    @Override
    public void update(double tpf) {
        if (drawing) {
            drawTime += tpf;
            // rest -> half -> full draw as the charge builds
            bowSprite.show(bowFrames, drawTime < MIN_DRAW ? 0 : drawTime < FULL_DRAW ? 1 : 2);
        }
    }

    @Override
    public void primaryRelease() {
        if (!drawing) return;
        stopDrawing();

        double damage, speed, gravity;
        int pierce;
        if (drawTime < MIN_DRAW) { // weak, early release
            damage = 10;
            speed = 600;
            pierce = 0;
            gravity = BowAbility.ARROW_GRAVITY;
        } else {
            double f = (Math.min(drawTime, FULL_DRAW) - MIN_DRAW) / (FULL_DRAW - MIN_DRAW);
            damage = 20 + 40 * f;
            speed = 900 + 900 * f;
            pierce = (int) Math.round(1 + 2 * f);
            gravity = BowAbility.ARROW_GRAVITY * (1 - 0.85 * f);
        }
        final double finalDamage = damage;
        PlayerProjectileComponent arrow = new PlayerProjectileComponent(
                aim().multiply(speed * BowAbility.EAGLE_EYE_SPEED),
                pierce(pierce),
                (e, i) -> Hit.of(finalDamage, DamageKind.RANGED).family(SkillType.BOW))
                .gravity(gravity * BowAbility.EAGLE_EYE_GRAVITY);
        spawnProjectile(projectileSprite("WarArrow.png", 36, 4), 36, 4, arrow);
        cooldown(RELEASE_COOLDOWN);
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
            heldBow.getComponent(HeldWeaponComponent.class).setLifetime(0.2);
            heldBow = null;
        }
    }

    @Override
    public double getChargeProgress() {
        return drawing ? Math.min(drawTime / FULL_DRAW, 1) : -1;
    }

    @Override
    public double getMoveSpeedMult() {
        return drawing ? 0.5 : 1;
    }
}
