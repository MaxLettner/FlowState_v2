package at.htl.flowstate.Components.Player.Abilities;

import at.htl.flowstate.Assets;
import at.htl.flowstate.Combat.Combat;
import at.htl.flowstate.Combat.DamageKind;
import at.htl.flowstate.Combat.Hit;
import at.htl.flowstate.Components.Player.Attacks.BlastComponent;
import at.htl.flowstate.Components.Player.Attacks.BurningGroundComponent;
import at.htl.flowstate.Components.Player.Attacks.PlayerProjectileComponent;
import at.htl.flowstate.Components.Player.LoadoutComponent;
import at.htl.flowstate.Skills.Branch;
import at.htl.flowstate.Skills.SkillType;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.texture.Texture;
import javafx.scene.paint.Color;

/** Hold to charge (0-1.5 s). Explodes on impact and leaves burning ground; everything scales with charge. */
public class FireballAbility extends Ability {
    private static final double FULL_CHARGE = 1.5;
    private static final double RELEASE_COOLDOWN = 0.3;
    private static final double SPEED = 800;

    private boolean charging = false;
    private double chargeTime = 0;

    public FireballAbility(Entity player, LoadoutComponent loadout) {
        super(player, loadout, Branch.MAGIC);
    }

    @Override
    public void primaryPress() {
        if (!ready()) return;
        charging = true;
        chargeTime = 0;
    }

    @Override
    public void update(double tpf) {
        if (charging) chargeTime += tpf;
    }

    @Override
    public void primaryRelease() {
        if (!charging) return;
        charging = false;

        double f = Math.min(chargeTime / FULL_CHARGE, 1);
        double mana = 25 + 35 * f;
        if (!stats().takeMana(mana)) return;
        loadout.markCast();
        sprite().playCast();
        cooldown(RELEASE_COOLDOWN);

        double damage = 30 + 50 * f;
        double radius = 120 + 100 * f;
        double groundDuration = 2 + 4 * f;

        PlayerProjectileComponent ball = new PlayerProjectileComponent(aim().multiply(SPEED), 0, (e, i) -> null)
                .onStop((pos, vel, lastEnemy, terrain) -> {
                    BlastComponent.spawn(pos, radius, 0.2, 0.4, BlastComponent.Style.FIRE,
                            e -> Combat.hit(e, Hit.of(damage, DamageKind.SPELL).family(SkillType.FIRE_BALL)));
                    BurningGroundComponent.spawn(pos.getX(), pos.getY(), radius, groundDuration);
                });

        spawnProjectile(projectileSprite("FireBall.png", 4, 14, 3, 30, 30, 19, 8), 30, 30, ball);
    }

    @Override
    public void onDeselect() {
        charging = false;
    }

    @Override
    public double getChargeProgress() {
        return charging ? Math.min(chargeTime / FULL_CHARGE, 1) : -1;
    }
}
