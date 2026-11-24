package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Combat.DamageKind;
import at.htl.flowstate.Combat.Hit;
import at.htl.flowstate.Components.Player.PlayerStatsComponent;
import at.htl.flowstate.Components.Enemies.HydraHeadBehaviour.Kind;
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
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.almasb.fxgl.dsl.FXGL.runOnce;
import static com.almasb.fxgl.dsl.FXGL.spawn;

/**
 * The Tar Hydra, the third boss: one body sunk in the tar of its arena (HydraArena) with three heads on long necks,
 * a Spitter, a Biter and a Lure ({@link HydraHeadBehaviour}). This is the body: out of reach, it carries the boss bar
 * (the heads' health added up), strings the necks ({@link HydraNeck}) and decides who attacks.
 * <p>
 * Cut a head off and its neck is left as a smoking stump ({@link HydraStumpBehaviour}): seal it with fire, ice, poison
 * or a heavy blow within a few seconds or the head grows back. The heads share one blood: a burn, poison or bleed on
 * one ticks on all of them.
 * <p>
 * Phase 1: one head attacks at a time. The lure's light marks where the spitter or the biter strikes next.
 * Phase 2 (a head is off): faster wind-ups, two heads at once, the lure hypnotises.
 * Phase 3 (all three stumps sealed): the tar rises over the ledges and the Crowned Head comes up out of the body;
 * it spits, bites and slams the tar into waves. When it dies, the Hydra dies.
 */
public class HydraBehaviour extends DemonBehaviour {
    /** Arena x of the body's centre. */
    static final double CENTRE_X = 820;
    static final double BODY_BELOW = 300;                  // the body's (2x2) hitbox under the floor: nothing reaches it
    // the intro: when each head comes up (s after the body appears), then when the fight may start
    private static final double LURE_AT = 0.2, SPITTER_AT = 1.0, BITER_AT = 1.8, READY_AFTER = 3.2;
    // the cutscene into the last phase (s after the last stump is sealed): the stumps sink, the Crowned Head comes
    // up, and control is back as its roar ends (BossArena gives it back 1.3 s after landed)
    private static final double STUMPS_GONE = 2.0, CROWNED_AFTER = 2.6, CROWNED_LANDS = 1.0;
    private static final double TEMPO_2 = 0.8, TEMPO_CROWNED = 1.0;
    // the Crowned Head alone: long wind-ups and long breaks, so every strike can be dodged
    private static final double CROWNED_SPIT_WINDUP = 0.85, CROWNED_BITE_WINDUP = 1.0, CROWNED_GAP_MIN = 1.9, CROWNED_GAP_MAX = 2.7;
    private static final double MARKED_WINDUP = 0.35, HYPNO_BITE_WINDUP = 0.3;
    private static final int REWARD_SP = 3;
    // death scene (s)
    private static final double D_SINK = 0.8, D_BURST = 3.4;

    private static Entity current;

    private final Map<Kind, HydraHeadBehaviour> heads = new EnumMap<>(Kind.class);
    private final Map<Kind, HydraStumpBehaviour> stumps = new EnumMap<>(Kind.class);
    private final Map<Kind, HydraNeck> necks = new EnumMap<>(Kind.class);
    private final Map<Kind, Integer> lastFacing = new EnumMap<>(Kind.class);
    private final Set<Kind> sealed = EnumSet.noneOf(Kind.class);
    private final Set<Kind> spawned = EnumSet.noneOf(Kind.class);
    private double hpScale = 1;
    private double time, cooldown = 1.0, finaleAt = -1, stumpsGoneAt = -1, landAt = -1;
    private int phase = 1;
    private boolean ready, crownedSpawned, crownedDead;
    private Kind lastAttacker;
    private HydraHeadBehaviour pendingSpot;                  // the lure whose spot the next strike aims at
    private boolean dying;
    private double deathT;
    private boolean sunk;

    /** The living Hydra, if any (the HUD shows its health bar). */
    public static Entity current() {
        return current != null && current.isActive() ? current : null;
    }

