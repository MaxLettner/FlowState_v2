package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Combat.Hit;
import at.htl.flowstate.Components.Player.PlayerMovementComponent;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Visuals.Impact;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.core.math.FXGLMath;
import com.almasb.fxgl.dsl.components.HealthDoubleComponent;
import com.almasb.fxgl.entity.Entity;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;

/**
 * One of the Tar Hydra's heads (tools/art/hydra.py). It hangs on its neck over the tar, leaning toward the player,
 * and attacks when the body ({@link HydraBehaviour}) tells it to:
 * <ul>
 *   <li>Spitter: rears back, its throat sac glowing, and spits globs of burning tar ({@link TarGlobComponent}).</li>
 *   <li>Biter: rears back with its jaws wide and lunges at where the player was; if it misses a pillar or ledge
 *   its teeth sink into the stone and it is stuck for a moment (takes half again as much).</li>
 *   <li>Lure: sweeps its bulb's light toward the player ({@link LureSpotComponent}); where it locks, the spitter or
 *   the biter strikes next. Later it hypnotises: close by, the player is drawn toward the biter.</li>
 *   <li>Crowned: the last head. Spits, bites and slams the tar into a wave ({@link TarWaveComponent}).</li>
 * </ul>
 * The head is kinematic: everything here moves it by hand. Its hitbox centre is the middle of the head; the neck
 * joins {@link #NECK_BACK} head sizes behind it.
 */
public class HydraHeadBehaviour extends DemonBehaviour {
    /**
     * size: the head's size in art px; base: arena x of the neck's root; rest: where it hangs (x toward the player, y
     * above the floor); layer: the z of the head (and its stump), its neck is one below. Back to front: lure, spitter,
     * biter, crowned, all behind the pillars (-1) and the player (0).
     */
    public enum Kind {
        SPITTER(34, 40, 26, 690, -70, -390, -8, DemonType.HYDRA_SPITTER, DemonType.HYDRA_SPITTER_STUMP),
        BITER(42, 48, 32, 800, 30, -300, -6, DemonType.HYDRA_BITER, DemonType.HYDRA_BITER_STUMP),
        LURE(30, 38, 24, 930, 90, -200, -10, DemonType.HYDRA_LURE, DemonType.HYDRA_LURE_STUMP),
        CROWNED(54, 58, 40, 860, 10, -360, -4, DemonType.HYDRA_CROWNED, null);

        final double size, baseRadius, neckRadius, baseX, restX, restY;
        final int layer;
        final DemonType type, stump;

        Kind(double size, double baseRadius, double neckRadius, double baseX, double restX, double restY, int layer, DemonType type, DemonType stump) {
            this.layer = layer;
            this.size = size;
            this.baseRadius = baseRadius;
            this.neckRadius = neckRadius;
            this.baseX = baseX;
            this.restX = restX;
            this.restY = restY;
            this.type = type;
            this.stump = stump;
        }
    }

    static final double NECK_BACK = 0.9;
    private static final Color TELL = Color.rgb(255, 150, 50, 0.95);
    private static final double FOLLOW = 4.0;                      // how quickly it settles where it wants to be
    private static final double EMERGE_SPEED = 520, RETURN_SPEED = 720, LUNGE_SPEED = 1700;
    private static final double CROWNED_LUNGE = 1100;              // slower than the others: it is the one to dodge
    private static final double REACH = 820;                       // the most a neck stretches from its root
    // spit
    private static final double REAR = 0.6, SPIT_AT = 0.12, SPIT_END = 0.5, SPIT_SPEED = 700, SPIT_SPREAD = 170;
    private static final double POOL_LIFE = 2.5;
    // bite
    private static final double BITE_DAMAGE = 24, CROWNED_BITE = 30, STUCK = 1.5, CROWNED_STUCK = 1.0, STUCK_GUARD = 1.5;
    private static final double PULL = 0.4, RECOVER = 0.3;
    // lure
    private static final double SWEEP = 1.5, SWEEP_SPEED = 560, HYPNO = 1.8, HYPNO_RANGE = 560, HYPNO_PULL = 120,
            HYPNO_BREAK = 25;
    // crowned
    private static final double ROAR = 1.0, WAVE_AT = 0.85, WAVE_END = 1.4;     // the slam is frame 5 of the wave strip
    private static final double DEATH_FALL = 1300;

