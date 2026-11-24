package at.htl.flowstate.Components.Player.Abilities;

import at.htl.flowstate.Combat.Combat;
import at.htl.flowstate.Combat.DamageKind;
import at.htl.flowstate.Combat.Hit;
import at.htl.flowstate.Components.Player.Attacks.BlastComponent;
import at.htl.flowstate.Components.Player.LoadoutComponent;
import at.htl.flowstate.Skills.SkillType;
import at.htl.flowstate.Visuals.Impact;
import com.almasb.fxgl.entity.Entity;
import javafx.geometry.Point2D;

/** Ground: heavy stunning swing. Mid-air: ground slam with a stunning shockwave on landing. */
public class HammerAbility extends MeleeAbility {
    private static final MeleeSpec SPEC = MeleeSpec.HAMMER;
    private static final double SLAM_DAMAGE = 25;
    private static final double SLAM_RADIUS = 150;
    private static final double SLAM_STUN = 1.5;
    private static final double SLAM_KNOCKBACK = 300;
    private static final double SLAM_COOLDOWN = 1.5;
    private static final double SLAM_IMPACT = 1.4;
    private static final double SLAM_SHAKE = 9;
    private static final double SLAM_HITSTOP = 0.05;

    private boolean slamming = false;

    public HammerAbility(Entity player, LoadoutComponent loadout) {
        super(player, loadout);
    }

    @Override
    public void primaryPress() {
        if (slamming) return;
        if (!movement().isGrounded() && !loadout.isLevitating()) {
            if (!ready()) return;
            slamming = true;
            movement().startSlam();
            cooldown(SLAM_COOLDOWN);
            return;
        }
        press(() -> {
            // heavy weapon: long anticipation, a short brutal strike
            Slash slash = slash(SPEC.hit(facing()), SPEC.swingTime(), false);
            slash.params().windup(0.32).strike(0.2);
            launch(SPEC, slash);
            cooldown(SPEC.interval());
        });
    }

    @Override
    public void update(double tpf) {
        super.update(tpf);
        if (slamming && movement().isGrounded()) {
            slamming = false;
            Point2D impact = new Point2D(player.getCenter().getX(), player.getY() + player.getHeight());
            Impact.shake(SLAM_SHAKE);
            Impact.hitStop(SLAM_HITSTOP);
            BlastComponent.spawn(impact, SLAM_RADIUS, 0.15, 0.25, BlastComponent.Style.DUST, e -> Combat.hit(e,
                    flowed(Hit.of(SLAM_DAMAGE, DamageKind.MELEE)
                            .family(SkillType.BLUNT)
                            .crit(SPEC.critPercent())
                            .stun(SLAM_STUN)
                            .knockbackFrom(SLAM_KNOCKBACK, impact.getX()))
                            .impact(SLAM_IMPACT)));
        }
    }
}