    @Override
    protected void onReady() {
        current = entity;
        entity.setZIndex(-20);                                   // behind every neck
        HealthDoubleComponent hp = entity.getComponent(HealthDoubleComponent.class);
        hpScale = hp.getMaxValue() / DemonType.HYDRA.hp;
        physics.overwritePosition(new Point2D(HydraArena.lockX() + CENTRE_X - entity.getWidth() / 2,
                HydraArena.groundY() + BODY_BELOW - entity.getHeight() / 2));
        for (Kind k : Kind.values()) necks.put(k, new HydraNeck(k.baseRadius, k.neckRadius, k.layer - 1));
        for (HydraNeck n : necks.values()) n.hide();
        Sfx.TAR_WAVE.at(entity.getCenter(), 1, 0.5);
    }

    int phase() {
        return phase;
    }

    //-----every frame-----
    @Override
    protected void think(double tpf) {
        time += tpf;
        physics.setVelocityX(0);
        physics.setVelocityY(0);
        intro();
        updateHealth();
        sharedBlood(tpf);
        updateNecks();
        finale();
        if (ready && !BossArena.inCutscene()) conduct(dt(tpf));
    }

    @Override
    protected void whileDisabled(double tpf) {
        updateNecks();
    }

    private void intro() {
        if (!spawned.contains(Kind.LURE) && time >= LURE_AT) spawnHead(Kind.LURE, false, null, 1);
        if (!spawned.contains(Kind.SPITTER) && time >= SPITTER_AT) spawnHead(Kind.SPITTER, false, null, 1);
        if (!spawned.contains(Kind.BITER) && time >= BITER_AT) spawnHead(Kind.BITER, false, null, 1);
        if (!ready && time >= READY_AFTER) {
            ready = true;
            BossArena.landed(entity);
        }
    }

    /** The boss bar: every head's health added up, the Crowned Head's full health until it comes. */
    private void updateHealth() {
        if (dying) return;
        double sum = 0;
        for (Kind k : new Kind[]{Kind.SPITTER, Kind.BITER, Kind.LURE}) {
            HydraHeadBehaviour h = heads.get(k);
            if (h != null && !h.isDying()) sum += h.getEntity().getComponent(HealthDoubleComponent.class).getValue();
            else if (!spawned.contains(k)) sum += k.type.hp * hpScale;
        }
        HydraHeadBehaviour crowned = heads.get(Kind.CROWNED);
        if (!crownedSpawned) sum += Kind.CROWNED.type.hp * hpScale;
        else if (crowned != null && !crowned.isDying()) sum += crowned.getEntity().getComponent(HealthDoubleComponent.class).getValue();
        HealthDoubleComponent hp = entity.getComponent(HealthDoubleComponent.class);
        if (crownedDead) {
            stats.damage(hp.getValue() + 1, DamageKind.OTHER);     // the last head is gone: so is the Hydra
            return;
        }
        hp.setValue(Math.max(1, Math.min(hp.getMaxValue(), sum)));
    }

    /** One blood: what burns, poisons or bleeds one head ticks on every other head too. */
    private void sharedBlood(double tpf) {
        List<HydraHeadBehaviour> alive = new ArrayList<>();
        for (HydraHeadBehaviour h : heads.values()) if (!h.isDying()) alive.add(h);
        for (HydraHeadBehaviour from : alive) {
            double dps = from.getEntity().getComponent(EnemyStatsComponent.class).getDotPerSecond();
            if (dps <= 0) continue;
            for (HydraHeadBehaviour to : alive) {
                if (to == from) continue;
                EnemyStatsComponent s = to.getEntity().getComponent(EnemyStatsComponent.class);
                s.damage(dps * tpf, DamageKind.DOT);
                if (Math.random() < tpf * 4) {
                    Particles.burst(to.position(), Particles.BLOOD, 1, 90, 20, 10, 40, 0.5, 4, 400);
                }
            }
        }
    }