    private static final double IDLE_FPS = 8, REAR_FPS = 10, SPIT_FPS = 12, BITE_FPS = 14, STUCK_FPS = 10, PULL_FPS = 10,
            SWEEP_FPS = 12, HYPNO_FPS = 10, ROAR_FPS = 8, WAVE_FPS = 6, REGROW_FPS = 10, DEATH_FPS = 12;

    enum State { EMERGE, REGROW, IDLE, REAR, SPIT, LUNGE, RECOVER, STUCK, PULL, RETURN, SWEEP, HYPNO, ROAR, WAVE, DYING }

    private enum Next { SPIT, BITE, WAVE }

    final Kind kind;
    private HydraBehaviour owner;
    private State state = State.EMERGE;
    private Next next;
    private double timer, time, tempo = 1;
    private int facing = 1;
    private double pitch;                                          // roughly where the head points (for the neck)
    private Point2D pos, target;
    private boolean fired, struck;
    private Entity spot;
    private double hypnoStartHp;
    private double fallVy;
    private boolean regrown;                                       // grows out of a stump instead of rising from the tar

    public HydraHeadBehaviour(Kind kind) {
        this.kind = kind;
    }

    /** Set right after the spawn: who runs the fight, and whether it grows back out of a stump. */
    void setOwner(HydraBehaviour owner, boolean regrown, int facing) {
        this.owner = owner;
        this.regrown = regrown;
        this.facing = facing;
    }

    @Override
    protected void onReady() {
        entity.setZIndex(kind.layer);                            // behind the pillars, over its own neck
        pos = entity.getCenter();
        sprite.setFacing(facing);
        if (regrown) regrow();
        else emerge();
    }

    //-----where it is-----
    /** Root of the neck, under the tar. */
    Point2D base() {
        return new Point2D(HydraArena.lockX() + kind.baseX, HydraArena.surface() + 90);
    }

    /** Where it hangs when it isn't attacking. */
    Point2D rest() {
        return new Point2D(HydraArena.lockX() + kind.baseX + facing * kind.restX, HydraArena.groundY() + kind.restY);
    }

    /** Where the neck joins the head, and the way the head points. */
    Point2D neckEnd() {
        double k = kind.size * NECK_BACK;
        return pos.subtract(facing * k * Math.cos(pitch), k * Math.sin(pitch) * facing * facing);
    }

    Point2D neckDir() {
        return new Point2D(facing * Math.cos(pitch), Math.sin(pitch));
    }

    int facing() {
        return facing;
    }

    boolean isIdle() {
        return state == State.IDLE && !stats.isDead();
    }

    boolean isDying() {
        return state == State.DYING;
    }

    /** Phase 2 and the Crowned Head wind up faster. */
    void setTempo(double tempo) {
        this.tempo = tempo;
    }

    private Point2D mouth() {
        return pos.add(facing * kind.size * 1.1 * Math.cos(pitch), kind.size * (0.25 + 1.1 * Math.sin(pitch)));
    }

    //-----coming in-----
    /** Rises out of the tar where it hangs (the intro, the Crowned Head's entrance). */
    private void emerge() {
        state = State.EMERGE;
        Point2D r = rest();
        place(new Point2D(r.getX(), HydraArena.surface() + kind.size * 2));
        Particles.burst(new Point2D(r.getX(), HydraArena.surface()), HydraArena.TAR, 24, -90, 40, 120, 480, 0.9, 7, 900);
        Sfx.TAR_SPLASH.at(new Point2D(r.getX(), HydraArena.surface()), 1, 0.7);
    }

    /** Grows back out of its stump. */
    private void regrow() {
        state = State.REGROW;
        timer = 0;
        sprite.once("regrow", REGROW_FPS, null);
        Sfx.HYDRA_ROAR.at(pos, 0.6, 1.3);
    }

    //-----orders from the body-----
    void spitAt(Point2D at, double windup) {
        if (!isIdle()) return;
        next = Next.SPIT;
        target = at;
        startRear(windup);
    }

    void bite(Point2D at, double windup) {
        if (!isIdle()) return;
        next = Next.BITE;
        target = clampTarget(at);
        startRear(windup);
    }

