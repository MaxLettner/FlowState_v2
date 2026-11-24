package at.htl.flowstate.Components.Player.Abilities;

import at.htl.flowstate.Combat.Combat;
import at.htl.flowstate.Combat.DamageKind;
import at.htl.flowstate.Combat.Hit;
import at.htl.flowstate.Components.Player.Attacks.FlailSpinComponent;
import at.htl.flowstate.Components.Player.Attacks.FlailWhipComponent;
import at.htl.flowstate.Components.Player.Attacks.MeleeSwingComponent;
import at.htl.flowstate.Components.Player.LoadoutComponent;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Skills.Branch;
import at.htl.flowstate.Skills.SkillManager;
import at.htl.flowstate.Skills.SkillType;
import com.almasb.fxgl.entity.Entity;

/**
 * A spiked ball on a chain. Tap: lashes it like a whip. Hold: whirls it around the player, hitting everything in
 * reach every 0.4 s, faster the longer it spins. Strength stretches the longest spin from 3 s (rank 0) to 9 s (rank 10).
 */
public class MorningstarAbility extends MeleeAbility {
    private static final MeleeSpec SPEC = MeleeSpec.MORNINGSTAR;
    private static final double HOLD_THRESHOLD = 0.25;
    private static final double WHIP_FROM = -120, WHIP_TO = 8;   // overhead behind -> straight out, just below level
    private static final double WHIP_REACH = 1.4;                // the lash reaches further than the old staff
    private static final double SPIN_TICK = 0.4;
    private static final double SPIN_MAX = 3.0;
    private static final double SPIN_MAX_PER_STRENGTH = 0.2;   // +20% spin time per Strength rank: x3 at rank 10
    private static final double SPIN_ACCEL = 1 / 3.0;          // speed gained per second of spinning
    private static final double SPIN_SPEED_MAX = 3;
    private static final double SPIN_RECOVERY = 1.0;
    private static final double SPIN_DAMAGE = 10;
    private static final double SPIN_RADIUS = 210;
    private static final double SPIN_KNOCKBACK = 300;
    private static final double SPIN_IMPACT = 0.4;   // many small hits: keep the freeze frames short

    private boolean buttonDown = false;
    private boolean pressed = false;
    private double pressTime = 0;
    private boolean spinning = false;
    private double spinTime = 0;
    private double tickTimer = 0;
    private double spinMax = SPIN_MAX;
    private Entity spinVisual;

    public MorningstarAbility(Entity player, LoadoutComponent loadout) {
        super(player, loadout);
    }

    @Override
    public void primaryPress() {
        if (spinning) return;
        buttonDown = true;
        press(() -> {
            if (!buttonDown) { // buffered tap, already released
                tapSwing();
                return;
            }
            pressed = true;
            pressTime = 0;
        });
    }

    private void tapSwing() {
        // a heavy flail: wide arc, slow pull back, whipping strike
        Slash slash = slash(SPEC.hit(facing()), SPEC.swingTime(), false, WHIP_FROM, WHIP_TO);
        slash.params().windup(0.3).strike(0.24);
        launch(SPEC, slash);
        cooldown(SPEC.interval());
    }

    @Override
    protected Entity spawnSwing(MeleeSpec spec, MeleeSwingComponent.Params params, double size) {
        params.sound(Sfx.FLAIL_SWING, 1);
        return FlailWhipComponent.spawn(params, spec.reach() * WHIP_REACH * size);
    }

    @Override
    public void update(double tpf) {
        super.update(tpf);
        if (pressed && !spinning) {
            pressTime += tpf;
            if (pressTime >= HOLD_THRESHOLD) startSpin();
        }
        if (spinning) {
            spinTime += tpf;
            tickTimer -= tpf;
            double speed = spinSpeed();
            spinVisual.getComponent(FlailSpinComponent.class).setSpeed(speed);
            if (tickTimer <= 0) {
                tick(speed);
                tickTimer = SPIN_TICK / speed;
            }
            if (spinTime >= spinMax) endSpin();
        }
    }

    @Override
    public void primaryRelease() {
        buttonDown = false;
        if (spinning) {
            endSpin();
        } else if (pressed) {
            tapSwing();
        }
        pressed = false;
    }

    @Override
    public void onDeselect() {
        super.onDeselect();
        if (spinning) endSpin();
        pressed = false;
        buttonDown = false;
    }

    private void startSpin() {
        spinning = true;
        spinTime = 0;
        tickTimer = 0;
        spinMax = SPIN_MAX * (1 + SPIN_MAX_PER_STRENGTH * SkillManager.get().getRank(Branch.MELEE));
        spinVisual = FlailSpinComponent.spawn(SPIN_RADIUS);
    }

    /** Spin speed as a multiple of the start: climbs the longer the spin is held. */
    private double spinSpeed() {
        return Math.min(SPIN_SPEED_MAX, 1 + spinTime * SPIN_ACCEL);
    }

    private void endSpin() {
        spinning = false;
        pressed = false;
        if (spinVisual != null) {
            spinVisual.removeFromWorld();
            spinVisual = null;
        }
        cooldown(SPIN_RECOVERY);
    }

    private void tick(double speed) {
        Sfx.SPIN.play(1, 0.9 + 0.1 * speed);
        double x = player.getCenter().getX();
        for (Entity e : Combat.enemiesInRadius(player.getCenter(), SPIN_RADIUS)) {
            Combat.hit(e, Hit.of(SPIN_DAMAGE, DamageKind.MELEE)
                    .family(SkillType.BLUNT)
                    .crit(SPEC.critPercent())
                    .knockbackFrom(SPIN_KNOCKBACK, x)
                    .impact(SPIN_IMPACT));
        }
    }

    @Override
    public double getMoveSpeedMult() {
        return spinning ? 0.5 : super.getMoveSpeedMult();
    }

    @Override
    public double getChargeProgress() {
        return spinning ? 1 - spinTime / spinMax : -1;
    }
}
