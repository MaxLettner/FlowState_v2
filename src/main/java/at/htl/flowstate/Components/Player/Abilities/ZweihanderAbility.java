package at.htl.flowstate.Components.Player.Abilities;

import at.htl.flowstate.Components.Player.LoadoutComponent;
import at.htl.flowstate.Skills.Branch;
import com.almasb.fxgl.entity.Entity;

/** Tap: slow long swing. Hold: charge a heavy cleave (up to 90 dmg, bigger arc, more knockback). */
public class ZweihanderAbility extends Ability {
    private static final MeleeSpec SPEC = MeleeSpec.ZWEIHANDER;
    private static final double TAP_THRESHOLD = 0.25;
    private static final double FULL_CHARGE = 1.5;

    private boolean charging = false;
    private double chargeTime = 0;

    public ZweihanderAbility(Entity player, LoadoutComponent loadout) {
        super(player, loadout, Branch.MELEE);
    }

    @Override
    public void primaryPress() {
        if (!ready()) return;
        charging = true;
        chargeTime = 0;
    }

    @Override
    public void update(double tpf) {
        if (charging) chargeTime += tpf;
    }

    @Override
    public void primaryRelease() {
        if (!charging) return;
        charging = false;
        if (chargeTime < TAP_THRESHOLD) {
            SPEC.swing(SPEC.hit(facing()), SPEC.swingTime(), false, 1);
        } else {
            double f = Math.min(chargeTime / FULL_CHARGE, 1);
            double damage = 35 + 55 * f;
            double knockback = 400 + 300 * f;
            double size = (160 + 20 * f) / 160;
            SPEC.swing(SPEC.hit(facing()).withDamage(damage).knockback(knockback, facing()), 0.6, false, size);
        }
        cooldown(SPEC.interval());
    }

    @Override
    public void onDeselect() {
        charging = false;
    }

    @Override
    public double getChargeProgress() {
        return charging ? Math.min(chargeTime / FULL_CHARGE, 1) : -1;
    }

    @Override
    public double getMoveSpeedMult() {
        return charging ? 0.5 : 1;
    }
}