    /** The tail slam: the wave strip is its own wind-up (the slam comes at {@link #WAVE_AT}). */
    void wave() {
        if (!isIdle()) return;
        next = Next.WAVE;
        startRear(0);
    }

    void sweep() {
        if (!isIdle()) return;
        state = State.SWEEP;
        timer = 0;
        double from = pos.getX() + facing * 80;
        if (spot != null && spot.isActive()) spot.removeFromWorld();
        spot = LureSpotComponent.spawn(from);
        Sfx.WATCHER_CHARGE.at(pos, 0.7, 0.7);
    }

    void hypnotise() {
        if (!isIdle()) return;
        state = State.HYPNO;
        timer = 0;
        hypnoStartHp = entity.getComponent(HealthDoubleComponent.class).getValue();
        sprite.setTelegraph(TELL);
        Sfx.WATCHER_CHARGE.at(pos, 1, 0.5);
    }

    void roar() {
        state = State.ROAR;
        timer = 0;
        sprite.once("roar", ROAR_FPS, null);
        Sfx.HYDRA_ROAR.at(pos, 1, 0.75);
        Impact.quake(18);
    }

    /** The bite or spit landed where the lure's spot was. */
    void clearSpot() {
        if (spot != null && spot.isActive()) spot.getComponent(LureSpotComponent.class).done();
        spot = null;
    }

    //-----the brain-----
    @Override
    protected void think(double tpf) {
        double t = dt(tpf);
        time += tpf;
        switch (state) {
            case EMERGE -> emerging(tpf);
            case REGROW -> {
                timer += t;
                pitch = -0.2;
                if (timer >= 10 / REGROW_FPS) back();
            }
            case IDLE -> idle(tpf);
            case REAR -> rear(tpf, t);
            case SPIT -> spit(t);
            case LUNGE -> lunge(tpf);
            case RECOVER -> {
                if ((timer -= t) <= 0) startPull(false);
            }
            case STUCK -> stuck(t);
            case PULL -> {
                if ((timer -= t) <= 0) back();
            }
            case RETURN -> returning(tpf);
            case SWEEP -> sweeping(tpf, t);
            case HYPNO -> hypno(tpf, t);
            case ROAR -> {
                timer += t;
                pitch = -0.5;
                if (Math.random() < t * 10) Impact.shake(4);
                if (timer >= ROAR) done();
            }
            case WAVE -> waving(t);
            case DYING -> { }
        }
    }

    @Override
    protected void whileDisabled(double tpf) {
        place(pos);                       // frozen or stunned where it is
    }

    @Override
    protected void interrupt() {
        sprite.setTelegraph(null);
        clearSpot();
        switch (state) {
            case REAR, SPIT, LUNGE, RECOVER, SWEEP, HYPNO, WAVE -> back();
            default -> { }
        }
    }

    private void emerging(double tpf) {
        sprite.loop("idle", IDLE_FPS);
        pitch = -0.3;
        if (moveTo(rest(), EMERGE_SPEED, tpf)) {
            Sfx.HYDRA_ROAR.at(pos, 0.9, kind == Kind.LURE ? 1.25 : kind == Kind.SPITTER ? 1.05 : 0.9);
            if (kind == Kind.CROWNED) roar();
            else done();
        }
        if (Math.random() < tpf * 20) {
            Particles.burst(pos.add(FXGLMath.random(-20, 20), kind.size * 0.6), HydraArena.TAR, 1, 90, 10, 20, 60, 0.6, 5, 700);
        }
    }

    private void idle(double tpf) {
        if (!sprite.isOneShotPlaying()) sprite.loop("idle", IDLE_FPS);
        int want = dirToPlayer();
        if (want != facing && Math.abs(dxToPlayer()) > 80) {
            facing = want;
            sprite.setFacing(facing);
        }
        pitch = 0.04 * Math.sin(time * 2);
        Point2D goal = rest().add(9 * Math.sin(time * 1.3 + kind.baseX), 7 * Math.cos(time * 0.9 + kind.baseX));
        follow(goal, FOLLOW, tpf);
    }