    private void updateNecks() {
        for (Kind k : Kind.values()) {
            HydraNeck neck = necks.get(k);
            if (neck == null) continue;
            HydraHeadBehaviour h = heads.get(k);
            HydraStumpBehaviour s = stumps.get(k);
            Point2D base = new Point2D(HydraArena.lockX() + k.baseX, HydraArena.surface() + 90);
            if (s != null && s.getEntity().isActive()) {
                neck.update(base, s.neckEnd(), new Point2D(0, -1), lastFacing.getOrDefault(k, 1));
            } else if (h != null && h.getEntity().isActive() && h.position() != null && (!h.isDying() || k == Kind.CROWNED)) {
                // a cut head drops off its neck; the Crowned Head dies on it
                lastFacing.put(k, h.facing());
                neck.update(base, h.neckEnd(), h.neckDir(), h.facing());
            } else {
                neck.hide();
            }
        }
    }

    //-----who attacks-----
    private void conduct(double t) {
        if (dying) return;
        int busy = 0;
        for (HydraHeadBehaviour h : heads.values()) if (!h.isIdle() && !h.isDying()) busy++;
        int allowed = phase == 1 || phase == 3 ? 1 : 2;
        cooldown -= t;
        if (cooldown > 0 || busy >= allowed) return;

        HydraHeadBehaviour spitter = idle(Kind.SPITTER), biter = idle(Kind.BITER), lure = idle(Kind.LURE), crowned = idle(Kind.CROWNED);
        Point2D feet = new Point2D(player.getCenter().getX(), player.getBottomY());
        double[] w = new double[6];                    // spit, bite, sweep, hypno, crowned spit, crowned bite/wave
        if (spitter != null) w[0] = 1.4;
        if (biter != null) w[1] = 2.0;
        if (lure != null && (spitter != null || biter != null)) w[2] = 1.8;
        if (lure != null && biter != null && phase >= 2) w[3] = 1.3;
        if (crowned != null) {
            w[4] = 1.3;
            w[5] = 3.8;
        }
        if (lastAttacker != null) {
            if (lastAttacker == Kind.SPITTER) w[0] *= 0.35;
            if (lastAttacker == Kind.BITER) w[1] *= 0.35;
            if (lastAttacker == Kind.LURE) w[2] *= 0.35;
        }
        double total = 0;
        for (double v : w) total += v;
        if (total <= 0) return;
        double roll = Math.random() * total;
        int pick = 0;
        while (pick < w.length - 1 && (roll -= w[pick]) > 0) pick++;
        if (w[pick] <= 0) return;
        switch (pick) {
            case 0 -> {
                spitter.spitAt(feet, 0.6);
                lastAttacker = Kind.SPITTER;
            }
            case 1 -> {
                biter.bite(player.getCenter(), 0.65);
                lastAttacker = Kind.BITER;
            }
            case 2 -> {
                lure.sweep();
                lastAttacker = Kind.LURE;
            }
            case 3 -> {
                lure.hypnotise();
                lastAttacker = Kind.LURE;
            }
            case 4 -> crowned.spitAt(feet, CROWNED_SPIT_WINDUP);
            default -> {
                if (Math.random() < 0.42) crowned.wave();
                else crowned.bite(player.getCenter(), CROWNED_BITE_WINDUP);
            }
        }
        cooldown = phase == 3 ? FXGLMath.random(CROWNED_GAP_MIN, CROWNED_GAP_MAX) : FXGLMath.random(1.3, 2.0) * tempo();
    }

    private HydraHeadBehaviour idle(Kind k) {
        HydraHeadBehaviour h = heads.get(k);
        return h != null && h.getEntity().isActive() && h.isIdle() ? h : null;
    }

    private double tempo() {
        return phase == 3 ? TEMPO_CROWNED : phase == 2 ? TEMPO_2 : 1;
    }

    /** The lure's light locked onto a spot: the spitter (or else the biter) strikes there. */
    void lureMarked(HydraHeadBehaviour lure, Point2D at) {
        HydraHeadBehaviour spitter = idle(Kind.SPITTER), biter = idle(Kind.BITER);
        if (spitter != null && (biter == null || Math.random() < 0.4)) spitter.spitAt(at, MARKED_WINDUP);
        else if (biter != null) biter.bite(at.subtract(0, Kind.BITER.size * 0.4), MARKED_WINDUP);
        else lure.clearSpot();
        pendingSpot = lure;
    }

