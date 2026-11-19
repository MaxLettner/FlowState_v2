package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Combat.Combat;
import at.htl.flowstate.Components.Player.PlayerStatsComponent;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Visuals.Impact;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.core.math.FXGLMath;
import com.almasb.fxgl.dsl.components.HealthDoubleComponent;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.SpawnData;
import javafx.geometry.Point2D;
import javafx.util.Duration;

import static com.almasb.fxgl.dsl.FXGL.runOnce;
import static com.almasb.fxgl.dsl.FXGL.spawn;

/**
 * Pit Lord, the mini-boss. Fought in a walled-off arena (BossArena): it drops in behind the player, lands with
 * a crash and roars, then fights in three phases by health:
 * <ol>
 *   <li>Cleaver slam (direct hit plus shockwaves both ways) up close, magma volleys from range.</li>
 *   <li>Below 60 %: roars and also tears open portals full of imps.</li>
 *   <li>Below 30 %: enraged; faster, double shockwaves, bigger volleys.</li>
 * </ol>
 * No knockback, stuns are cut to a third. Killing it opens the arena and grants two skill points.
 */
public class PitLordBehaviour extends GroundDemonBehaviour {
    private static final double SPEED = 80;
    private static final double RAGE = 1.4;
    private static final double CONTACT_DAMAGE = 12;
    private static final double SLAM_DAMAGE = 28;
    private static final double SLAM_RANGE = 330;
    private static final double SLAM_REACH = 260;
    private static final double BALL_DAMAGE = 14;
    private static final double BALL_SPEED = 780;
    private static final double DROP_SPEED = 500;      // it doesn't drift down, it's hurled in
    private static final int REWARD_SP = 2;
    // slam timeline (s)
    private static final double S_RAISE = 0.35, S_HOLD = 0.9, S_DOWN = 0.98, S_IMPACT = 1.2, S_SETTLE = 1.4, S_END = 1.75;
    // volley timeline (s)
    private static final double V_RELEASE = 0.9, V_END = 1.3;
    private static final double ROAR_TIME = 1.2;

    private static Entity current;

    private enum State { DROP, WALK, SLAM, VOLLEY, SUMMON, ROAR, RECOVER }

    private State state = State.DROP;
    private double timer;
    private double cooldown = 1.5;
    private double summonCooldown = 4;
    private boolean fired;
    private int phase = 1;
    private boolean falling;

    /** The living Pit Lord, if any (the HUD shows its health bar). */
    public static Entity current() {
        return current != null && current.isActive() ? current : null;
    }

    @Override
    protected void onReady() {
        current = entity;
        state = State.DROP;
        timer = 0;
        physics.setVelocityY(DROP_SPEED);
    }

    private double hpFraction() {
        return entity.getComponent(HealthDoubleComponent.class).getValuePercent() / 100.0;
    }

    @Override
    protected void act(double tpf) {
        double t = dt(tpf) * (phase == 3 ? RAGE : 1);
        cooldown -= t;
        summonCooldown -= t;
        int now = hpFraction() < 0.3 ? 3 : hpFraction() < 0.6 ? 2 : 1;
        if (now > phase && state == State.WALK) {
            phase = now;
            state = State.ROAR;
            timer = 0;
            roar();
        }
        switch (state) {
            case DROP -> drop(t);
            case WALK -> walkState();
            case SLAM -> slam(t);
            case VOLLEY -> volley(t);
            case SUMMON, ROAR -> {
                stop();
                timer += t;
                sprite.show("roar", (int) (timer * 6) % 2);
                if (state == State.SUMMON && !fired && timer > 0.5) {
                    fired = true;
                    summon();
                }
                if (timer >= ROAR_TIME) {
                    state = State.RECOVER;
                    timer = 0.4;
                }
            }
            case RECOVER -> {
                stop();
                sprite.loop("idle", 6);
                if ((timer -= t) <= 0) state = State.WALK;
            }
        }
    }

    /** Falling in from above the screen, cleaver raised; crashes down and roars. */
    private void drop(double t) {
        stop();
        face(dirToPlayer());
        timer += t;
        if (physics.getVelocityY() > 50) falling = true;
        sprite.show("slam", falling && !grounded ? 1 : 4);
        // landed (or spawned standing on the ground: nothing to fall from)
        if (grounded && (falling || timer > 0.3)) {
            Point2D feet = new Point2D(entity.getCenter().getX(), entity.getBottomY() - 4);
            Sfx.BRUTE_SLAM.at(entity.getCenter(), 1, 0.6);
            Impact.shake(26);
            Particles.burst(feet, Particles.DUST, 44, -90, 85, 200, 640, 0.9, 9, 900);
            Particles.burst(feet, Particles.EMBER, 26, -90, 70, 160, 520, 0.8, 6, 600);
            BossArena.landed(entity);
            state = State.ROAR;
            timer = 0;
            roar();
        }
    }

    private void walkState() {
        if (BossArena.inCutscene()) {          // the intro roar is over but the player can't move yet
            stop();
            face(dirToPlayer());
            sprite.loop("idle", 6);
            return;
        }
        double dx = dxToPlayer();
        double adx = Math.abs(dx);
        walk(adx < 170 ? 0 : dx > 0 ? 1 : -1, SPEED * (phase == 3 ? RAGE : 1));
        face(dirToPlayer());
        touch(CONTACT_DAMAGE);
        locomotion("idle", "walk", phase == 3 ? 9 : 7, "idle");
        if (!grounded || cooldown > 0) return;
        if (phase >= 2 && summonCooldown <= 0) {
            start(State.SUMMON);
            Sfx.BOSS_ROAR.at(entity.getCenter(), 0.8, 1.1);
        } else if (adx < SLAM_RANGE) {
            start(State.SLAM);
            sprite.setTelegraph(TELL_MELEE);
            Sfx.BRUTE_ROAR.at(entity.getCenter(), 1, 0.7);
        } else if (adx < 1100) {
            start(State.VOLLEY);
            sprite.setTelegraph(TELL_RANGED);
            Sfx.CAST_FIREBALL.at(entity.getCenter(), 0.9, 0.7);
        }
    }