    //-----wind-up-----
    private void startRear(double windup) {
        state = State.REAR;
        timer = windup * tempo;
        fired = false;
        struck = false;
        facing = target != null ? (target.getX() >= pos.getX() ? 1 : -1) : dirToPlayer();
        sprite.setFacing(facing);
        // the Crowned Head rears up with its roar (it has no rear strip of its own)
        sprite.once(kind == Kind.CROWNED ? "roar" : kind == Kind.LURE ? "hypno" : "rear", (kind == Kind.CROWNED ? 5 : 6) / Math.max(0.2, timer), null);
        if (next == Next.WAVE) sprite.once("wave", WAVE_FPS, null);
        sprite.setTelegraph(TELL);
        Sfx.HYDRA_ROAR.at(pos, 0.4, next == Next.SPIT ? 1.4 : 1.1);
    }

    private void rear(double tpf, double t) {
        pitch = -0.45;
        if (next == Next.WAVE) {                         // the slam: the wave strip runs on from here
            state = State.WAVE;
            timer = 0;
            return;
        }
        follow(rest().add(-facing * 50, -50), 6, tpf);
        if ((timer -= t) > 0) return;
        sprite.setTelegraph(null);
        if (next == Next.SPIT) {
            state = State.SPIT;
            timer = 0;
            sprite.once("spit", SPIT_FPS, null);
        } else {
            state = State.LUNGE;
            sprite.once("bite", BITE_FPS, null);
            Sfx.HOUND_LUNGE.at(pos, 1, 0.5);
        }
    }

    //-----spit-----
    private void spit(double t) {
        timer += t;
        pitch = 0.05;
        if (!fired && timer >= SPIT_AT) {
            fired = true;
            int n = kind == Kind.CROWNED ? 3 : owner != null && owner.phase() >= 2 ? 2 : 1;
            Point2D m = mouth();
            for (int i = 0; i < n; i++) {
                double off = i == 0 ? 0 : (i % 2 == 1 ? 1 : -1) * ((i + 1) / 2) * SPIT_SPREAD * FXGLMath.random(0.8, 1.2);
                double x = clampX(target.getX() + off);
                double y = i == 0 ? target.getY() : HydraArena.floorAt(x, target.getY() - 200);
                // only the aimed glob leaves a burning pool; the others just splash
                TarGlobComponent.lob(m, new Point2D(x, y), SPIT_SPEED + FXGLMath.random(-60, 60), i == 0 ? POOL_LIFE : 0);
            }
            Sfx.TAR_SPIT.at(m);
            Impact.shake(6);
            Particles.burst(m, Particles.EMBER, 14, facing > 0 ? 0 : 180, 30, 80, 300, 0.4, 5, 200);
            if (owner != null) owner.strikeLanded();
        }
        if (timer >= SPIT_END) back();
    }

    //-----bite-----
    private void lunge(double tpf) {
        pitch = 0.35;
        if (touchBite()) return;
        if (moveTo(target, kind == Kind.CROWNED ? CROWNED_LUNGE : LUNGE_SPEED, tpf)) {
            Impact.shake(kind == Kind.CROWNED ? 14 : 10);
            Sfx.MIMIC_CHOMP.at(pos, 1, kind == Kind.CROWNED ? 0.5 : 0.65);
            if (owner != null) owner.strikeLanded();
            if (struck || snapHits()) {
                state = State.RECOVER;
                timer = RECOVER;
                return;
            }
            double[] s = HydraArena.surfaceUnder(mouth().getX(), mouth().getY() - 40);
            if (s != null && Math.abs(s[2] - mouth().getY()) < 70) {
                state = State.STUCK;                    // the teeth bit into the stone
                timer = kind == Kind.CROWNED ? CROWNED_STUCK : STUCK;
                sprite.loop("stuck", STUCK_FPS);
                Sfx.STONE_CRACK.at(mouth(), 1, 0.8);
                Impact.quake(kind == Kind.CROWNED ? 16 : 12);
                Particles.burst(new Point2D(mouth().getX(), s[2]), Particles.ROCK, 18, -90, 70, 100, 340, 0.8, 6, 900);
            } else {
                state = State.RECOVER;
                timer = RECOVER;
            }
        }
    }

    /** Running into the player on the way also bites. */
    private boolean touchBite() {
        if (struck || !entity.isColliding(player)) return false;
        struck = true;
        hurtPlayer(kind == Kind.CROWNED ? CROWNED_BITE : BITE_DAMAGE);
        Impact.shake(12);
        return false;
    }