    /** A spit or a bite landed: the lure's spot has done its job. */
    void strikeLanded() {
        if (pendingSpot != null) pendingSpot.clearSpot();
        pendingSpot = null;
    }

    /** The lure has drawn the player in: the biter snaps. */
    void hypnoDone() {
        HydraHeadBehaviour biter = idle(Kind.BITER);
        if (biter != null) biter.bite(player.getCenter(), HYPNO_BITE_WINDUP);
    }

    void headReady(HydraHeadBehaviour h) {
        // nothing to do yet: the conductor sees it idle
    }

    //-----heads coming and going-----
    private void spawnHead(Kind k, boolean regrown, Point2D at, double hpShare) {
        spawned.add(k);
        Point2D c = at != null ? at : new Point2D(HydraArena.lockX() + k.baseX, HydraArena.surface() + 200);
        int facing = player.getCenter().getX() >= HydraArena.lockX() + k.baseX ? 1 : -1;
        Entity e = spawn(k.type.spawnName(), new SpawnData(c.getX() - k.type.width / 2, c.getY() - k.type.height / 2)
                .put("hpScale", hpScale * hpShare));
        HydraHeadBehaviour h = (HydraHeadBehaviour) DemonBehaviour.of(e);
        if (h == null) return;
        h.setOwner(this, regrown, facing);
        h.setTempo(tempo());
        heads.put(k, h);
    }

    /** A head was cut off: its neck is left as a smoking stump. */
    void headDied(HydraHeadBehaviour h) {
        if (h.kind == Kind.CROWNED) {
            crownedDead = true;
            return;
        }
        if (phase == 1) {
            phase = 2;
            for (HydraHeadBehaviour o : heads.values()) o.setTempo(TEMPO_2);
            Sfx.HYDRA_ROAR.at(entity.getCenter(), 0.8, 0.8);
        }
        Point2D end = h.neckEnd();
        double r = h.kind.neckRadius;
        DemonType t = h.kind.stump;
        Point2D centre = end.subtract(0, r * HydraStumpBehaviour.NECK_BELOW);
        Entity e = spawn(t.spawnName(), new SpawnData(centre.getX() - t.width / 2, centre.getY() - t.height / 2));
        HydraStumpBehaviour s = (HydraStumpBehaviour) DemonBehaviour.of(e);
        if (s != null) {
            s.setOwner(this, h.facing());
            stumps.put(h.kind, s);
        }
        heads.remove(h.kind);
        lastFacing.put(h.kind, h.facing());
    }

    void headRemoved(HydraHeadBehaviour h) {
        if (heads.get(h.kind) == h) heads.remove(h.kind);
    }

    /** Not sealed in time: the head grows back out of the stump, at half health. */
    void regrow(HydraStumpBehaviour s) {
        stumps.remove(s.kind);
        int facing = lastFacing.getOrDefault(s.kind, 1);
        Point2D at = s.neckEnd().add(facing * s.kind.size * HydraHeadBehaviour.NECK_BACK, 0);     // its neck joins the stump's
        s.getEntity().removeFromWorld();
        spawnHead(s.kind, true, at, 0.5);
    }

    void stumpSealed(HydraStumpBehaviour s) {
        sealed.add(s.kind);
        if (sealed.size() == 3 && finaleAt < 0) startFinale();
    }

    void stumpRemoved(HydraStumpBehaviour s) {
        if (stumps.get(s.kind) == s) stumps.remove(s.kind);
    }

    //-----the last phase-----
    /** The three sealed necks sink into the tar, the tar rises, and the Crowned Head comes up: a short cutscene. */
    private void startFinale() {
        phase = 3;
        BossArena.midFightCutscene(entity);
        for (HydraStumpBehaviour s : stumps.values()) {
            s.sink();
            Particles.burst(s.getEntity().getCenter(), HydraArena.TAR, 12, -90, 60, 80, 300, 0.8, 6, 800);
        }
        Sfx.TAR_SPLASH.at(entity.getCenter().subtract(0, BODY_BELOW), 1, 0.6);
        stumpsGoneAt = time + STUMPS_GONE;
        finaleAt = time + CROWNED_AFTER;
        cooldown = 99;                                       // until control is back
        HydraArena.rise();
        Impact.quake(22);
        Sfx.HYDRA_ROAR.at(entity.getCenter(), 1, 0.5);
        Particles.burst(entity.getCenter().subtract(0, BODY_BELOW), HydraArena.TAR, 40, -90, 60, 140, 520, 1.0, 7, 900);
    }

