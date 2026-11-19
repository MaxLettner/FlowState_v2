package at.htl.flowstate.Components.Player.Abilities;

import at.htl.flowstate.Combat.DamageKind;
import at.htl.flowstate.Combat.Hit;
import at.htl.flowstate.Components.Player.Attacks.PlayerProjectileComponent;
import at.htl.flowstate.Components.Player.LoadoutComponent;
import at.htl.flowstate.Skills.SkillType;
import com.almasb.fxgl.entity.Entity;

/**
 * Hold to draw; damage, speed and pierce scale with draw time (0.3 s min, 1.5 s full).
 * Releasing before the minimum shoots a weak arrow whose damage scales with the draw, so tapping never pays off.
 */
public class WarBowAbility extends DrawnBowAbility {
    private static final double MIN_DRAW = 0.3;
    private static final double FULL_DRAW = 1.5;
    private static final double WEAK_DAMAGE = 10;
    private static final double WEAK_SPEED = 600;

    public WarBowAbility(Entity player, LoadoutComponent loadout) {
        super(player, loadout, "WarBow.png", 41, 32, 24);
    }

    @Override
    protected double fullDraw() {
        return FULL_DRAW;
    }

    @Override
    protected void release(double drawTime) {
        double damage, speed, gravity;
        int pierce;
        if (drawTime < MIN_DRAW) { // weak, early release
            double w = drawTime / MIN_DRAW;
            damage = WEAK_DAMAGE * Math.max(BowAbility.MIN_POWER, w);
            speed = WEAK_SPEED * (0.5 + 0.5 * w);
            pierce = 0;
            gravity = BowAbility.ARROW_GRAVITY;
        } else {
            double f = (Math.min(drawTime, FULL_DRAW) - MIN_DRAW) / (FULL_DRAW - MIN_DRAW);
            damage = 20 + 40 * f;
            speed = 900 + 900 * f;
            pierce = (int) Math.round(1 + 2 * f);
            gravity = BowAbility.ARROW_GRAVITY * (1 - 0.85 * f);
        }
        final double finalDamage = damage;
        PlayerProjectileComponent arrow = new PlayerProjectileComponent(
                aim().multiply(speed * BowAbility.EAGLE_EYE_SPEED),
                pierce(pierce),
                (e, i) -> Hit.of(finalDamage, DamageKind.RANGED).family(SkillType.BOW))
                .gravity(gravity * BowAbility.EAGLE_EYE_GRAVITY);
        spawnProjectile(projectileSprite("WarArrow.png", 36, 4), 36, 4, arrow);
    }
}
