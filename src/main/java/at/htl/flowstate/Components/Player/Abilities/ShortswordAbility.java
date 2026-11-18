package at.htl.flowstate.Components.Player.Abilities;

import at.htl.flowstate.Combat.Hit;
import at.htl.flowstate.Components.Player.LoadoutComponent;
import com.almasb.fxgl.entity.Entity;

/** 3-hit combo: slash, rising slash, finisher (30 dmg, strong knockback, wide lunging cleave). */
public class ShortswordAbility extends MeleeAbility {
    private static final MeleeSpec SPEC = MeleeSpec.SHORTSWORD;
    private static final double COMBO_WINDOW = 0.8;
    private static final double FINISHER_DAMAGE = 30;
    private static final double FINISHER_KNOCKBACK = 450;
    private static final double FINISHER_INTERVAL = 0.6;
    private static final double FINISHER_IMPACT = 1.6;

    private int comboStep = 0;
    private double sinceLastAttack = 99;

    public ShortswordAbility(Entity player, LoadoutComponent loadout) {
        super(player, loadout);
    }

    @Override
    public void update(double tpf) {
        super.update(tpf);
        sinceLastAttack += tpf;
    }

    @Override
    public void primaryPress() {
        press(this::attack);
    }

    private void attack() {
        if (sinceLastAttack > COMBO_WINDOW) comboStep = 0;

        boolean finisher = comboStep == 2;
        Hit hit = SPEC.hit(facing());
        if (finisher) hit = hit.withDamage(FINISHER_DAMAGE).knockback(FINISHER_KNOCKBACK, facing()).impact(FINISHER_IMPACT);

        Slash slash = slash(hit, finisher ? 0.4 : SPEC.swingTime(), comboStep == 1);
        if (finisher) slash.params().windup(0.28).strike(0.22);
        launch(SPEC, slash, finisher ? 1.15 : 1, finisher ? 1.8 : 1);
        cooldown(finisher ? FINISHER_INTERVAL : SPEC.interval());

        comboStep = (comboStep + 1) % 3;
        sinceLastAttack = 0;
    }

    @Override
    public String getStatusText() {
        String combo = comboStep > 0 && sinceLastAttack <= COMBO_WINDOW ? "combo " + comboStep + "/3" : "";
        String f = super.getStatusText();
        return combo.isEmpty() ? f : f.isEmpty() ? combo : combo + "  " + f;
    }
}
