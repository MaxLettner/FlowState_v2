package at.htl.flowstate.Components.Player.Abilities;

import at.htl.flowstate.Components.Player.LoadoutComponent;
import com.almasb.fxgl.entity.Entity;

/** A plain swing weapon: Rusty Sword, Iron Sword, Club, Spring Hammer. Alternates overhead and rising slashes. */
public class SwingWeaponAbility extends MeleeAbility {
    private static final double ALTERNATE_WINDOW = 0.5;   // seconds after ready in which the next swing comes back up

    private final MeleeSpec spec;
    private boolean backswing = false;
    private double sinceSwing = 99;

    public SwingWeaponAbility(Entity player, LoadoutComponent loadout, MeleeSpec spec) {
        super(player, loadout);
        this.spec = spec;
    }

    @Override
    public void update(double tpf) {
        super.update(tpf);
        sinceSwing += tpf;
    }

    @Override
    public void primaryPress() {
        press(() -> {
            backswing = sinceSwing <= spec.interval() + ALTERNATE_WINDOW && !backswing;
            launch(spec, slash(spec.hit(facing()), spec.swingTime(), backswing));
            cooldown(spec.interval());
            sinceSwing = 0;
        });
    }
}
