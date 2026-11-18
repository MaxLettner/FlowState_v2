package at.htl.flowstate.Components.Player.Abilities;

import at.htl.flowstate.Components.Player.LoadoutComponent;
import at.htl.flowstate.Skills.Branch;
import com.almasb.fxgl.entity.Entity;

/** A plain swing weapon: Rusty Sword, Iron Sword, Club, Spring Hammer. */
public class SwingWeaponAbility extends Ability {
    private final MeleeSpec spec;

    public SwingWeaponAbility(Entity player, LoadoutComponent loadout, MeleeSpec spec) {
        super(player, loadout, Branch.MELEE);
        this.spec = spec;
    }

    @Override
    public void primaryPress() {
        if (!ready()) return;
        spec.swing(spec.hit(facing()), spec.swingTime(), false, 1);
        cooldown(spec.interval());
    }
}
