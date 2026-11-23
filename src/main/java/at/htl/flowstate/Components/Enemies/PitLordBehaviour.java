package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Assets;
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
import javafx.scene.image.ImageView;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;
import static com.almasb.fxgl.dsl.FXGL.runOnce;
import static com.almasb.fxgl.dsl.FXGL.spawn;

/**
 * Pit Lord, the mini-boss. Fought in a walled-off arena (BossArena): it drops in behind the player, lands with
 * a crash and roars, then fights in three phases by health:
 * <ol>
 *   <li>Up close: a sword slam (direct hit plus shockwaves both ways) or a sideways swing (a wide sweep at waist
 *       height: jump it; enraged it comes back as a backhand). From range: magma volleys; a bull charge
 *       across the arena (only the lowered horns hit: jump it) that ends dazed against a gate, shaking rubble
 *       loose; fissures that erupt under the player one after another.</li>
 *   <li>Below 60 %: roars and also tears open portals full of imps, leaps onto the player's spot (shockwaves on
 *       landing) and calls down a rain of hellfire meteors on marked spots.</li>
 *   <li>Below 30 %: enraged; faster, double shockwaves, bigger volleys, more fissures and meteors.</li>
 * </ol>
 * No knockback, stuns are cut to a third. Its death is a cutscene: staggered, it drops the sword (which stays,
 * stuck in the ground), sinks to its knees while lava cracks spread from the core, roars once more and bursts,
 * taking its minions with it; then the gates open and it grants two skill points.
 * <p>
 * Every blow is announced: a long wind-up with the sprite's telegraph glow and a glint just before it falls; aims
 * (the charge's direction, the leap's landing spot, the volley's targets) are fixed when the wind-up starts.
 * The blows themselves land heavy: a freeze frame, a quake, rubble and layered low impacts; a hit on the player
 * freezes longer.
 * <p>
 * Art: the goat-skull executioner with a two-handed flamberge, drawn by tools/art/lord2d.py. Every strip is a set of
 * key poses sampled at twice the key count, so animations are driven by a key position over time ({@link #keyAt},
 * {@link #showKey}) rather than by frame numbers.
 */
public class PitLordBehaviour extends GroundDemonBehaviour {
    private static final double SPEED = 80;
    private static final double RAGE = 1.4;
    private static final double CONTACT_DAMAGE = 12;
    private static final double SLAM_DAMAGE = 28;
    private static final double SLAM_RANGE = 330;
    private static final double SLAM_REACH = 260;      // where the blade bites into the ground (past the hitbox)
    private static final double SLAM_LOW = 120;        // jumping higher than this clears the direct hit
    private static final double BALL_DAMAGE = 14;
    private static final double BALL_SPEED = 780;
    private static final double DROP_SPEED = 500;      // it doesn't drift down, it's hurled in
    private static final int REWARD_SP = 2;
    // slam timeline (s)
    private static final double S_RAISE = 0.35, S_HOLD = 0.9, S_DOWN = 0.98, S_IMPACT = 1.2, S_SETTLE = 1.4, S_END = 1.75;
    // volley timeline (s)
    private static final double V_RELEASE = 0.9, V_END = 1.3;
    private static final double V_AIM = 0.4;             // the volley's spots are picked this early
    private static final double ROAR_TIME = 1.2;
    private static final double GRAVITY = 800;          // Game's physics gravity
    // charge: a bull rush across the arena that ends against a gate
    // it straps the sword to its back, paws the ground, gallops head down, crashes, shakes it off, draws again
    private static final double SHEATHE_TIME = 0.75, PAW_TIME = 0.55, CHARGE_WINDUP = SHEATHE_TIME + PAW_TIME;
    private static final double CHARGE_SPEED = 950, CHARGE_DAMAGE = 24, CHARGE_MAX = 2200;
    private static final double IMPACT_TIME = 0.22, REBOUND_TIME = 0.3, REBOUND_SPEED = 260, DRAW_TIME = 0.9;
    private static final double CHARGE_LOW = 130;       // only its lowered horns and sword hit: jump over it
    private static final double DAZE_TIME = 1.5;
    // the hitbox (150 px) is far narrower than the drawn body: keep the drawing out of the gates
    private static final double CRASH_GAP = 150;        // the lowered horns reach this far past the hitbox
    private static final double WALL_MARGIN = 300;      // the sword chopped into the ground reaches this far
    // the sideways swing: a wide sweep at waist height, jump over it (enraged: a backhand follows)
    private static final double SWING_DAMAGE = 26, SWING_RANGE = 400, SWING_REACH = 320, SWING_HIGH = 170;
    // wound back and held, then a fast sweep: the blade is a blur for under a tenth of a second
    private static final double[] SWING_ONCE = {0, 0, 0.45, 1, 0.85, 1, 0.9, 2, 0.96, 3, 1.12, 4, 1.55, 5};
    private static final double[] SWING_TWICE = {0, 0, 0.45, 1, 0.85, 1, 0.9, 2, 0.96, 3, 1.12, 4, 1.3, 3, 1.38, 2, 1.6, 1, 2.0, 0};
    private static final double[] SWING_HITS_ONCE = {0.95}, SWING_HITS_TWICE = {0.95, 1.31};
    private static final double GLINT_LEAD = 0.28;      // the blade flashes this long before it falls

