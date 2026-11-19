package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Combat.Combat;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.core.math.FXGLMath;
import com.almasb.fxgl.entity.Entity;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Hex Priest: support. Stays behind its allies and alternates two spells: a ward that shields up to three nearby
 * demons (it soaks 30 damage), and a hex bolt that curses you (lower jumps, no mana regen for a few seconds).
 * Kill it first, or knock it away from the group.
 */
public class PriestBehaviour extends GroundDemonBehaviour {
    private static final double SPEED = 100;
    private static final double CONTACT_DAMAGE = 4;
    private static final double TOO_CLOSE = 380, TOO_FAR = 650;
    private static final double WARD_RANGE = 330;
    private static final double WARD_AMOUNT = 30;
    private static final int WARD_TARGETS = 3;
    private static final double HEX_DAMAGE = 6;
    private static final double HEX_RANGE = 900;
    private static final double CAST_TIME = 0.7;
    private static final Color[] HEX_MOTES = {Color.web("#f6d8ff"), Color.web("#d08cff"), Color.web("#a050f0")};
    private static final Color TELL_HEX = Color.rgb(200, 120, 255, 0.9);

    private enum Spell { WARD, HEX }

    private Spell casting;
    private double timer;
    private double cooldown = FXGLMath.random(1.0, 2.0);
    private boolean wardNext = true;

    @Override
    protected void act(double tpf) {
        double t = dt(tpf);
        cooldown -= t;
        if (casting != null) {
            cast(t);
            return;
        }
        double dx = dxToPlayer();
        double adx = Math.abs(dx);
        int toward = dx >= 0 ? 1 : -1;
        if (adx < TOO_CLOSE) walk(-toward, SPEED);
        else if (adx > TOO_FAR) walk(toward, SPEED * 1.15);
        else stop();
        if (adx <= TOO_FAR) face(toward);
        touch(CONTACT_DAMAGE);
        locomotion("idle", "walk", 9, "idle");

        if (grounded && cooldown <= 0) {
            boolean canWard = !wardTargets().isEmpty();
            boolean canHex = adx < HEX_RANGE;
            Spell next = (wardNext && canWard) || !canHex ? (canWard ? Spell.WARD : null) : Spell.HEX;
            if (next != null) {
                casting = next;
                timer = 0;
                face(toward);
                sprite.setTelegraph(TELL_HEX);
                Sfx.HEX_CAST.at(entity.getCenter());
            }
        }
    }

    private void cast(double t) {
        stop();
        timer += t;
        sprite.show("cast", Math.min(3, (int) (timer / CAST_TIME * 4)));
        if (Math.random() < 0.4) Particles.burst(lantern(), HEX_MOTES, 1, -90, 90, 20, 70, 0.4, 4, -50);
        if (timer < CAST_TIME) return;
        sprite.setTelegraph(null);
        if (casting == Spell.WARD) {
            for (Entity e : wardTargets()) {
                e.getComponent(EnemyStatsComponent.class).ward(WARD_AMOUNT);
                Point2D from = lantern(), to = e.getCenter();
                for (int k = 0; k <= 6; k++) {
                    Point2D p = from.add(to.subtract(from).multiply(k / 6.0));
                    Particles.burst(p, HEX_MOTES, 1, -90, 180, 10, 40, 0.5, 4, 0);
                }
            }
            Sfx.SHIELD_ON.at(entity.getCenter(), 0.8, 0.8);
        } else {
            Point2D from = lantern();
            Point2D to = player.getCenter().subtract(from);
            HomingOrbComponent.spawnHex(from, Math.atan2(to.getY(), to.getX()), HEX_DAMAGE, entity);
        }
        wardNext = casting == Spell.HEX;
        casting = null;
        cooldown = FXGLMath.random(2.6, 3.4);
    }

    private Point2D lantern() {
        return entity.getCenter().add(facing * 22, -48);
    }

    /** Nearby demons without a ward, nearest first (never itself or the Pit Lord). */
    private List<Entity> wardTargets() {
        List<Entity> list = new ArrayList<>();
        for (Entity e : Combat.enemiesInRadius(entity.getCenter(), WARD_RANGE)) {
            if (e == entity) continue;
            EnemyStatsComponent s = e.getComponent(EnemyStatsComponent.class);
            if (s.isDead() || s.hasWard() || s.getType() == DemonType.LORD) continue;
            list.add(e);
        }
        list.sort(Comparator.comparingDouble(e -> e.getCenter().distance(entity.getCenter())));
        return list.size() > WARD_TARGETS ? list.subList(0, WARD_TARGETS) : list;
    }

    @Override
    protected void interrupt() {
        casting = null;
        cooldown = Math.max(cooldown, 0.8);
    }

    @Override
    protected void disabledPose() {
        sprite.loop("idle", 5);
    }
}
