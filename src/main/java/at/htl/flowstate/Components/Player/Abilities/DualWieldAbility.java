package at.htl.flowstate.Components.Player.Abilities;

import at.htl.flowstate.Components.Player.LoadoutComponent;
import at.htl.flowstate.Skills.Branch;
import com.almasb.fxgl.entity.Entity;

/** Two katanas that alternate; the swing lasts longer than the interval so two can overlap. */
public class DualWieldAbility extends Ability {
    private static final MeleeSpec SPEC = MeleeSpec.DUAL_WIELDING;
    private boolean rightBlade = false;

    public DualWieldAbility(Entity player, LoadoutComponent loadout) {
        super(player, loadout, Branch.MELEE);
    }

    @Override
    public void primaryPress() {
        if (!ready()) return;
        SPEC.swing(SPEC.hit(facing()), SPEC.swingTime(), rightBlade, 1);
        rightBlade = !rightBlade;
        cooldown(SPEC.interval());
    }
}