    /** A strip: key poses sampled at twice the key count (tools/art/lord2d.py). */
    private record Strip(String name, int keys, int frames) {}

    private static final Strip SLAM = new Strip("slam", 6, 12), CAST = new Strip("cast", 4, 8),
            SHEATHE = new Strip("sheathe", 4, 8), CRASH = new Strip("crash", 2, 4), DRAW = new Strip("draw", 4, 8),
            LEAP = new Strip("leap", 3, 6), STAB = new Strip("stab", 3, 6), SWING = new Strip("swing", 6, 12),
            DEATH = new Strip("death", 5, 10);
    // death scene timeline (s after the killing blow)
    private static final double D_DROP = 0.45, D_KNEEL = 1.0, D_SLUMP = 1.45, D_ROAR = 2.5, D_FLARE = 2.8, D_BURST = 3.1;
    private static final double STUCK_CLEAVER_X = 156;   // where the dropped sword sticks, ahead of the centre (px)
    // looping strips and their rates (twice the old ones)
    private static final double IDLE_FPS = 12, WALK_FPS = 14, RAGE_WALK_FPS = 18, CHARGE_FPS = 28, DAZED_FPS = 10, ROAR_FPS = 12;
    // leap: jumps onto the spot the player stood on
    private static final double LEAP_CROUCH = 0.75, LEAP_VY = 700, LEAP_DAMAGE = 30, LEAP_REACH = 140, LAND_TIME = 0.6;
    private static final double LEAP_LOW = 120;
    // fissures: the ground erupts under the player, one after another
    private static final double FISSURE_DAMAGE = 22, FISSURE_STAB = 0.6, FISSURE_GAP = 0.5;
    // hellfire rain: meteors on marked spots all over the arena
    private static final double METEOR_DAMAGE = 16, RAIN_RAISE = 0.75, RAIN_SPREAD = 0.22;

    private static Entity current;

    private enum State { DROP, WALK, SLAM, SWING, VOLLEY, SUMMON, ROAR, RECOVER, CHARGE, CRASHED, DAZED, DRAW, LEAP, LAND, FISSURE, RAIN }

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
    private boolean hitDone, airborne, cue;
    private boolean sheathed;                            // the sword is on its back (charge to draw)
    private int chargeDir, left;
    private double chargeStartX, lastX, next, leapTargetX, leapGroundY;
    private int hits;                                    // swing blows landed so far this swing
    private boolean dying;
    private double deathT;
    private int deathCue;
    private final List<Point2D> aims = new ArrayList<>();
    private boolean glinted;

    /** The living Pit Lord, if any (the HUD shows its health bar). */
    public static Entity current() {
        return current != null && current.isActive() ? current : null;
    }

    /** Key position at time t, linear between breakpoints given as {time, key, time, key, ...}. */
    private static double keyAt(double t, double... tk) {
        if (t <= tk[0]) return tk[1];
        for (int i = 2; i < tk.length; i += 2) {
            if (t <= tk[i]) return tk[i - 1] + (tk[i + 1] - tk[i - 1]) * (t - tk[i - 2]) / (tk[i] - tk[i - 2]);
        }
        return tk[tk.length - 1];
    }