    private boolean snapHits() {
        if (mouth().distance(player.getCenter()) > kind.size * 0.9 + 44) return false;
        struck = true;
        hurtPlayer(kind == Kind.CROWNED ? CROWNED_BITE : BITE_DAMAGE);
        return true;
    }

    private void stuck(double t) {
        pitch = 0.45;
        if (Math.random() < t * 6) {
            Particles.burst(mouth(), Particles.ROCK, 2, -90, 50, 40, 160, 0.6, 5, 900);
        }
        if (Math.random() < t * 1.5) Sfx.STONE_CRACK.at(mouth(), 0.4, 1.3);
        if ((timer -= t) <= 0) startPull(true);
    }

    private void startPull(boolean wasStuck) {
        state = State.PULL;
        timer = PULL;
        sprite.once("pull", PULL_FPS, null);
        if (wasStuck) {
            Sfx.STONE_CRACK.at(mouth(), 0.9, 1.0);
            Particles.burst(mouth(), Particles.ROCK, 12, -90, 60, 80, 260, 0.7, 6, 900);
        }
    }

    //-----lure-----
    private void sweeping(double tpf, double t) {
        sprite.loop("sweep", SWEEP_FPS);
        pitch = 0.08;
        follow(rest(), FOLLOW, tpf);
        timer += t;
        LureSpotComponent s = spot != null && spot.isActive() ? spot.getComponent(LureSpotComponent.class) : null;
        if (s == null) {
            back();
            return;
        }
        s.follow(player.getCenter().getX(), SWEEP_SPEED, tpf);
        if (timer >= SWEEP * tempo) {
            Point2D at = s.lock();
            Sfx.WATCHER_CHARGE.at(at, 0.6, 1.4);
            if (owner != null) owner.lureMarked(this, at);
            back();
        }
    }

    private void hypno(double tpf, double t) {
        sprite.loop("hypno", HYPNO_FPS);
        pitch = 0.12;
        follow(rest(), FOLLOW, tpf);
        timer += t;
        PlayerMovementComponent movement = player.getComponent(PlayerMovementComponent.class);
        double hp = entity.getComponent(HealthDoubleComponent.class).getValue();
        if (movement.isDodging() || hypnoStartHp - hp >= HYPNO_BREAK) {          // broken out of it
            Particles.burst(player.getCenter(), Particles.EMBER, 12, -90, 180, 60, 220, 0.5, 4, 200);
            Sfx.SHIELD_BREAK.at(pos, 0.6, 1.4);
            sprite.setTelegraph(null);
            back();
            return;
        }
        if (pos.distance(player.getCenter()) < HYPNO_RANGE) {
            double toward = HydraArena.lockX() + Kind.BITER.baseX - player.getCenter().getX();
            double step = Math.signum(toward) * Math.min(Math.abs(toward), HYPNO_PULL * tpf);
            if (movement.isGrounded()) {                 // dragged to the edge of their footing, not over it
                for (double[] s : HydraArena.surfaces()) {
                    if (player.getRightX() > s[0] && player.getX() < s[1] && Math.abs(player.getBottomY() - s[2]) < 8) {
                        double room = step > 0 ? s[1] - player.getRightX() : player.getX() - s[0];
                        step = Math.signum(step) * Math.max(0, Math.min(Math.abs(step), room));
                    }
                }
            }
            if (step != 0) movement.pull(step);
            if (Math.random() < tpf * 12) {
                Point2D p = player.getCenter();
                Particles.burst(p.add(FXGLMath.random(-20, 20), FXGLMath.random(-30, 10)), Particles.EMBER, 1,
                        Math.toDegrees(Math.atan2(pos.getY() - p.getY(), pos.getX() - p.getX())), 10, 80, 160, 0.5, 3, 0);
            }
        }
        if (timer >= HYPNO) {
            sprite.setTelegraph(null);
            if (owner != null) owner.hypnoDone();
            back();
        }
    }

    //-----crowned: the wave-----
    private void waving(double t) {
        timer += t;
        pitch = timer < WAVE_AT ? -0.55 : 0.6;
        if (!fired && timer >= WAVE_AT) {
            fired = true;
            sprite.setTelegraph(null);
            double x = clampX(pos.getX() + facing * kind.size * 1.6);
            TarWaveComponent.spawn(x, dirToPlayer());
            Impact.quake(20);
            Sfx.BRUTE_SLAM.at(new Point2D(x, HydraArena.surface()), 1, 0.6);
            Particles.burst(new Point2D(x, HydraArena.surface()), HydraArena.TAR, 30, -90, 50, 140, 520, 0.9, 7, 900);
            if (owner != null) owner.strikeLanded();
        }
        if (timer >= WAVE_END) back();
    }

