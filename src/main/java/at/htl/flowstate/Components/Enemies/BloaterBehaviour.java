package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Combat.Combat;
import at.htl.flowstate.Combat.DamageKind;
import at.htl.flowstate.Combat.Hit;
import at.htl.flowstate.Components.Player.Attacks.BlastComponent;
import at.htl.flowstate.Components.Player.PlayerStatsComponent;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Visuals.Impact;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.entity.Entity;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import static com.almasb.fxgl.dsl.FXGL.runOnce;

/**
 * Tar Bloater: waddles at you; when it gets close it swells for 1.5 s and bursts. It also bursts when it dies,
 * and the blast hurts demons too, so knock it into a crowd (Bloaters set each other off).
 * Once lit, the fuse can't be put out: stunning or knocking it away only moves the bomb.
 */
public class BloaterBehaviour extends GroundDemonBehaviour {
    private static final double SPEED = 75;
    private static final double FUSE_RANGE = 75;
    private static final double FUSE_TIME = 1.5;
    private static final double RADIUS = 140;
    private static final double PLAYER_DAMAGE = 22;
    private static final double DEMON_DAMAGE = 45;
    private static final double CHAIN_DELAY = 0.12;      // a bloater set off by another bursts a moment later
    private static final Color FUSE_GLOW = Color.rgb(255, 230, 160, 0.95);
    private static final Color[] TAR = {Color.web("#524634"), Color.web("#352c20"), Color.web("#1e1912")};

    private boolean lit;
    private double fuse;

    @Override
    protected void act(double tpf) {
        if (lit) {
            stop();
            burnFuse(tpf);
            return;
        }
        double dx = dxToPlayer();
        walk(Math.abs(dx) < 6 ? 0 : dx > 0 ? 1 : -1, SPEED);
        locomotion("idle", "walk", 9, "idle");
        if (Math.abs(dx) < FUSE_RANGE + entity.getWidth() / 2 && Math.abs(dyToPlayer()) < 90) light();
    }

    private void light() {
        lit = true;
        fuse = 0;
        sprite.once("fuse", 4 / FUSE_TIME, null);
        sprite.setTelegraph(FUSE_GLOW);
        Sfx.BLOATER_FUSE.at(entity.getCenter());
    }

    private void burnFuse(double tpf) {
        fuse += tpf;
        if (fuse >= FUSE_TIME) stats.selfDestruct();
    }

    @Override
    protected void whileDisabled(double tpf) {
        super.whileDisabled(tpf);
        if (lit) {
            sprite.setTelegraph(FUSE_GLOW);
            burnFuse(tpf);
        }
    }

    @Override
    public void onDeath() {
        Point2D c = entity.getCenter();
        Entity self = entity;
        runOnce(() -> explode(c, self), Duration.seconds(lit ? 0.01 : CHAIN_DELAY));
    }

    private static void explode(Point2D c, Entity self) {
        Sfx.EXPLOSION.at(c, 1, 0.9);
        Impact.shake(10);
        Particles.splat(c, TAR);
        BlastComponent.spawn(c, RADIUS, 0.18, 0.3, BlastComponent.Style.FIRE, e -> {
            if (e == self) return;
            Combat.hit(e, Hit.of(DEMON_DAMAGE, DamageKind.OTHER).knockbackFrom(520, c.getX()).impact(0));
        });
        Entity player = at.htl.flowstate.Game.getPlayer();
        if (player.getCenter().distance(c) < RADIUS + 20) {
            player.getComponent(PlayerStatsComponent.class).takeDamage(PLAYER_DAMAGE, null, false);
        }
    }

    @Override
    protected void disabledPose() {
        if (!lit) sprite.loop("idle", 6);
    }
}