    private void start(State s) {
        state = s;
        timer = 0;
        fired = false;
        face(dirToPlayer());
    }

    private void slam(double t) {
        stop();
        timer += t;
        int frame = timer < S_RAISE ? 0 : timer < S_HOLD ? 1 : timer < S_DOWN ? 2 : timer < S_IMPACT ? 3 : timer < S_SETTLE ? 4 : 5;
        sprite.show("slam", frame);
        if (!fired && timer >= S_DOWN) {
            fired = true;
            sprite.setTelegraph(null);
            impact();
        }
        if (timer >= S_END) {
            state = State.RECOVER;
            timer = 0.7;
            cooldown = FXGLMath.random(1.4, 2.0);
        }
    }

    private void impact() {
        double groundY = entity.getBottomY();
        double tipX = entity.getCenter().getX() + facing * (entity.getWidth() / 2 + 110);
        Sfx.BRUTE_SLAM.at(entity.getCenter(), 1, 0.75);
        Impact.shake(20);
        Particles.burst(new Point2D(tipX, groundY - 4), Particles.DUST, 34, -90, 75, 160, 560, 0.8, 8, 900);
        Particles.burst(new Point2D(tipX, groundY - 4), Particles.EMBER, 20, -90, 60, 160, 480, 0.7, 6, 600);
        double px = dxToPlayer() * facing;
        boolean low = player.getBottomY() > groundY - 120;
        if (px > -entity.getWidth() / 2 && px < entity.getWidth() / 2 + SLAM_REACH && low) hurtPlayer(SLAM_DAMAGE);
        waves(tipX, groundY);
        if (phase == 3) {
            Entity self = entity;
            runOnce(() -> { if (self.isActive()) waves(tipX, groundY); }, Duration.seconds(0.4));
        }
    }

    private void waves(double tipX, double groundY) {
        ShockwaveComponent.spawn(tipX, groundY, facing, entity);
        ShockwaveComponent.spawn(entity.getCenter().getX() - facing * entity.getWidth() / 2, groundY, -facing, entity);
    }

    private void volley(double t) {
        stop();
        timer += t;
        sprite.show("cast", timer < V_RELEASE ? Math.min(2, (int) (timer / V_RELEASE * 3)) : 3);
        if (!fired && timer >= V_RELEASE) {
            fired = true;
            sprite.setTelegraph(null);
            Point2D hand = entity.getCenter().add(facing * 40, -150);
            int count = phase == 3 ? 5 : 3;
            for (int i = 0; i < count; i++) {
                double offset = (i - (count - 1) / 2.0) * 130;
                HurlerBehaviour.lob(hand, player.getCenter().add(offset, 20), BALL_SPEED, BALL_DAMAGE, entity);
            }
            Sfx.ENEMY_THROW.at(entity.getCenter(), 1, 0.6);
        }
        if (timer >= V_END) {
            state = State.RECOVER;
            timer = 0.5;
            cooldown = FXGLMath.random(1.2, 1.8);
        }
    }

    private void summon() {
        summonCooldown = phase == 3 ? 7 : 9;
        for (int side : new int[]{-1, 1}) {
            double x = BossArena.insideX(entity.getCenter().getX() + side * 260, 120);
            double ground = Combat.groundYBelow(x, entity.getY());
            if (Double.isNaN(ground)) continue;
            double gy = ground;
            PortalComponent.spawn(x, gy, () -> {
                for (int i = 0; i < 2; i++) {
                    DemonType imp = DemonType.IMP;
                    Entity e = spawn(imp.spawnName(), new SpawnData(x - imp.width / 2 + (i - 0.5) * 24, gy - imp.height - 2));
                    e.getComponent(EnemyStatsComponent.class).markSummoned();
                }
            });
        }
    }

    private void roar() {
        Sfx.BOSS_ROAR.at(entity.getCenter());
        Impact.shake(14);
        Particles.burst(entity.getCenter().add(facing * 40, -130), Particles.EMBER, 30, -90, 100, 120, 420, 0.9, 6, 200);
    }

    @Override
    protected void interrupt() {
        if (state == State.DROP) return;      // nothing cuts the entrance short
        state = State.RECOVER;
        timer = 0.3;
    }

    @Override
    protected void disabledPose() {
        sprite.loop("idle", 5);
    }

    @Override
    public void onDeath() {
        BossArena.end();
        PlayerStatsComponent stats = player.getComponent(PlayerStatsComponent.class);
        for (int i = 0; i < REWARD_SP; i++) stats.addSkillPoint();
        Sfx.LEVEL_UP.play();
        Impact.shake(24);
        Point2D c = entity.getCenter();
        for (int k = 0; k < 6; k++) {
            Particles.burst(c.add(FXGLMath.random(-60, 60), FXGLMath.random(-140, 120)), Particles.EMBER, 20, -90, 180, 100, 520, 1.0, 7, 300);
        }
        if (current == entity) current = null;
    }

    /** Gone without dying (fell into a pit, a new game): never leave the way barred. */
    @Override
    public void onRemoved() {
        BossArena.end();
        if (current == entity) current = null;
    }
}
