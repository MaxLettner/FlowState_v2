package at.htl.flowstate.Components.Player.Abilities;

import at.htl.flowstate.Components.Player.LoadoutComponent;
import com.almasb.fxgl.entity.Entity;

/** Two katanas that alternate (overhead / rising, from two hands); swings last longer than the interval so they overlap. */
public class DualWieldAbility extends MeleeAbility {
    private static final MeleeSpec SPEC = MeleeSpec.DUAL_WIELDING;
    private static final double HAND_OFFSET = 7;
    private boolean rightBlade = false;

    public DualWieldAbility(Entity player, LoadoutComponent loadout) {
        super(player, loadout);
    }

    @Override
    public void primaryPress() {
        press(() -> {
            Slash slash = slash(SPEC.hit(facing()), SPEC.swingTime(), rightBlade);
            slash.params().pivotOffset(0, rightBlade ? HAND_OFFSET : -HAND_OFFSET).windup(0.15).strike(0.3);
            launch(SPEC, slash, 1, 0.6);
            rightBlade = !rightBlade;
            cooldown(SPEC.interval());
        });
    }
}