    private void finale() {
        if (stumpsGoneAt >= 0 && time >= stumpsGoneAt) {    // under the tar: gone for good
            stumpsGoneAt = -1;
            for (HydraStumpBehaviour s : new ArrayList<>(stumps.values())) if (s.getEntity().isActive()) s.getEntity().removeFromWorld();
            stumps.clear();
        }
        if (finaleAt >= 0 && !crownedSpawned && time >= finaleAt) spawnCrowned();
        if (landAt >= 0 && time >= landAt) {                // up and roaring: the fight goes on
            landAt = -1;
            cooldown = 1.2;
            BossArena.landed(entity);
        }
    }

    private void spawnCrowned() {
        crownedSpawned = true;
        landAt = time + CROWNED_LANDS;
        spawnHead(Kind.CROWNED, false, null, 1);
    }

    //-----damage-----
    /** Sunk in the tar: only the heads can be hurt. */
    @Override
    public double guard(Hit h, Entity p) {
        return 0;
    }

    @Override
    public boolean isLurking() {
        return true;
    }

    //-----death-----
    @Override
    public boolean diesSlowly() {
        return true;
    }

    @Override
    public void onDeath() {
        if (current == entity) current = null;
        dying = true;
        deathT = 0;
        Sfx.HYDRA_ROAR.at(entity.getCenter(), 1, 0.55);
        Impact.quake(20);
        BossArena.bossDying(entity);
    }

    @Override
    protected void dying(double tpf) {
        deathT += tpf;
        updateNecks();
        if (!sunk && deathT >= D_SINK) {                    // the stumps slide back into the tar
            sunk = true;
            for (HydraStumpBehaviour s : stumps.values()) s.sink();
        }
        if (deathT >= D_SINK && Math.random() < tpf * 10) {
            double x = HydraArena.lockX() + HydraArena.LAKE_FROM + Math.random() * (HydraArena.LAKE_TO - HydraArena.LAKE_FROM);
            Particles.burst(new Point2D(x, HydraArena.surface()), HydraArena.TAR, 3, -90, 40, 80, 300, 0.7, 6, 800);
        }
        if (deathT >= D_BURST) burst();
    }

    /** It's over: the heads and necks go, the tar drains, the arena opens. */
    private void burst() {
        Point2D c = new Point2D(entity.getCenter().getX(), HydraArena.surface());
        Sfx.EXPLOSION.at(c, 1, 0.5);
        Sfx.TAR_WAVE.at(c, 1, 0.7);
        Impact.shake(28);
        Particles.burst(c, HydraArena.TAR, 80, -90, 70, 150, 800, 1.3, 8, 900);
        Particles.burst(c, Particles.EMBER, 40, -90, 80, 150, 600, 1.0, 5, -100);
        PlayerStatsComponent ps = player.getComponent(PlayerStatsComponent.class);
        for (int i = 0; i < REWARD_SP; i++) ps.addSkillPoint();
        runOnce(Sfx.LEVEL_UP::play, Duration.seconds(0.9));
        clearParts();
        BossArena.bossExploded();
        entity.removeFromWorld();
    }

    private void clearParts() {
        for (HydraHeadBehaviour h : new ArrayList<>(heads.values())) if (h.getEntity().isActive()) h.getEntity().removeFromWorld();
        for (HydraStumpBehaviour s : new ArrayList<>(stumps.values())) if (s.getEntity().isActive()) s.getEntity().removeFromWorld();
        heads.clear();
        stumps.clear();
        for (HydraNeck n : necks.values()) n.remove();
        necks.clear();
    }

    @Override
    public void onRemoved() {
        if (!dying) BossArena.end();
        if (current == entity) current = null;
        clearParts();
    }
}
