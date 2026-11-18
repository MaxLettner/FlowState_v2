package at.htl.flowstate.Components.Player.Abilities;

import at.htl.flowstate.Combat.Hit;
import at.htl.flowstate.Components.Player.LoadoutComponent;
import at.htl.flowstate.Skills.Branch;
import com.almasb.fxgl.entity.Entity;

/** 3-hit combo: slash, slash, finisher (30 dmg, strong knockback). */
public class ShortswordAbility extends Ability {
    private static final MeleeSpec SPEC = MeleeSpec.SHORTSWORD;
    private static final double COMBO_WINDOW = 0.8;
    private static final double FINISHER_DAMAGE = 30;
    private static final double FINISHER_KNOCKBACK = 450;
    private static final double FINISHER_INTERVAL = 0.6;

    private int comboStep = 0;
    private double sinceLastAttack = 99;

    public ShortswordAbility(Entity player, LoadoutComponent loadout) {
        super(player, loadout, Branch.MELEE);
    }

    @Override
    public void update(double tpf) {
        sinceLastAttack += tpf;
    }

    @Override
    public void primaryPress() {
        if (!ready()) return;
        if (sinceLastAttack > COMBO_WINDOW) comboStep = 0;

        boolean finisher = comboStep == 2;
        Hit hit = SPEC.hit(facing());
        if (finisher) hit = hit.withDamage(FINISHER_DAMAGE).knockback(FINISHER_KNOCKBACK, facing());

        SPEC.swing(hit, finisher ? 0.4 : SPEC.swingTime(), comboStep == 1, finisher ? 1.15 : 1);
        cooldown(finisher ? FINISHER_INTERVAL : SPEC.interval());

        comboStep = (comboStep + 1) % 3;
        sinceLastAttack = 0;
    }

    @Override
    public String getStatusText() {
        return comboStep > 0 && sinceLastAttack <= COMBO_WINDOW ? "combo " + comboStep + "/3" : "";
    }
}
