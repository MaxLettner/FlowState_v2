package at.htl.flowstate.Components.Player.Abilities;

import at.htl.flowstate.Combat.Hit;
import at.htl.flowstate.Components.Player.Attacks.MeleeSwingComponent;
import at.htl.flowstate.Components.Player.LoadoutComponent;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.entity.Entity;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;

/**
 * Tap: slow long swing. Hold: the blade is raised and trembles while charging a heavy cleave
 * (up to 90 dmg, bigger arc, more knockback). Releasing right as the charge completes (the blade flashes)
 * is a perfect release: it counts as an on-beat attack for flow and hits much harder visually.
 */
public class ZweihanderAbility extends MeleeAbility {
    private static final MeleeSpec SPEC = MeleeSpec.ZWEIHANDER;
    private static final double TAP_THRESHOLD = 0.25;
    private static final double FULL_CHARGE = 1.5;
    private static final double PERFECT_WINDOW = 0.22;
    private static final double PERFECT_IMPACT = 1.5;

    private boolean buttonDown = false;
    private boolean charging = false;
    private double chargeTime = 0;
    private boolean flashed = false;
    private Entity pose;

    public ZweihanderAbility(Entity player, LoadoutComponent loadout) {
        super(player, loadout);
    }

    @Override
    public void primaryPress() {
        buttonDown = true;
        press(() -> {
            if (!buttonDown) { // buffered tap, already released
                tapSwing();
                cooldown(SPEC.interval());
                return;
            }
            charging = true;
            chargeTime = 0;
            flashed = false;
        });
    }

    private void tapSwing() {
        Slash slash = slash(SPEC.hit(facing()), SPEC.swingTime(), false);
        slash.params().windup(0.3).strike(0.22);
        launch(SPEC, slash);
    }

    @Override
    public void update(double tpf) {
        super.update(tpf);
        if (!charging) return;
        chargeTime += tpf;

        if (chargeTime >= TAP_THRESHOLD && pose == null) {
            // raised blade while charging; the facing is locked so the pose can't flip
            pose = SPEC.swing(slash(SPEC.hit(facing()), 1, false).params().held(), 1);
            // the hum rises until the blade flashes
            Sfx.CHARGE_WEAPON.start(false, 1.5 / (FULL_CHARGE - TAP_THRESHOLD));
        }
        if (pose != null) {
            double f = Math.min(chargeTime / FULL_CHARGE, 1);
            pose.getComponent(MeleeSwingComponent.class).setTremble(f);
            movement().faceAttack(facing(), 0.1);
        }
        if (!flashed && chargeTime >= FULL_CHARGE) {
            flashed = true;
            Sfx.CHARGE_READY.play();
            Particles.glint(hand(), new Color[]{Color.WHITE, Color.web("#fff3a0")}, 14);
        }
    }

    @Override
    public void primaryRelease() {
        buttonDown = false;
        if (!charging) return;
        charging = false;
        removePose();
        if (chargeTime < TAP_THRESHOLD) {
            tapSwing();
        } else {
            double f = Math.min(chargeTime / FULL_CHARGE, 1);
            boolean perfect = chargeTime >= FULL_CHARGE && chargeTime <= FULL_CHARGE + PERFECT_WINDOW;
            double damage = 35 + 55 * f;
            double knockback = 400 + 300 * f;
            double size = (160 + 20 * f) / 160;
            if (perfect) {
                flow = Math.min(FLOW_MAX, flow + 1);
                Sfx.PERFECT.play();
                Particles.text(new Point2D(player.getCenter().getX(), player.getY() - 10), "PERFECT", Color.web("#62e0ff"), 20);
            }
            Hit hit = SPEC.hit(facing()).withDamage(damage).knockback(knockback, facing())
                    .impact(1 + 0.4 * f + (perfect ? PERFECT_IMPACT - 1 : 0));
            // already wound up while charging: almost no windup, a fast heavy strike
            Slash slash = slash(hit, 0.6, false);
            slash.params().windup(0.06).strike(0.26);
            launch(SPEC, slash, size);
        }
        cooldown(SPEC.interval());
    }

    private void removePose() {
        Sfx.CHARGE_WEAPON.stop();
        if (pose != null) {
            pose.removeFromWorld();
            pose = null;
        }
    }

    @Override
    public void onDeselect() {
        super.onDeselect();
        charging = false;
        buttonDown = false;
        removePose();
    }

    @Override
    public double getChargeProgress() {
        return charging ? Math.min(chargeTime / FULL_CHARGE, 1) : -1;
    }

    @Override
    public double getMoveSpeedMult() {
        return charging ? 0.5 : super.getMoveSpeedMult();
    }
}
