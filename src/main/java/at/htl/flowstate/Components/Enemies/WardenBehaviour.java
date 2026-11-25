package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Combat.DamageKind;
import at.htl.flowstate.Combat.Hit;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Skills.SkillType;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.core.math.FXGLMath;
import com.almasb.fxgl.entity.Entity;
import javafx.geometry.Point2D;

/**
 * Chainwarden: walks at you behind a tower shield that always faces you. Arrows and spells from the front
 * bounce off; hits from behind or from above get through. Blunt weapons and knockback batter the shield until it
 * breaks, then it drops the act and comes at you faster. Up close it whirls its flail overhead and brings it down.
 */
public class WardenBehaviour extends GroundDemonBehaviour {
    private static final double SPEED = 85, BARE_SPEED = 135;
    private static final double CONTACT_DAMAGE = 6;
    private static final double FLAIL_DAMAGE = 15;
    private static final double FLAIL_REACH = 190;
    private static final double TRIGGER = 170;
    private static final double SHIELD_HP = 70;
    private static final double MELEE_THROUGH = 0.25;     // a sword on the shield only scratches it
    private static final double HEAVY_KNOCKBACK = 300;    // this much knockback counts as a shield bash
    // swing timeline (s): wind up, overhead, strike, follow through, recover
    private static final double T1 = 0.3, T2 = 0.6, T_HIT = 0.68, T4 = 0.9, T_END = 1.2;

    private double shield = SHIELD_HP;
    private boolean swinging, struck;
    private double timer;
    private double cooldown = FXGLMath.random(0.6, 1.4);

    private boolean bare() {
        return shield <= 0;
    }

    private String anim(String base) {
        return bare() ? base + "_bare" : base;
    }

    @Override
    protected void act(double tpf) {
        double t = dt(tpf);
        cooldown -= t;
        if (swinging) {
            swing(t);
            return;
        }
        double dx = dxToPlayer();
        walk(Math.abs(dx) < 30 ? 0 : dx > 0 ? 1 : -1, bare() ? BARE_SPEED : SPEED);
        face(dirToPlayer());                  // the shield always turns to you
        touch(CONTACT_DAMAGE);
        locomotion(anim("idle"), anim("walk"), bare() ? 11 : 8, anim("idle"));
        if (grounded && cooldown <= 0 && Math.abs(dx) < TRIGGER && Math.abs(dyToPlayer()) < 110) {
            swinging = true;
            struck = false;
            timer = 0;
            sprite.setTelegraph(TELL_MELEE);
            Sfx.FLAIL_SWING.at(entity.getCenter(), 0.7, 0.8);
        }
    }

    private void swing(double t) {
        stop();
        timer += t;
        int frame = timer < T1 ? 0 : timer < T2 ? 1 : timer < T4 - 0.1 ? 2 : timer < T4 ? 3 : 4;
        sprite.show(anim("swing"), frame);
        if (!struck && timer >= T_HIT) {
            struck = true;
            sprite.setTelegraph(null);
            Sfx.FLAIL_SWING.at(entity.getCenter(), 1, 1.1);
            double px = dxToPlayer() * facing;
            if (px > -entity.getWidth() / 2 && px < FLAIL_REACH && Math.abs(dyToPlayer()) < 110) hurtPlayer(FLAIL_DAMAGE);
            Point2D ball = entity.getCenter().add(facing * 120, 10);
            Particles.of(Particles.DUST).count(8).angle(-90, 60).speed(80, 220).life(0.4).size(5).gravity(700).at(ball);
        }
        if (timer >= T_END) {
            swinging = false;
            cooldown = FXGLMath.random(1.6, 2.2);
        }
    }

    @Override
    public double guard(Hit h, Entity player) {
        if (bare() || !fromFront(h)) return 1;
        if (player.getBottomY() < entity.getY() + 12) return 1;          // from above, over the shield
        boolean bash = h.getFamily() == SkillType.BLUNT || h.getKnockback() >= HEAVY_KNOCKBACK;
        double wear = switch (h.getKind()) {
            case MELEE -> bash ? h.getDamage() * 1.6 + h.getKnockback() / 15 : h.getDamage() * 0.4;
            case OTHER -> h.getDamage();
            default -> bash ? h.getDamage() : h.getDamage() * 0.15;
        };
        shield -= wear;
        if (shield <= 0) {
            breakShield();
            return 1;
        }
        Sfx.BLOCK_HIT.at(entity.getCenter(), 0.9, bash ? 0.8 : 1.1);
        return h.getKind() == DamageKind.MELEE || h.getKind() == DamageKind.OTHER ? MELEE_THROUGH : 0;
    }

    private void breakShield() {
        Sfx.SHIELD_BREAK.at(entity.getCenter(), 1, 0.7);
        Point2D at = entity.getCenter().add(facing * entity.getWidth() * 0.6, 0);
        Particles.of(new javafx.scene.paint.Color[]{javafx.scene.paint.Color.web("#7a849c"), javafx.scene.paint.Color.web("#383f52"), javafx.scene.paint.Color.web("#ece2c6")}).count(18).angle(-90, 100).speed(120, 380)
                .life(0.7).size(6).gravity(900).at(at);
        stats.stun(0.6);                       // staggers when the shield goes
    }

    @Override
    protected void interrupt() {
        swinging = false;
    }

    @Override
    protected void disabledPose() {
        sprite.loop(anim("idle"), 6);
    }
}
