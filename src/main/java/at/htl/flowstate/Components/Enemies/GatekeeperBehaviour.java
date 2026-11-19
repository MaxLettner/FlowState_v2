package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Combat.Combat;
import at.htl.flowstate.Sfx;
import com.almasb.fxgl.core.math.FXGLMath;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.SpawnData;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

import static com.almasb.fxgl.dsl.FXGL.spawn;

/**
 * Gatekeeper: plants itself (it only shuffles closer when you are far away) and raises its key-staff to tear open
 * portals that let imps through, up to four of its own at a time. Kill it first or the imps never stop.
 */
public class GatekeeperBehaviour extends GroundDemonBehaviour {
    private static final double SPEED = 55;
    private static final double CONTACT_DAMAGE = 6;
    private static final double SUMMON_RANGE = 1100;
    private static final double SUMMON_TIME = 0.8;
    private static final int MAX_IMPS = 4;
    private static final Color TELL_PORTAL = Color.rgb(255, 80, 140, 0.9);

    private final List<Entity> imps = new ArrayList<>();
    private boolean summoning;
    private double timer;
    private double cooldown = FXGLMath.random(0.8, 1.6);

    @Override
    protected void act(double tpf) {
        double t = dt(tpf);
        cooldown -= t;
        imps.removeIf(e -> !e.isActive());
        face(dirToPlayer());
        if (summoning) {
            stop();
            timer += t;
            sprite.show("summon", Math.min(3, (int) (timer / SUMMON_TIME * 4)));
            if (timer >= SUMMON_TIME) {
                summoning = false;
                sprite.setTelegraph(null);
                openPortal();
                cooldown = FXGLMath.random(4.0, 5.0);
            }
            return;
        }
        double dx = dxToPlayer();
        walk(Math.abs(dx) > 700 ? (dx > 0 ? 1 : -1) : 0, SPEED);
        touch(CONTACT_DAMAGE);
        locomotion("idle", "idle", 6, "idle");
        if (grounded && cooldown <= 0 && imps.size() < MAX_IMPS && Math.abs(dx) < SUMMON_RANGE) {
            summoning = true;
            timer = 0;
            sprite.setTelegraph(TELL_PORTAL);
            Sfx.HEX_CAST.at(entity.getCenter(), 0.8, 0.7);
        }
    }

    private void openPortal() {
        // on the player's side, a little in front of the gatekeeper, on solid ground
        double x = entity.getCenter().getX() + facing * FXGLMath.random(90, 150);
        double ground = Combat.groundYBelow(x, entity.getY());
        if (Double.isNaN(ground)) {
            x = entity.getCenter().getX() - facing * 110;
            ground = Combat.groundYBelow(x, entity.getY());
            if (Double.isNaN(ground)) return;
        }
        double gx = x, gy = ground;
        int count = Math.min(MAX_IMPS - imps.size(), FXGLMath.random(1, 2));
        PortalComponent.spawn(gx, gy, () -> {
            for (int i = 0; i < count; i++) {
                DemonType imp = DemonType.IMP;
                Entity e = spawn(imp.spawnName(), new SpawnData(gx - imp.width / 2 + (i - 0.5) * 20, gy - imp.height - 2));
                e.getComponent(EnemyStatsComponent.class).markSummoned();
                imps.add(e);
            }
        });
    }

    @Override
    protected void interrupt() {
        summoning = false;
    }

    @Override
    protected void disabledPose() {
        sprite.loop("idle", 5);
    }
}