    //-----back to rest-----
    private void back() {
        state = State.RETURN;
        sprite.setTelegraph(null);
    }

    private void returning(double tpf) {
        if (!sprite.isOneShotPlaying()) sprite.loop("idle", IDLE_FPS);
        pitch *= Math.max(0, 1 - tpf * 4);
        if (moveTo(rest(), RETURN_SPEED, tpf)) done();
    }

    private void done() {
        state = State.IDLE;
        sprite.setTelegraph(null);
        if (owner != null) owner.headReady(this);
    }

    //-----damage-----
    @Override
    public double guard(Hit h, Entity p) {
        return state == State.STUCK ? STUCK_GUARD : 1;
    }

    //-----death-----
    @Override
    public boolean diesSlowly() {
        return true;
    }

    @Override
    public void onDeath() {
        sprite.setTelegraph(null);
        clearSpot();
        state = State.DYING;
        timer = 0;
        fallVy = 0;
        sprite.once("death", kind == Kind.CROWNED ? DEATH_FPS * 0.7 : DEATH_FPS, null);
        Sfx.HYDRA_ROAR.at(pos, 0.9, kind == Kind.CROWNED ? 0.6 : 1.5);
        Impact.shake(14);
        Particles.burst(neckEnd(), Particles.BLOOD, 20, -90, 120, 100, 360, 0.7, 5, 700);
        if (owner != null) owner.headDied(this);
    }

    /** The Crowned Head's death is part of the body's death scene: it just keeps falling until it's told to go. */
    @Override
    protected void dying(double tpf) {
        timer += tpf;
        double hold = kind == Kind.CROWNED ? 1.6 : 0.55;
        if (timer < hold) return;
        fallVy += DEATH_FALL * tpf * (kind == Kind.CROWNED ? 0.35 : 1);
        place(pos.add(0, fallVy * tpf));
        if (pos.getY() > HydraArena.surface() + kind.size * 1.5) {
            Particles.burst(new Point2D(pos.getX(), HydraArena.surface()), HydraArena.TAR, 26, -90, 50, 120, 460, 0.9, 7, 900);
            Sfx.TAR_SPLASH.at(pos, 1, kind == Kind.CROWNED ? 0.5 : 0.8);
            entity.removeFromWorld();
        }
    }

    @Override
    public void onRemoved() {
        clearSpot();
        if (owner != null) owner.headRemoved(this);
    }

    //-----helpers-----
    private Point2D clampTarget(Point2D p) {
        Point2D b = base();
        double x = clampX(p.getX());
        double y = Math.min(p.getY(), HydraArena.surface() - kind.size * 0.5);
        Point2D at = new Point2D(x, y);
        Point2D d = at.subtract(b);
        if (d.magnitude() > REACH) at = b.add(d.normalize().multiply(REACH));
        return at;
    }

    private static double clampX(double x) {
        double lo = HydraArena.lockX() + BossGateComponent.WIDTH + 60, hi = HydraArena.lockX() + BossArena.WIDTH - BossGateComponent.WIDTH - 60;
        return Math.max(lo, Math.min(hi, x));
    }

    /** Eases toward p (k per second). */
    private void follow(Point2D p, double k, double tpf) {
        place(pos.add(p.subtract(pos).multiply(Math.min(1, k * tpf))));
    }

    /** Moves toward p at speed. @return true once there */
    private boolean moveTo(Point2D p, double speed, double tpf) {
        Point2D d = p.subtract(pos);
        double step = speed * tpf;
        if (d.magnitude() <= step) {
            place(p);
            return true;
        }
        place(pos.add(d.normalize().multiply(step)));
        return false;
    }

    void place(Point2D centre) {
        pos = centre;
        physics.setVelocityX(0);
        physics.setVelocityY(0);
        physics.overwritePosition(centre.subtract(entity.getWidth() / 2, entity.getHeight() / 2));
    }

    Point2D position() {
        return pos;
    }
}
