package at.htl.flowstate.Components.Player.Abilities;

import at.htl.flowstate.Assets;
import at.htl.flowstate.Combat.Combat;
import at.htl.flowstate.Combat.DamageKind;
import at.htl.flowstate.Combat.Hit;
import at.htl.flowstate.Components.Player.Attacks.SpinVisualComponent;
import at.htl.flowstate.Components.Player.LoadoutComponent;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Skills.SkillType;
import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.physics.BoundingShape;
import com.almasb.fxgl.physics.HitBox;
import at.htl.flowstate.Visuals.WeaponArt;
import javafx.scene.image.ImageView;

/** Tap: long swing. Hold: flail spin hitting everything around the player every 0.4 s (max 3 s). */
public class MorningstarAbility extends MeleeAbility {
    private static final MeleeSpec SPEC = MeleeSpec.MORNINGSTAR;
    private static final double HOLD_THRESHOLD = 0.25;
    private static final double SPIN_TICK = 0.4;
    private static final double SPIN_MAX = 3.0;
    private static final double SPIN_RECOVERY = 1.0;
    private static final double SPIN_DAMAGE = 10;
    private static final double SPIN_RADIUS = 140;
    private static final double SPIN_KNOCKBACK = 300;
    private static final double SPIN_IMPACT = 0.4;   // many small hits: keep the freeze frames short

    private boolean buttonDown = false;
    private boolean pressed = false;
    private double pressTime = 0;
    private boolean spinning = false;
    private double spinTime = 0;
    private double tickTimer = 0;
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
        Slash slash = slash(SPEC.hit(facing()), SPEC.swingTime(), false);
        slash.params().windup(0.3).strike(0.24);
        launch(SPEC, slash, 1, 1.2);
        cooldown(SPEC.interval());
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
            if (tickTimer <= 0) {
                tick();
                tickTimer = SPIN_TICK;
            }
            if (spinTime >= SPIN_MAX) endSpin();
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
        ImageView tex = WeaponArt.hanging(SPEC.texture(), SPEC.width() / 2, 0, 1);
        spinVisual = FXGL.entityBuilder()
                .bbox(new HitBox(BoundingShape.box(SPEC.width(), SPEC.reach())))
                .view(tex)
                .with(new SpinVisualComponent())
                .zIndex(13)
                .buildAndAttach();
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

    private void tick() {
        Sfx.SPIN.play();
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
        return spinning ? 1 - spinTime / SPIN_MAX : -1;
    }
}
