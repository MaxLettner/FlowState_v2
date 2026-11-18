package at.htl.flowstate.Components.Player.Abilities;

import at.htl.flowstate.Combat.Combat;
import at.htl.flowstate.Combat.DamageKind;
import at.htl.flowstate.Combat.Hit;
import at.htl.flowstate.Components.Player.Attacks.BlastComponent;
import at.htl.flowstate.Components.Player.LoadoutComponent;
import at.htl.flowstate.Skills.Branch;
import at.htl.flowstate.Skills.SkillType;
import com.almasb.fxgl.entity.Entity;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;

/** Ground: stunning swing. Mid-air: ground slam with a stunning shockwave on landing. */
public class HammerAbility extends Ability {
    private static final MeleeSpec SPEC = MeleeSpec.HAMMER;
    private static final double SLAM_DAMAGE = 25;
    private static final double SLAM_RADIUS = 150;
    private static final double SLAM_STUN = 1.5;
    private static final double SLAM_KNOCKBACK = 300;
    private static final double SLAM_COOLDOWN = 1.5;

    private boolean slamming = false;

    public HammerAbility(Entity player, LoadoutComponent loadout) {
        super(player, loadout, Branch.MELEE);
    }

    @Override
    public void primaryPress() {
        if (!ready() || slamming) return;
        if (!movement().isGrounded() && !loadout.isLevitating()) {
            slamming = true;
            movement().startSlam();
            cooldown(SLAM_COOLDOWN);
            return;
        }
        SPEC.swing(SPEC.hit(facing()), SPEC.swingTime(), false, 1);
        cooldown(SPEC.interval());
    }

    @Override
    public void update(double tpf) {
        if (slamming && movement().isGrounded()) {
            slamming = false;
            Point2D impact = new Point2D(player.getCenter().getX(), player.getY() + player.getHeight());
            BlastComponent.spawn(impact, SLAM_RADIUS, 0.15, 0.25, BlastComponent.Style.DUST, e -> Combat.hit(e,
                    Hit.of(SLAM_DAMAGE, DamageKind.MELEE)
                            .family(SkillType.BLUNT)
                            .crit(SPEC.critPercent())
                            .stun(SLAM_STUN)
                            .knockbackFrom(SLAM_KNOCKBACK, impact.getX())));
        }
    }

    @Override
    public String getStatusText() {
        return slamming ? "slam!" : "";
    }
}