    /** Shows the frame of a strip nearest to a key position (0 = first key pose, keys - 1 = the last). */
    private void showKey(Strip s, double key) {
        double k = Math.max(0, Math.min(s.keys() - 1, key));
        sprite.show(s.name(), (int) Math.round(k / (s.keys() - 1) * (s.frames() - 1)));
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
                sprite.show("roar", (int) (timer * ROAR_FPS) % 4);
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
            case CRASHED -> {
                // horns into the gate, then bouncing back off it
                timer += t;
                showKey(CRASH, keyAt(timer, 0, 0, IMPACT_TIME, 0.3, IMPACT_TIME + REBOUND_TIME, 1));
                if (timer < IMPACT_TIME) {
                    stop();
                } else if (timer < IMPACT_TIME + REBOUND_TIME) {
                    physics.setVelocityX(-chargeDir * REBOUND_SPEED);
                } else {
                    stop();
                    state = State.DAZED;
                    timer = DAZE_TIME;
                }
            }
            case DAZED -> {
                stop();
                timer -= t;
                sprite.loop("dazed", DAZED_FPS);
                if (Math.random() < t * 6) {
                    Particles.burst(entity.getCenter().add(facing * 30 + FXGLMath.random(-40, 40), -140), Particles.SPARK, 2, -90, 60, 40, 90, 0.5, 4, 100);
                }
                if (timer <= 0) startDraw();
            }
            case DRAW -> draw(t);
            case SWING -> swing(t);
            case LEAP -> leap(t);
            case LAND -> {
                stop();
                timer += t;
                showKey(SLAM, keyAt(timer, 0, 3, LAND_TIME, 5));
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
                sprite.loop("idle", IDLE_FPS);
                if ((timer -= t) <= 0) state = State.WALK;
            }
        }
    }

    /** Falling in from above the screen, sword raised; crashes down and roars. */
    private void drop(double t) {
        stop();
        face(dirToPlayer());
        timer += t;
        if (physics.getVelocityY() > 50) falling = true;
        showKey(SLAM, falling && !grounded ? 1 : 4);
        // landed (or spawned standing on the ground: nothing to fall from)
        if (grounded && (falling || timer > 0.3)) {
            Point2D feet = new Point2D(entity.getCenter().getX(), entity.getBottomY() - 4);
            Sfx.BRUTE_SLAM.at(entity.getCenter(), 1, 0.6);
            Impact.quake(26);
            Impact.hitStop(0.1);
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
            sprite.loop("idle", IDLE_FPS);
            return;
        }
        double dx = dxToPlayer();
        double adx = Math.abs(dx);
        walk(adx < 170 ? 0 : dx > 0 ? 1 : -1, SPEED * (phase == 3 ? RAGE : 1));
        face(dirToPlayer());
        touch(CONTACT_DAMAGE);
        locomotion("idle", IDLE_FPS, "walk", phase == 3 ? RAGE_WALK_FPS : WALK_FPS, "idle", IDLE_FPS);
        if (!grounded || cooldown > 0) return;
        if (phase >= 2 && summonCooldown <= 0) {
            start(State.SUMMON);
            Sfx.BOSS_ROAR.at(entity.getCenter(), 0.8, 1.1);
        } else if (phase >= 2 && rainCooldown <= 0) {
            start(State.RAIN);
            sprite.setTelegraph(TELL_SPELL);
            Sfx.CAST_FIREBALL.at(entity.getCenter(), 1, 0.5);
        } else if (adx < SWING_RANGE) {
            // up close: the overhead slam or the sideways swing (only the swing reaches this far), not the same twice
            boolean swing = adx >= SLAM_RANGE || (lastAttack == State.SLAM ? Math.random() < 0.75 : lastAttack != State.SWING && Math.random() < 0.5);
            start(swing ? State.SWING : State.SLAM);
            sprite.setTelegraph(TELL_MELEE);
            Sfx.BRUTE_ROAR.at(entity.getCenter(), 1, swing ? 0.8 : 0.7);
        } else {
            startRanged(adx);
        }
    }

    /** Out of sword reach: one of the ranged attacks, never the same one twice in a row if there is a choice. */
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
        cue = false;
        hits = 0;
        glinted = false;
        if (s != State.SUMMON && s != State.ROAR) lastAttack = s;
        face(dirToPlayer());
        aim(s);
    }

    //-----telegraphs-----
    /** Fixes where the attack just started is going, at the start of its wind-up (no tracking during it). */
    private void aim(State s) {
        if (s == State.CHARGE) chargeDir = facing;
        if (s == State.LEAP) leapTargetX = BossArena.insideX(player.getCenter().getX(), entity.getWidth() / 2 + WALL_MARGIN);
    }

    /** The last beat before a blow: the blade (or the eyes) flash and ring. */
    private void glint(Point2D at) {
        if (glinted) return;
        glinted = true;
        Particles.burst(at, Particles.SPARK, 14, -90, 180, 80, 260, 0.35, 5, 0);
        Sfx.DEFLECT.at(at, 0.7, 0.55);
    }

    //-----weight-----
    /** A blow meeting the ground: freeze frame, quake, rubble and dust thrown up, a deep layered crunch. */
    private void heavy(Point2D at, double quake, double freeze) {
        Impact.quake(quake);
        Impact.hitStop(freeze);
        Particles.burst(at, Particles.ROCK, 22, -90, 60, 220, 680, 1.1, 8, 1500);
        Particles.burst(at, Particles.DUST, 34, -90, 80, 160, 560, 0.9, 9, 900);
        Particles.burst(at, Particles.EMBER, 18, -90, 60, 160, 480, 0.7, 6, 600);
        Sfx.BRUTE_SLAM.at(at, 1, 0.6);
        Sfx.GATE_SLAM.at(at, 0.55, 0.55);
        Sfx.EXPLOSION.at(at, 0.35, 0.45);
    }

    /** Hurts the player with a heavy blow: on a hit everything stops a little longer and the screen jolts. */
    private boolean crush(double damage) {
        boolean hit = hurtPlayer(damage);
        if (hit) {
            Impact.hitStop(0.15);
            Impact.quake(18);
            Sfx.HIT_BLUNT.at(player.getCenter(), 1, 0.55);
            Particles.burst(player.getCenter(), Particles.SPARK, 12, -90, 180, 150, 420, 0.4, 5, 300);
        }
        return hit;
    }

    //-----charge-----
    private void charge(double tpf, double t) {
        timer += t;
        double front = entity.getCenter().getX() + facing * entity.getWidth() / 2;
        if (timer < SHEATHE_TIME) {
            // the sword goes up over the shoulder and onto the back (it has picked its lane: no turning now)
            stop();
            face(chargeDir);
            double key = keyAt(timer, 0, 0, SHEATHE_TIME * 0.85, 3);
            showKey(SHEATHE, key);
            if (key >= 2 && !cue) {
                cue = true;
                sheathed = true;
                Sfx.BLOCK_RAISE.at(entity.getCenter(), 1, 0.55);
            }
            return;
        }
        if (timer < CHARGE_WINDUP) {
            // head down, pawing the ground like a bull
            stop();
            face(chargeDir);
            showKey(SHEATHE, 3);
            if (cue) {
                cue = false;
                Sfx.HOUND_GROWL.at(entity.getCenter(), 1, 0.4);
            }
            if (Math.random() < t * 14) {
                Particles.burst(new Point2D(entity.getCenter().getX() - facing * 20, entity.getBottomY() - 4),
                        Particles.DUST, 4, -90 - facing * 65, 20, 90, 230, 0.45, 6, 600);
            }
            return;
        }
        if (!fired) {
            fired = true;
            sprite.setTelegraph(null);
            chargeStartX = entity.getX();
            lastX = Double.NaN;
            Sfx.BOSS_ROAR.at(entity.getCenter(), 0.9, 1.25);
            Sfx.HOUND_LUNGE.at(entity.getCenter(), 1, 0.5);
        }
        facing = chargeDir;
        double speed = CHARGE_SPEED * stats.getSpeedMult() * (phase == 3 ? 1.15 : 1);
        physics.setVelocityX(chargeDir * speed);
        sprite.loop("charge", CHARGE_FPS);
        if (Math.random() < tpf * 30) {
            Particles.burst(new Point2D(entity.getCenter().getX() - chargeDir * 70, entity.getBottomY() - 4),
                    Particles.DUST, 2, -90 - chargeDir * 60, 25, 60, 180, 0.45, 7, 500);
        }
        if (!hitDone && entity.isColliding(player) && player.getBottomY() > entity.getBottomY() - CHARGE_LOW) {
            hitDone = crush(CHARGE_DAMAGE);
        }
        // look one frame ahead: a long frame at this speed would otherwise carry the horns into the gate
        boolean wall = BossArena.wallGap(front, chargeDir) - speed * tpf < CRASH_GAP;
        boolean stalled = !Double.isNaN(lastX) && Math.abs(entity.getX() - lastX) < 0.5 && timer > CHARGE_WINDUP + 0.2;
        lastX = entity.getX();
        if (wall || stalled) {
            crash(front);
        } else if (Math.abs(entity.getX() - chargeStartX) > CHARGE_MAX) {
            // ran out of steam without hitting anything: skid to a halt and arm itself again
            stop();
            Particles.burst(new Point2D(entity.getCenter().getX(), entity.getBottomY() - 4), Particles.DUST, 20, -90 - chargeDir * 50, 40, 100, 300, 0.6, 7, 700);
            startDraw();
        }
    }

    private void startDraw() {
        state = State.DRAW;
        timer = 0;
        cue = false;
    }

    /** The sword comes back off its back: reach, grip, swing it overhead, a flourish forward. */
    private void draw(double t) {
        stop();
        timer += t;
        double key = keyAt(timer, 0, 0, DRAW_TIME * 0.67, 3);
        showKey(DRAW, key);
        if (key >= 1) sheathed = false;
        if (key >= 2 && !cue) {
            cue = true;
            Sfx.SWING_HEAVY.at(entity.getCenter(), 1, 0.6);
        }
        if (key >= 3 && !fired) {
            fired = true;
            Point2D blade = entity.getCenter().add(facing * 300, 40);
            Particles.burst(blade, Particles.EMBER, 14, facing > 0 ? 0 : 180, 40, 80, 260, 0.5, 5, 200);
            Sfx.BRUTE_ROAR.at(entity.getCenter(), 0.7, 0.8);
        }
        if (timer >= DRAW_TIME + 0.15) {
            state = State.RECOVER;
            timer = 0.3;
        }
    }

    /** Head first into the gate: the arena shakes, rubble rains down, and the Lord is dazed for a moment. */
    private void crash(double front) {
        stop();
        // horns exactly at the gate, not in it or short of it (at most a frame's worth of movement)
        double shift = BossArena.wallGap(front, chargeDir) - CRASH_GAP;
        if (Math.abs(shift) < 120) {
            physics.overwritePosition(entity.getPosition().add(chargeDir * shift, 0));
            front += chargeDir * shift;
        }
        Point2D at = new Point2D(front + chargeDir * CRASH_GAP, entity.getBottomY() - 160);
        Sfx.BRUTE_SLAM.at(at, 1, 0.55);
        Sfx.GATE_SLAM.at(at, 0.8, 0.8);
        Sfx.EXPLOSION.at(at, 0.3, 0.5);
        Impact.quake(24);
        Impact.hitStop(0.1);
        Particles.burst(at, Particles.ROCK, 16, 180 + chargeDir * 180, 70, 150, 450, 1.0, 8, 1400);
        Particles.burst(at, Particles.EMBER, 30, 180 + chargeDir * 180, 70, 150, 450, 0.7, 6, 500);
        Particles.burst(at, Particles.DUST, 24, 180 + chargeDir * 180, 80, 100, 360, 0.7, 8, 700);
        int rubble = phase == 3 ? 5 : phase == 2 ? 3 : 2;
        for (int i = 0; i < rubble; i++) meteor(BossArena.randomX(100, entity.getCenter().getX()));
        state = State.CRASHED;
        timer = 0;
        fired = false;
        cooldown = FXGLMath.random(1.0, 1.4);
    }

    //-----leap-----
    private void leap(double t) {
        timer += t;
        if (!airborne) {
            stop();
            face(leapTargetX >= entity.getCenter().getX() ? 1 : -1);
            showKey(LEAP, 0);
            if (timer >= LEAP_CROUCH - GLINT_LEAD) glint(entity.getCenter().add(facing * 20, -200));
            if (timer < LEAP_CROUCH) return;
            airborne = true;
            falling = false;
            sprite.setTelegraph(null);
            leapGroundY = entity.getBottomY();
            double flight = 2 * LEAP_VY / GRAVITY;
            physics.setVelocityY(-LEAP_VY);
            physics.setVelocityX((leapTargetX - entity.getCenter().getX()) / flight);
            grounded = false;
            Sfx.IMP_LEAP.at(entity.getCenter(), 1, 0.4);
            Particles.burst(new Point2D(entity.getCenter().getX(), leapGroundY - 4), Particles.DUST, 24, -90, 80, 120, 380, 0.7, 8, 800);
            return;
        }
        // rising (1) through the apex to coming down (2), following the vertical speed
        showKey(LEAP, 1 + Math.max(0, Math.min(1, (physics.getVelocityY() + 300) / 600)));
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
        heavy(new Point2D(cx, groundY - 4), 24, 0.09);
        Particles.burst(new Point2D(cx, groundY - 4), Particles.DUST, 30, -90, 85, 180, 600, 0.9, 9, 900);
        boolean low = player.getBottomY() > groundY - LEAP_LOW;
        if (Math.abs(dxToPlayer()) < entity.getWidth() / 2 + LEAP_REACH && low) crush(LEAP_DAMAGE);
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
        // sword raised point-up, driven into the ground, held there glowing while the cracks run
        double key = timer < FISSURE_STAB + 0.12 ? keyAt(timer, 0, 0, FISSURE_STAB - 0.1, 0, FISSURE_STAB, 1)
                : 1.5 - 0.5 * Math.cos((timer - FISSURE_STAB - 0.12) * Math.PI * 5);      // the held sword pulses
        showKey(STAB, key);
        if (!fired && timer >= FISSURE_STAB - GLINT_LEAD) glint(entity.getCenter().add(facing * 70, -230));
        if (!fired && timer >= FISSURE_STAB) {
            // the sword goes into the ground and the cracks run out from it
            fired = true;
            sprite.setTelegraph(null);
            Point2D tip = new Point2D(entity.getCenter().getX() + facing * (entity.getWidth() / 2 + 100), entity.getBottomY() - 6);
            heavy(tip, 14, 0.05);
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
        showKey(CAST, keyAt(timer, 0, 0, RAIN_RAISE, 2, RAIN_RAISE + 0.15, 3));
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

    //-----sideways swing-----
    /** Cleaver drawn back behind him, swept round past the camera at waist height and through the front. */
    private void swing(double t) {
        stop();
        timer += t;
        boolean twice = phase == 3;
        double[] line = twice ? SWING_TWICE : SWING_ONCE;
        double[] hitsAt = twice ? SWING_HITS_TWICE : SWING_HITS_ONCE;
        double key = keyAt(timer, line);
        showKey(SWING, key);
        // the sweep itself: a whoosh going in, then a trail of embers along the arc in front
        if (timer >= hitsAt[0] - GLINT_LEAD) glint(entity.getCenter().add(-facing * 120, 60));
        boolean sweeping = timer > 0.86 && timer < (twice ? 1.4 : 1.02);
        if (sweeping && !cue) {
            cue = true;
            sprite.setTelegraph(null);
            Sfx.SWING_HEAVY.at(entity.getCenter(), 1, 0.42);
            Sfx.FLAIL_SWING.at(entity.getCenter(), 0.8, 0.5);
        }
        if (sweeping && Math.random() < t * 40) {
            double reach = entity.getWidth() / 2 + FXGLMath.random(60, SWING_REACH);
            Point2D at = new Point2D(entity.getCenter().getX() + facing * reach, entity.getBottomY() - FXGLMath.random(60, 150));
            Particles.burst(at, Particles.EMBER, 2, facing > 0 ? 0 : 180, 30, 60, 180, 0.35, 5, 100);
        }
        if (hits < hitsAt.length && timer >= hitsAt[hits]) {
            hits++;
            double px = dxToPlayer() * facing;
            boolean inFront = px > -entity.getWidth() / 2 && px < entity.getWidth() / 2 + SWING_REACH;
            boolean low = player.getBottomY() > entity.getBottomY() - SWING_HIGH;
            if (inFront && low) crush(SWING_DAMAGE);
            Impact.quake(10);
            // the wind of it: dust ripped off the ground all along the arc
            for (int i = 0; i < 5; i++) {
                double reach = entity.getWidth() / 2 + SWING_REACH * (0.2 + 0.2 * i);
                Particles.burst(new Point2D(entity.getCenter().getX() + facing * reach, entity.getBottomY() - 4),
                        Particles.DUST, 4, facing > 0 ? -20 : -160, 25, 120, 380, 0.5, 7, 400);
            }
            if (hits == 2) {
                Sfx.SWING_HEAVY.at(entity.getCenter(), 1, 0.5);   // the backhand
                Sfx.FLAIL_SWING.at(entity.getCenter(), 0.8, 0.55);
            }
        }
        if (timer >= line[line.length - 2]) {
            state = State.RECOVER;
            timer = 0.4;
            cooldown = FXGLMath.random(1.2, 1.7);
        }
    }

    private void slam(double t) {
        stop();
        timer += t;
        showKey(SLAM, keyAt(timer, 0, 0, S_RAISE, 1, S_HOLD, 1, S_DOWN - 0.04, 2, S_DOWN, 3, S_IMPACT, 3.4, S_SETTLE, 4, S_END, 5));
        if (timer >= S_DOWN - GLINT_LEAD) glint(entity.getCenter().add(facing * 10, -220));
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
        // where the blade bites into the ground
        double tipX = entity.getCenter().getX() + facing * (entity.getWidth() / 2 + SLAM_REACH);
        heavy(new Point2D(tipX, groundY - 4), 22, 0.09);
        double px = dxToPlayer() * facing;
        boolean low = player.getBottomY() > groundY - SLAM_LOW;
        if (px > -entity.getWidth() / 2 && px < entity.getWidth() / 2 + SLAM_REACH && low) crush(SLAM_DAMAGE);
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
        showKey(CAST, keyAt(timer, 0, 0, V_RELEASE, 2, V_RELEASE + 0.12, 3));
        if (!fired && aims.isEmpty() && timer >= V_AIM) {
            // the spots are picked well before the throw: no last-moment tracking
            int count = phase == 3 ? 5 : 3;
            for (int i = 0; i < count; i++) {
                double offset = (i - (count - 1) / 2.0) * 130;
                Point2D target = player.getCenter().add(offset, 20);
                aims.add(target);
            }
        }
        if (!fired && timer >= V_RELEASE) {
            fired = true;
            sprite.setTelegraph(null);
            Point2D hand = entity.getCenter().add(facing * 40, -150);
            for (Point2D target : aims) HurlerBehaviour.lob(hand, target, BALL_SPEED, BALL_DAMAGE, entity);
            aims.clear();
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
        Impact.quake(16);
        Particles.burst(entity.getCenter().add(facing * 40, -130), Particles.EMBER, 30, -90, 100, 120, 420, 0.9, 6, 200);
    }

    @Override
    protected void interrupt() {
        if (state == State.DROP) return;      // nothing cuts the entrance short
        airborne = false;
        aims.clear();
        if (sheathed) {                        // never pop back to a sword in hand: draw it first
            startDraw();
            return;
        }
        state = State.RECOVER;
        timer = 0.3;
    }

    @Override
    protected void disabledPose() {
        sprite.loop(sheathed ? "dazed" : "idle", DAZED_FPS);
    }

    @Override
    public boolean diesSlowly() {
        return true;
    }

    /** The killing blow: the death scene starts (BossArena takes control away and freezes everything else). */
    @Override
    public void onDeath() {
        if (current == entity) current = null;             // the boss bar goes
        dying = true;
        deathT = 0;
        deathCue = 0;
        physics.setVelocityX(0);
        sprite.setTelegraph(null);
        Sfx.BOSS_ROAR.at(entity.getCenter(), 1, 0.55);
        Impact.quake(18);
        Particles.burst(entity.getCenter().add(0, -40), Particles.EMBER, 30, -90, 120, 100, 380, 0.8, 6, 300);
        BossArena.bossDying(entity);
    }

    @Override
    protected void dying(double tpf) {
        deathT += tpf;
        physics.setVelocityX(0);
        showKey(DEATH, keyAt(deathT, 0, 0, D_DROP, 1, D_KNEEL, 2, D_SLUMP, 3, D_ROAR, 3, D_FLARE, 4));
        Point2D c = entity.getCenter();
        double feet = entity.getBottomY();
        if (deathCue == 0 && deathT >= D_DROP * 0.7) {        // the sword drops and sticks in the ground
            deathCue++;
            Point2D tip = new Point2D(c.getX() + facing * STUCK_CLEAVER_X, feet - 4);
            Sfx.BLOCK_HIT.at(tip, 1, 0.5);
            Sfx.BRUTE_SLAM.at(tip, 0.5, 1.4);
            Particles.burst(tip, Particles.DUST, 16, -90, 60, 60, 200, 0.5, 6, 700);
            Particles.burst(tip, Particles.SPARK, 8, -90, 70, 80, 240, 0.4, 4, 600);
        }
        if (deathCue == 1 && deathT >= D_KNEEL) {             // the knees hit the ground
            deathCue++;
            Sfx.BRUTE_SLAM.at(c, 0.9, 0.5);
            Impact.shake(14);
            Particles.burst(new Point2D(c.getX(), feet - 4), Particles.DUST, 30, -90, 85, 120, 420, 0.8, 8, 900);
        }
        if (deathT >= D_KNEEL) {                               // the cracks spread: embers leak, the ground trembles
            double k = Math.min(1, (deathT - D_KNEEL) / (D_BURST - D_KNEEL));
            if (Math.random() < tpf * (8 + 45 * k)) {
                Particles.burst(c.add(FXGLMath.random(-70, 70), FXGLMath.random(-130, 40)), Particles.EMBER, 2, -90, 40, 40, 160, 0.7, 5, -80);
            }
            if (Math.random() < tpf * 6) Impact.shake(2 + 9 * k);
            if (Math.random() < tpf * 3) Sfx.CRACKLE.at(c, 0.9, 0.6 + 0.4 * k);
        }
        if (deathCue == 2 && deathT >= D_ROAR) {              // one last roar, head thrown back
            deathCue++;
            Sfx.BOSS_ROAR.at(c, 1, 0.7);
        }
        if (deathT >= D_FLARE && Math.random() < tpf * 16) sprite.flash();     // white-hot
        if (deathT >= D_BURST) burst();
    }

    /** The core bursts: the Lord is gone, its sword stays stuck in the ground, the arena opens. */
    private void burst() {
        Point2D c = entity.getCenter();
        Sfx.EXPLOSION.at(c, 1, 0.6);
        Sfx.BOSS_ROAR.at(c, 0.6, 0.45);
        Impact.quake(26);
        Particles.burst(c, Particles.EMBER, 90, -90, 180, 150, 900, 1.4, 8, 350);
        Particles.burst(c, Particles.SPARK, 40, -90, 180, 200, 700, 0.8, 5, 200);
        Particles.burst(new Point2D(c.getX(), entity.getBottomY() - 4), Particles.DUST, 40, -90, 80, 150, 500, 1.0, 9, 900);
        for (int k = 0; k < 6; k++) {
            Particles.burst(c.add(FXGLMath.random(-60, 60), FXGLMath.random(-140, 120)), Particles.EMBER, 20, -90, 180, 100, 520, 1.0, 7, 300);
        }
        PlayerStatsComponent stats = player.getComponent(PlayerStatsComponent.class);
        for (int i = 0; i < REWARD_SP; i++) stats.addSkillPoint();
        runOnce(Sfx.LEVEL_UP::play, Duration.seconds(0.9));
        leaveCleaver();
        BossArena.bossExploded();
        entity.removeFromWorld();
    }

    /** The dropped sword stays where it stuck: the same frame and anchor as the Lord's sprite. */
    private void leaveCleaver() {
        DemonType t = DemonType.LORD;
        ImageView view = Assets.sprite("DemonLord_cleaver.png", 2);
        view.setTranslateX(t.width / 2 - t.anchorX * 2);
        view.setTranslateY(t.height - t.anchorY * 2);
        view.setScaleX(facing);
        entityBuilder().at(entity.getPosition()).view(view).zIndex(-1).buildAndAttach();
    }

    /** Gone without its death scene (fell into a pit, a new game): never leave the way barred. */
    @Override
    public void onRemoved() {
        if (!dying) BossArena.end();
        if (current == entity) current = null;
    }
}
