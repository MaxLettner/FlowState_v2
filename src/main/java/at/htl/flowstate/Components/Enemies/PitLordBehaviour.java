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

import java.util.ArrayList;
import java.util.List;

import static com.almasb.fxgl.dsl.FXGL.runOnce;
import static com.almasb.fxgl.dsl.FXGL.spawn;

/**
 * Pit Lord, the mini-boss. Fought in a walled-off arena (BossArena): it drops in behind the player, lands with
 * a crash and roars, then fights in three phases by health:
 * <ol>
 *   <li>Cleaver slam (direct hit plus shockwaves both ways) up close. From range: magma volleys; a bull charge
 *       across the arena (only the lowered horns hit: jump it) that ends dazed against a gate, shaking rubble
 *       loose; fissures that erupt under the player one after another.</li>
 *   <li>Below 60 %: roars and also tears open portals full of imps, leaps onto the player's spot (shockwaves on
 *       landing) and calls down a rain of hellfire meteors on marked spots.</li>
 *   <li>Below 30 %: enraged; faster, double shockwaves, bigger volleys, more fissures and meteors.</li>
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
    private static final double GRAVITY = 800;          // Game's physics gravity
    // charge: a bull rush across the arena that ends against a gate
    private static final double CHARGE_WINDUP = 0.8, CHARGE_SPEED = 950, CHARGE_DAMAGE = 24, CHARGE_MAX = 2200;
    private static final double CHARGE_LOW = 130;       // only its lowered horns and cleaver hit: jump over it
    private static final double DAZE_TIME = 1.5;
    // the hitbox is far narrower than the drawn body: keep the body out of the gates
    private static final double CRASH_GAP = 150;        // head and shoulders reach this far past the hitbox
    // leap: jumps onto the spot the player stood on
    private static final double LEAP_CROUCH = 0.55, LEAP_VY = 700, LEAP_DAMAGE = 30, LEAP_REACH = 140, LAND_TIME = 0.6;
    // fissures: the ground erupts under the player, one after another
    private static final double FISSURE_DAMAGE = 22, FISSURE_STAB = 0.6, FISSURE_GAP = 0.5;
    // hellfire rain: meteors on marked spots all over the arena
    private static final double METEOR_DAMAGE = 16, RAIN_RAISE = 0.75, RAIN_SPREAD = 0.22;

    private static Entity current;

    private enum State { DROP, WALK, SLAM, VOLLEY, SUMMON, ROAR, RECOVER, CHARGE, DAZED, LEAP, LAND, FISSURE, RAIN }

    private State state = State.DROP;
    private double timer;
    private double cooldown = 1.5;
    private double summonCooldown = 4;
    private boolean fired;
    private int phase = 1;
    private boolean falling;
    private double rainCooldown = 6;
    private State lastAttack;
    // the attack in progress
    private boolean hitDone, airborne;
    private int chargeDir, left;
    private double chargeStartX, lastX, next, leapTargetX, leapGroundY;

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
        rainCooldown -= t;
        int now = hpFraction() < 0.3 ? 3 : hpFraction() < 0.6 ? 2 : 1;
        if (now > phase && state == State.WALK) {
            if (phase < 2) rainCooldown = 7;      // not right on top of the first summon
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
            case CHARGE -> charge(tpf, t);
            case DAZED -> {
                stop();
                timer -= t;
                sprite.loop("idle", 3);
                if (Math.random() < t * 6) {
                    Particles.burst(entity.getCenter().add(FXGLMath.random(-40, 40), -170), Particles.SPARK, 2, -90, 60, 40, 90, 0.5, 4, 100);
                }
                if (timer <= 0) {
                    state = State.RECOVER;
                    timer = 0.3;
                }
            }
            case LEAP -> leap(t);
            case LAND -> {
                stop();
                timer += t;
                sprite.show("slam", timer < LAND_TIME * 0.3 ? 3 : timer < LAND_TIME * 0.65 ? 4 : 5);
                if (timer >= LAND_TIME) {
                    state = State.RECOVER;
                    timer = 0.4;
                    cooldown = FXGLMath.random(1.2, 1.7);
                }
            }
            case FISSURE -> fissure(t);
            case RAIN -> rain(t);
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
        } else if (phase >= 2 && rainCooldown <= 0) {
            start(State.RAIN);
            sprite.setTelegraph(TELL_SPELL);
            Sfx.CAST_FIREBALL.at(entity.getCenter(), 1, 0.5);
        } else if (adx < SLAM_RANGE) {
            start(State.SLAM);
            sprite.setTelegraph(TELL_MELEE);
            Sfx.BRUTE_ROAR.at(entity.getCenter(), 1, 0.7);
        } else {
            startRanged(adx);
        }
    }

    /** Out of cleaver reach: one of the ranged attacks, never the same one twice in a row if there is a choice. */
    private void startRanged(double adx) {
        List<State> options = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        if (adx < 1100) { options.add(State.VOLLEY); weights.add(3.0); }
        if (adx > 400) { options.add(State.CHARGE); weights.add(2.5); }
        options.add(State.FISSURE);
        weights.add(2.0);
        if (phase >= 2 && adx > 450) { options.add(State.LEAP); weights.add(2.5); }
        if (options.size() > 1 && options.contains(lastAttack)) {
            weights.remove(options.indexOf(lastAttack));
            options.remove(lastAttack);
        }
        double roll = Math.random() * weights.stream().mapToDouble(Double::doubleValue).sum();
        State pick = options.get(options.size() - 1);
        for (int i = 0; i < options.size(); i++) {
            roll -= weights.get(i);
            if (roll <= 0) {
                pick = options.get(i);
                break;
            }
        }
        start(pick);
        switch (pick) {
            case VOLLEY -> {
                sprite.setTelegraph(TELL_RANGED);
                Sfx.CAST_FIREBALL.at(entity.getCenter(), 0.9, 0.7);
            }
            case CHARGE -> {
                sprite.setTelegraph(TELL_MELEE);
                Sfx.HOUND_GROWL.at(entity.getCenter(), 1, 0.45);
            }
            case FISSURE -> {
                sprite.setTelegraph(TELL_SPELL);
                Sfx.BRUTE_ROAR.at(entity.getCenter(), 0.9, 0.85);
            }
            case LEAP -> {
                sprite.setTelegraph(TELL_MELEE);
                Sfx.BRUTE_ROAR.at(entity.getCenter(), 1, 0.6);
            }
            default -> {}
        }
    }

    private void start(State s) {
        state = s;
        timer = 0;
        fired = false;
        hitDone = false;
        airborne = false;
        if (s != State.SUMMON && s != State.ROAR) lastAttack = s;
        face(dirToPlayer());
    }

    //-----charge-----
    private void charge(double tpf, double t) {
        timer += t;
        double front = entity.getCenter().getX() + facing * entity.getWidth() / 2;
        if (timer < CHARGE_WINDUP) {
            // pawing the ground, cleaver scraping sparks
            stop();
            face(dirToPlayer());
            sprite.show("slam", 3);
            if (Math.random() < t * 30) {
                Point2D tip = new Point2D(entity.getCenter().getX() + facing * (entity.getWidth() / 2 + 90), entity.getBottomY() - 8);
                Particles.burst(tip, Particles.SPARK, 3, -90 - facing * 50, 30, 120, 260, 0.3, 4, 600);
            }
            if (Math.random() < t * 12) {
                Particles.burst(new Point2D(entity.getCenter().getX() - facing * 60, entity.getBottomY() - 4),
                        Particles.DUST, 3, -90 - facing * 60, 25, 80, 200, 0.4, 6, 600);
            }
            return;
        }
        if (!fired) {
            fired = true;
            sprite.setTelegraph(null);
            chargeDir = facing;
            chargeStartX = entity.getX();
            lastX = Double.NaN;
            Sfx.BOSS_ROAR.at(entity.getCenter(), 0.9, 1.25);
            Sfx.HOUND_LUNGE.at(entity.getCenter(), 1, 0.5);
        }
        facing = chargeDir;
        double speed = CHARGE_SPEED * stats.getSpeedMult() * (phase == 3 ? 1.15 : 1);
        physics.setVelocityX(chargeDir * speed);
        sprite.loop("roar", 10);
        if (Math.random() < tpf * 30) {
            Particles.burst(new Point2D(entity.getCenter().getX() - chargeDir * 70, entity.getBottomY() - 4),
                    Particles.DUST, 2, -90 - chargeDir * 60, 25, 60, 180, 0.45, 7, 500);
        }
        if (!hitDone && entity.isColliding(player) && player.getBottomY() > entity.getBottomY() - CHARGE_LOW) {
            hitDone = hurtPlayer(CHARGE_DAMAGE);
        }
        boolean wall = BossArena.wallGap(front, chargeDir) < CRASH_GAP;
        boolean stalled = !Double.isNaN(lastX) && Math.abs(entity.getX() - lastX) < 0.5 && timer > CHARGE_WINDUP + 0.2;
        lastX = entity.getX();
        if (wall || stalled) {
            crash(front);
        } else if (Math.abs(entity.getX() - chargeStartX) > CHARGE_MAX) {
            stop();
            state = State.RECOVER;
            timer = 0.6;
            cooldown = FXGLMath.random(1.2, 1.6);
        }
    }

    /** Head first into the gate: the arena shakes, rubble rains down, and the Lord is dazed for a moment. */
    private void crash(double front) {
        stop();
        Point2D at = new Point2D(front + chargeDir * CRASH_GAP, entity.getBottomY() - 160);
        Sfx.BRUTE_SLAM.at(at, 1, 0.55);
        Sfx.GATE_SLAM.at(at, 0.8, 0.8);
        Impact.shake(24);
        Particles.burst(at, Particles.EMBER, 30, 180 + chargeDir * 180, 70, 150, 450, 0.7, 6, 500);
        Particles.burst(at, Particles.DUST, 24, 180 + chargeDir * 180, 80, 100, 360, 0.7, 8, 700);
        int rubble = phase == 3 ? 5 : phase == 2 ? 3 : 2;
        for (int i = 0; i < rubble; i++) meteor(BossArena.randomX(100, entity.getCenter().getX()));
        state = State.DAZED;
        timer = DAZE_TIME;
        cooldown = FXGLMath.random(1.0, 1.4);
    }

    //-----leap-----
    private void leap(double t) {
        timer += t;
        if (!airborne) {
            stop();
            face(dirToPlayer());
            sprite.show("slam", 0);
            if (timer < LEAP_CROUCH) return;
            airborne = true;
            falling = false;
            sprite.setTelegraph(null);
            leapGroundY = entity.getBottomY();
            leapTargetX = BossArena.insideX(player.getCenter().getX(), entity.getWidth() / 2 + CRASH_GAP);
            double flight = 2 * LEAP_VY / GRAVITY;
            physics.setVelocityY(-LEAP_VY);
            physics.setVelocityX((leapTargetX - entity.getCenter().getX()) / flight);
            grounded = false;
            Sfx.IMP_LEAP.at(entity.getCenter(), 1, 0.4);
            Particles.burst(new Point2D(entity.getCenter().getX(), leapGroundY - 4), Particles.DUST, 24, -90, 80, 120, 380, 0.7, 8, 800);
            return;
        }
        sprite.show("slam", 1);
        // the landing spot smoulders
        if (Math.random() < t * 25) {
            Particles.burst(new Point2D(leapTargetX + FXGLMath.random(-90, 90), leapGroundY - 2),
                    Particles.EMBER, 1, -90, 20, 40, 120, 0.4, 5, 100);
        }
        if (physics.getVelocityY() > 50) falling = true;
        if ((falling && grounded) || timer > LEAP_CROUCH + 4) land();
    }

    private void land() {
        stop();
        double groundY = entity.getBottomY();
        double cx = entity.getCenter().getX();
        Sfx.BRUTE_SLAM.at(entity.getCenter(), 1, 0.65);
        Impact.shake(24);
        Particles.burst(new Point2D(cx, groundY - 4), Particles.DUST, 40, -90, 85, 180, 600, 0.9, 9, 900);
        Particles.burst(new Point2D(cx, groundY - 4), Particles.EMBER, 22, -90, 70, 150, 480, 0.7, 6, 600);
        boolean low = player.getBottomY() > groundY - 120;
        if (Math.abs(dxToPlayer()) < entity.getWidth() / 2 + LEAP_REACH && low) hurtPlayer(LEAP_DAMAGE);
        double half = entity.getWidth() / 2;
        ShockwaveComponent.spawn(cx + half, groundY, 1, entity);
        ShockwaveComponent.spawn(cx - half, groundY, -1, entity);
        if (phase == 3) {
            Entity self = entity;
            runOnce(() -> {
                if (!self.isActive()) return;
                ShockwaveComponent.spawn(cx + half, groundY, 1, self);
                ShockwaveComponent.spawn(cx - half, groundY, -1, self);
            }, Duration.seconds(0.4));
        }
        state = State.LAND;
        timer = 0;
    }

    //-----fissures-----
    private void fissure(double t) {
        stop();
        timer += t;
        sprite.show("slam", timer < FISSURE_STAB * 0.5 ? 0 : timer < FISSURE_STAB ? 1 : timer < FISSURE_STAB + 0.1 ? 3 : 4);
        if (!fired && timer >= FISSURE_STAB) {
            // the cleaver goes into the ground and the cracks run out from it
            fired = true;
            sprite.setTelegraph(null);
            Point2D tip = new Point2D(entity.getCenter().getX() + facing * (entity.getWidth() / 2 + 100), entity.getBottomY() - 6);
            Sfx.BRUTE_SLAM.at(tip, 0.8, 0.9);
            Impact.shake(10);
            Particles.burst(tip, Particles.EMBER, 16, -90, 50, 100, 300, 0.6, 6, 600);
            left = phase == 3 ? 5 : 3;
            next = 0;
        }
        if (!fired) return;
        double gap = FISSURE_GAP * (phase == 3 ? 0.8 : 1);
        if (left > 0 && timer >= FISSURE_STAB + next) {
            double x = BossArena.insideX(player.getCenter().getX(), 60);
            double ground = Combat.groundYBelow(x, entity.getY());
            if (!Double.isNaN(ground)) FissureComponent.spawn(x, ground, FISSURE_DAMAGE, entity);
            left--;
            next += gap;
        }
        if (left == 0 && timer >= FISSURE_STAB + next + 0.3) {
            state = State.RECOVER;
            timer = 0.5;
            cooldown = FXGLMath.random(1.0, 1.5);
        }
    }

    //-----hellfire rain-----
    private void rain(double t) {
        stop();
        timer += t;
        sprite.show("cast", timer < RAIN_RAISE * 0.35 ? 0 : timer < RAIN_RAISE * 0.7 ? 1 : timer < RAIN_RAISE ? 2 : 3);
        if (!fired && timer >= RAIN_RAISE) {
            // hurls a fireball up into the dark; it comes back down as many
            fired = true;
            sprite.setTelegraph(null);
            Point2D hand = entity.getCenter().add(facing * 20, -190);
            Sfx.HELLFIRE_LAUNCH.at(hand, 1, 0.6);
            Particles.burst(hand, Particles.EMBER, 30, -90, 12, 600, 1100, 0.8, 8, 0);
            left = phase == 3 ? 9 : 6;
            next = 0.5;
        }
        if (!fired) return;
        if (left > 0 && timer >= RAIN_RAISE + next) {
            // every third one comes down where the player is
            boolean aimed = left % 3 == 0;
            meteor(aimed ? BossArena.insideX(player.getCenter().getX(), 60) : BossArena.randomX(80, player.getCenter().getX()));
            left--;
            next += RAIN_SPREAD * (phase == 3 ? 0.8 : 1);
        }
        if (left == 0 && timer >= RAIN_RAISE + next + 0.3) {
            state = State.RECOVER;
            timer = 0.5;
            rainCooldown = phase == 3 ? 9 : 12;
            cooldown = FXGLMath.random(1.0, 1.5);
        }
    }

    private void meteor(double x) {
        double ground = Combat.groundYBelow(x, entity.getY());
        if (!Double.isNaN(ground)) MeteorComponent.spawn(x, ground, METEOR_DAMAGE, entity);
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
        airborne = false;
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
