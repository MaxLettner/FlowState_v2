package at.htl.flowstate.Components.Enemies.Bosses.Hollow;

import at.htl.flowstate.Assets;
import at.htl.flowstate.Combat.Combat;
import at.htl.flowstate.Combat.DamageKind;
import at.htl.flowstate.Combat.Element;
import at.htl.flowstate.Combat.Hit;
import at.htl.flowstate.Components.Enemies.Ballistics;
import at.htl.flowstate.Components.Enemies.Bosses.BossArena;
import at.htl.flowstate.Components.Enemies.GroundDemonBehaviour;
import at.htl.flowstate.Components.Enemies.ShockwaveComponent;
import at.htl.flowstate.Components.Player.Abilities.SkillMove;
import at.htl.flowstate.Components.Player.LoadoutComponent;
import at.htl.flowstate.Components.Player.PlayerStatsComponent;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Skills.SkillManager;
import at.htl.flowstate.Skills.SkillType;
import at.htl.flowstate.Visuals.FrameSprite;
import at.htl.flowstate.Visuals.Frames;
import at.htl.flowstate.Visuals.Impact;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.core.math.FXGLMath;
import com.almasb.fxgl.dsl.components.HealthDoubleComponent;
import com.almasb.fxgl.entity.Entity;
import javafx.geometry.Point2D;
import javafx.scene.effect.BlendMode;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;

import java.util.ArrayList;
import java.util.List;


/**
 * The Hollow Ember (fourth boss): the player's void double. It carries void copies of whatever is on the player's
 * hotbar when the fight starts (every usable skill is a {@link SkillMove}: swings, slams, punches, projectiles, a
 * mana shield, a levitation hop; enchantments buff its hits) and moves like the player: runs, dashes, jumps, pogos
 * off the player's head. Phase 1 uses one skill and swaps every {@link #SWAP_EVERY} attacks, phase 2 chains two in a
 * combo, phase 3 chains them all and sends echoes of the player's own past at them.
 * <p>
 * Every hit it lands steals an ember shard (it orbits it, +8 % damage each); hits on it knock shards loose, and
 * a loose shard heals the player. After each big attack it recoils and the hollow in its chest flares: hits do
 * +50 % and a fire hit staggers it.
 */
public class HollowBehaviour extends GroundDemonBehaviour {
    /** Where it climbs out of the glass, from the arena's left edge (world x of its centre). */
    public static final double RISE_X = 560;

    private static final double RISE_TIME = 1.5, ROAR_AT = 1.6, ROAR_END = 2.7;
    private static final double RUN_SPEED = 330, WALK_SPEED = 210, DASH_SPEED = 820, DASH_TIME = 0.22;
    private static final int MAX_SHARDS = 5;
    private static final double SHARD_BONUS = 0.08, LOOSE_COOLDOWN = 0.35;
    private static final double FLARE_TIME = 0.8, FLARE_MULT = 1.5, FLARE_STUN = 1.0;
    private static final int SWAP_EVERY = 3;
    private static final double WARD = 120;
    private static final double ECHO_FIRST = 2.0, ECHO_MIN = 8, ECHO_MAX = 11, ECHO_AGE = 3.6, ECHO_STEP = 1.0;
    private static final double SIZE = 1.5;
    private static final double SHARDS_BACK = 0.8, DEATH_SEEP = 2.6, DEATH_END = 4.4;
    private static final int REWARD_SP = 3;
    private static final double STEAL_HEAL = 0.025;      // life steal: of its max health per hit
    private static final double SHARPEN = 1.15, PIERCE_BOOST = 1.25;
    private static final Color TELL = Color.rgb(190, 120, 255, 0.9);
    private static final Color[] EMBERS_OUT = {Color.web("#ffc23d"), Color.web("#ff7a1f"), Color.web("#d1361a")};

    private enum State { RISE, ROAR, MOVE, ATTACK, RECOIL, DASH, POGO, HOVER, DYING }

    private final BossArena arena = BossArena.active();


    private final List<SkillMove> kit = new ArrayList<>();
    private final List<SkillMove> combo = new ArrayList<>();
    private final HollowRecorder recorder = new HollowRecorder();
    private final List<FrameSprite> shardViews = new ArrayList<>();
    private Circle flareNode;
    private double sharpen = 1;
    private boolean lifeSteal, piercing;
    private Element imbue;

    private State state = State.RISE;
    private double timer, standY, cooldown = 0.6, moveTimer, dashCooldown, pogoCooldown = 4;
    private int phase = 1, cur, attacksSinceSwap, step, fired;
    private SkillMove move;
    private int shards;
    private double looseCooldown, flare, orbit, echoTimer = ECHO_FIRST, vyBefore, landTimer;
    private double stride, hoverTimer, hoverStartY;
    private int hoverStage, pogoStage, hoverDir = 1, originalZ;
    private boolean dying, bounced;
    private double deathT;
    private int deathCue;

    @Override
    protected void onReady() {
        snapshot();
        stats.setInvulnerable(true);
        standY = entity.getY();
        originalZ = entity.getZIndex();
        entity.setZIndex(9);               // under the glass floor while it climbs out
        for (int i = 0; i < MAX_SHARDS; i++) {
            Frames f = Assets.frames("HollowShard.png", 4, 2);
            FrameSprite s = new FrameSprite(f).loop(f, 8);
            s.setVisible(false);
            shardViews.add(s);
            entity.getViewComponent().addChild(s);
        }
        flareNode = new Circle(18);
        flareNode.setFill(new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.WHITE), new Stop(0.4, Color.web("#d8b4ff")), new Stop(1, Color.web("#a86cff", 0))));
        flareNode.setBlendMode(BlendMode.ADD);
        flareNode.setTranslateX(entity.getWidth() / 2);
        flareNode.setTranslateY(84);
        flareNode.setOpacity(0);
        entity.getViewComponent().addChild(flareNode);
    }

    /** Copies the hotbar as it is now: attacks into the kit, enchantments into buffs. */
    private void snapshot() {
        SkillManager skills = SkillManager.get();
        LoadoutComponent loadout = player.getComponent(LoadoutComponent.class);
        for (int slot = 0; slot < SkillManager.HOTBAR_SIZE; slot++) {
            SkillType type = skills.getSlot(slot);
            SkillMove m = type == null ? null : SkillMove.of(type);
            if (m == null) continue;
            if (m.kind() != SkillMove.Kind.ENCHANT) {
                kit.add(m);
                continue;
            }
            switch (type) {
                case ENCHANTING -> sharpen = SHARPEN;
                case IMBUE -> imbue = loadout.getImbueElement() != null ? loadout.getImbueElement()
                        : loadout.ownedElements().isEmpty() ? null : loadout.ownedElements().get(0);
                case LIFE_STEAL -> lifeSteal = true;
                default -> piercing = true;
            }
        }
        if (kit.isEmpty()) kit.add(SkillMove.of(SkillType.RUSTY_SWORD));
    }

    //-----what it can do-----
    @Override
    protected void act(double tpf) {
        double t = dt(tpf);
        recorder.record(player);
        orbitShards(tpf);
        looseCooldown -= tpf;
        flare = Math.max(0, flare - tpf);
        cooldown -= t;
        dashCooldown -= t;
        pogoCooldown -= t;
        phaseCheck();
        if (phase == 3 && state != State.DYING && !arena.inCutscene()) echoes(t);

        switch (state) {
            case RISE -> rise(tpf);
            case ROAR -> roar(tpf);
            case MOVE -> moveState(t);
            case ATTACK -> attack(t);
            case RECOIL -> recoil(t);
            case DASH -> dash(t);
            case POGO -> pogo(t);
            case HOVER -> hover(t);
            case DYING -> { }
        }
        updateFlare();
    }

    //-----intro-----
    private void rise(double tpf) {
        timer += tpf;
        double k = Math.min(1, timer / RISE_TIME);
        double ease = 1 - (1 - k) * (1 - k);
        physics.overwritePosition(new Point2D(entity.getX(), standY + entity.getHeight() * (1 - ease)));
        physics.setVelocityX(0);
        physics.setVelocityY(0);
        sprite.loop("idle", 8);
        if (timer == tpf) {
            Sfx.HOLLOW_RISE.at(entity.getCenter(), 1, 1);
            Impact.shake(8);
        }
        if (FXGLMath.randomDouble() < tpf * 18) {
            Particles.of(Particles.VOID).count(2).angle(-90, 50).speed(40, 160)
                    .life(0.6).size(4).gravity(200).at(new Point2D(entity.getCenter().getX() + FXGLMath.random(-30, 30), standY + entity.getHeight()));
        }
        if (timer >= RISE_TIME) {
            entity.setZIndex(originalZ);
            physics.overwritePosition(new Point2D(entity.getX(), standY));
            state = State.ROAR;
            timer = 0;
            Impact.shake(10);
        }
    }

    private void roar(double tpf) {
        timer += tpf;
        stop();
        face(dirToPlayer());
        double k = Math.max(0, timer - 0.1);
        sprite.show("charge", Math.min(5, (int) (k * 7)));
        if (fired == 0 && timer >= 0.1) {          // its embers go out and turn violet; the hollow opens, the crown lights
            fired = 1;
            Particles.of(EMBERS_OUT).count(18).angle(-90, 90).speed(60, 240).life(0.8).size(5).gravity(-60).at(entity.getCenter());
        }
        if (fired == 1 && timer >= ROAR_AT - 1.5 + 0.6) {
            fired = 2;
            Sfx.HOLLOW_ROAR.at(entity.getCenter(), 1, 1);
            Impact.quake(14);
            Particles.of(Particles.VOID).count(40).angle(-90, 180).speed(120, 520)
                    .life(0.9).size(6).gravity(100).at(entity.getCenter());
            flare = Math.max(flare, 1.2);
            arena.landed(entity);
        }
        if (timer >= ROAR_END - ROAR_AT) {
            stats.setInvulnerable(false);
            arena.hollow().introDone();
            state = State.MOVE;
            timer = 0;
            fired = 0;
            cooldown = 0.8;
        }
    }

    //-----phases and the kit-----
    private double hpFraction() {
        return entity.getComponent(HealthDoubleComponent.class).getValuePercent() / 100.0;
    }

    private void phaseCheck() {
        double f = hpFraction();
        int now = f > 0.66 ? 1 : f > 0.33 ? 2 : 3;
        if (now <= phase || dying) return;
        phase = now;
        combo.clear();
        Sfx.HOLLOW_SWAP.at(entity.getCenter(), 1, 0.6);
        Impact.shake(10);
        Particles.of(Particles.VOID).count(30).angle(-90, 180).speed(100, 400)
                .life(0.7).size(6).gravity(100).at(entity.getCenter());
        if (phase == 3) echoTimer = ECHO_FIRST;
    }

    /** The moves of the next combo: one (phase 1), two (phase 2), up to three (phase 3), starting at the current skill. */
    private void planCombo() {
        combo.clear();
        int n = kit.size();
        int length = phase == 1 ? 1 : phase == 2 ? 2 : Math.max(2, Math.min(3, n));
        for (int i = 0; i < length; i++) combo.add(kit.get((cur + i) % n));
        step = 0;
    }

    private void comboDone() {
        int n = kit.size();
        if (phase == 1) {
            if (++attacksSinceSwap >= SWAP_EVERY) {
                attacksSinceSwap = 0;
                cur = (cur + 1) % n;
                if (n > 1) swapCue(kit.get(cur));
            }
        } else {
            cur = (cur + 1) % n;
        }
        combo.clear();
    }

    /** The swap is shown: a void copy of the skill's hotbar icon flashes over its head. */
    private void swapCue(SkillMove m) {
        Sfx.HOLLOW_SWAP.at(entity.getCenter());
        HollowFx.icon(m.skill(), new Point2D(entity.getCenter().getX(), entity.getY() - 6));
        Particles.of(Particles.VOID).count(10).angle(-90, 80).speed(60, 200)
                .life(0.5).size(4).gravity(0).at(new Point2D(entity.getCenter().getX(), entity.getY()));
    }

    //-----moving-----
    private void moveState(double t) {
        locomote(t);
        if (arena.inCutscene()) {
            stop();
            return;
        }
        int toward = dirToPlayer();
        face(toward);
        double adx = Math.abs(dxToPlayer());
        moveTimer += t;
        if (combo.isEmpty()) planCombo();
        SkillMove next = combo.get(step);
        boolean melee = next.kind() == SkillMove.Kind.SWING || next.kind() == SkillMove.Kind.SLAM || next.kind() == SkillMove.Kind.FISTS;
        double reach = melee ? reachOf(next) : 0;
        boolean ranged = next.kind() == SkillMove.Kind.SHOT;

        if (cooldown > 0) {            // between attacks: keep a sensible distance
            if (ranged && adx < 300 && arena.wallGap(entity.getCenter().getX(), -toward) > 150) walk(-toward, WALK_SPEED);
            else if (adx > (melee ? reach + 80 : 560)) walk(toward, WALK_SPEED);
            else stop();
            return;
        }
        if (grounded && pogoCooldown <= 0 && adx > 200 && adx < 560 && player.getY() + player.getHeight() >= entity.getBottomY() - 40
                && FXGLMath.randomDouble() < 0.02) {
            startPogo();
            return;
        }
        boolean inRange = melee ? adx <= reach + 30 : ranged ? adx >= 260 && adx <= 800 : true;
        if (inRange || moveTimer > 3) {
            startStep();
            return;
        }
        double want = melee ? reach : 520;
        if (adx > want + 520 && grounded && dashCooldown <= 0) {
            state = State.DASH;
            timer = 0;
            dashCooldown = 3;
            return;
        }
        if (ranged && adx < 260 && arena.wallGap(entity.getCenter().getX(), -toward) > 150) walk(-toward, RUN_SPEED);
        else walk(toward, adx > want + 250 ? RUN_SPEED : WALK_SPEED);
    }

    private double reachOf(SkillMove m) {
        double r = m.reach() * SIZE * (piercing ? PIERCE_BOOST : 1);
        return m.kind() == SkillMove.Kind.FISTS ? r + 20 : r;
    }

    private void dash(double t) {
        timer += t;
        face(dirToPlayer());
        physics.setVelocityX(facing * DASH_SPEED);
        sprite.loop("dash", 30);
        if (timer >= DASH_TIME) {
            stop();
            state = State.MOVE;
        }
    }

    /** Idle / walk / run / air from the real movement, picking frames out of the player-style grids. */
    private void locomote(double tpf) {
        double vx = physics.getVelocityX(), vy = physics.getVelocityY();
        if (justLanded() && vyBefore > 300) landTimer = 0.25;
        vyBefore = vy;
        landTimer = Math.max(0, landTimer - tpf);
        if (!grounded) {
            int row = nearest(new double[]{-620, -400, -180, 0, 180, 400, 620}, vy);
            int col = nearest(new double[]{-1, -0.5, 0, 0.5, 1}, Math.max(-1, Math.min(1, vx * facing / 400)));
            sprite.show("airflow", row * 5 + col);
        } else if (landTimer > 0) {
            sprite.show("land", 24 + Math.min(7, (int) ((0.25 - landTimer) / 0.25 * 8)));
        } else if (Math.abs(vx) > 5) {
            boolean run = Math.abs(vx) > 270;
            stride = (stride + Math.abs(vx) * tpf / (run ? 132 : 144)) % 1.0;
            int col = nearest(run ? new double[]{0.85, 1.0, 1.2} : new double[]{0.6, 0.75, 0.9}, Math.abs(vx) / 400);
            sprite.show(run ? "run" : "walk", col * 10 + Math.min(9, (int) (stride * 10)));
        } else {
            sprite.loop("idle", 8);
        }
    }

    private static int nearest(double[] values, double v) {
        int best = 0;
        for (int i = 1; i < values.length; i++) if (Math.abs(values[i] - v) < Math.abs(values[best] - v)) best = i;
        return best;
    }

    //-----attacking-----
    private void startStep() {
        move = combo.get(step);
        state = State.ATTACK;
        timer = 0;
        fired = 0;
        moveTimer = 0;
        stop();
        face(dirToPlayer());
        sprite.setTelegraph(TELL);
        if (kit.size() > 1 && (phase > 1 || step > 0)) HollowFx.icon(move.skill(), new Point2D(entity.getCenter().getX(), entity.getY() - 6));
        if (move.kind() == SkillMove.Kind.HOP) {
            sprite.setTelegraph(null);
            startHover();
        }
    }

    private void attack(double t) {
        timer += t;
        stop();
        switch (move.kind()) {
            case SWING -> swingStep(0.4, 0.95, false);
            case SLAM -> swingStep(0.7, 1.5, true);
            case FISTS -> fistsStep();
            case SHOT -> shotStep();
            default -> shieldStep();
        }
    }

    private void swingStep(double hitAt, double total, boolean slam) {
        pose("attack", 8, timer / total);
        if (fired == 0 && timer >= hitAt) {
            fired = 1;
            sprite.setTelegraph(null);
            Sfx.SWING_HEAVY.at(entity.getCenter(), 1, 0.6);
            if (!slam && Math.abs(dxToPlayer()) > 100) physics.setVelocityX(facing * 260);
            HollowStrike.swing(this, move, move.reach() * (piercing ? PIERCE_BOOST : 1), facing, total - hitAt + 0.1, -130, 45);
        }
        if (slam && fired == 1 && timer >= hitAt + 0.3) {      // the heavy blow reaches the ground
            fired = 2;
            Sfx.BRUTE_SLAM.at(entity.getCenter(), 0.7, 0.8);
            Impact.shake(8);
            ShockwaveComponent.spawn(entity.getCenter().getX() + facing * 40, entity.getBottomY(), facing, entity);
            Particles.of(Particles.DUST).count(10).angle(-90, 60).speed(80, 260)
                    .life(0.6).size(6).gravity(700).at(new Point2D(entity.getCenter().getX() + facing * 60, entity.getBottomY()));
        }
        if (timer >= total) stepDone();
    }

    private void fistsStep() {
        pose("attack", 8, timer / 1.1);
        int due = (int) ((timer - 0.25) / 0.25) + 1;
        while (fired < 3 && fired < due && timer >= 0.25) {
            fired++;
            sprite.setTelegraph(null);
            Sfx.PUNCH.at(entity.getCenter(), 1, 0.6);
            physics.setVelocityX(facing * 220);
            HollowStrike.punch(this, move, facing, damageOf(move) * (fired == 3 ? 1.5 : 1));
        }
        if (timer >= 1.1) stepDone();
    }

    private void shotStep() {
        boolean spell = move.sound().name().startsWith("CAST");
        double windup = 0.45;
        pose(spell ? "cast" : "shoot", spell ? 8 : 5, timer / (windup + 0.5));
        boolean fan = move.skill() == SkillType.POISON_DARTS;
        int total = fan ? 1 : move.count();
        double due = windup + 0.12 * fired;
        if (fired < total && timer >= due) {
            fired++;
            sprite.setTelegraph(null);
            fire(fan);
        }
        if (timer >= windup + 0.12 * total + 0.4) stepDone();
    }

    private void fire(boolean fan) {
        Point2D from = new Point2D(entity.getCenter().getX() + facing * 30, entity.getY() + 52);
        Point2D target = player.getCenter();
        double speed = move.speed() * (piercing ? PIERCE_BOOST : 1);
        Point2D v = move.gravity() > 0 ? Ballistics.velocity(from, target, speed, move.gravity()) : null;
        if (v == null) v = target.subtract(from).normalize().multiply(speed);
        double dmg = damageOf(move);
        move.sound().at(entity.getCenter(), 1, move.soundRate());
        if (fan) {
            for (int i = -1; i <= 1; i++) HollowStrike.bolt(this, move, from, rotate(v, i * 5), dmg);
        } else {
            HollowStrike.bolt(this, move, from, v, dmg);
        }
    }

    private static Point2D rotate(Point2D v, double degrees) {
        double r = Math.toRadians(degrees);
        return new Point2D(v.getX() * Math.cos(r) - v.getY() * Math.sin(r), v.getX() * Math.sin(r) + v.getY() * Math.cos(r));
    }

    /** Mana Shield: its own shield that soaks hits until it is broken. */
    private void shieldStep() {
        sprite.loop("block", 8);
        if (fired == 0 && timer >= 0.3) {
            fired = 1;
            sprite.setTelegraph(null);
            if (!stats.hasWard()) {
                stats.ward(WARD);
                Sfx.SHIELD_ON.at(entity.getCenter(), 1, 0.6);
                Particles.of(Particles.VOID).count(14).angle(-90, 180).speed(60, 220)
                        .life(0.5).size(4).gravity(0).at(entity.getCenter());
            }
        }
        if (timer >= 0.7) stepDone();
    }

    private void pose(String anim, int frames, double progress) {
        sprite.show(anim, Math.max(0, Math.min(frames - 1, (int) (progress * frames))));
    }

    private void stepDone() {
        sprite.setTelegraph(null);
        boolean last = ++step >= combo.size();
        if (!last) {
            startStep();
            return;
        }
        boolean big = move.kind() != SkillMove.Kind.SHIELD;
        comboDone();
        if (big) startRecoil();
        else endAttack(0.5);
    }

    private void endAttack(double pause) {
        state = State.MOVE;
        timer = 0;
        moveTimer = 0;
        cooldown = pause;
    }

    /** After a big attack: it recoils and the hollow flares, a window for the player. */
    private void startRecoil() {
        state = State.RECOIL;
        timer = 0;
        stop();
        flare = FLARE_TIME;
        Sfx.HOLLOW_FLARE.at(entity.getCenter());
        Particles.of(Particles.VOID).count(10).angle(-90, 180).speed(50, 200)
                .life(0.5).size(4).gravity(-40).at(entity.getCenter().add(0, 14));
    }

    private void recoil(double t) {
        timer += t;
        stop();
        sprite.show("hurt", timer < 0.15 ? 1 : 3);
        if (timer >= FLARE_TIME + 0.1) endAttack(FXGLMath.random(0.35, 0.7));
    }

    private void updateFlare() {
        if (flareNode == null) return;
        double open = dying ? 1 : Math.min(1, flare / 0.2);
        double pulse = 0.85 + 0.15 * Math.sin(orbit * 20);
        flareNode.setOpacity(open * pulse);
        flareNode.setScaleX(1 + 0.6 * open);
        flareNode.setScaleY(1 + 0.6 * open);
    }

    //-----pogo and hover-----
    private void startPogo() {
        state = State.POGO;
        pogoStage = 0;
        bounced = false;
        pogoCooldown = FXGLMath.random(6, 9);
        timer = 0;
        face(dirToPlayer());
        physics.setVelocityY(-740);
        grounded = false;
        Sfx.ENEMY_JUMP.at(entity.getCenter(), 1, 0.6);
    }

    /** Jumps, drops onto the player's head and bounces off it (like the player's down-slash); on the floor it slams. */
    private void pogo(double t) {
        double vy = physics.getVelocityY();
        face(dirToPlayer());
        double steer = Math.max(-380, Math.min(380, dxToPlayer() * 3));
        physics.setVelocityX(steer);
        if (pogoStage == 0) {
            sprite.show("takeoff", vy < -500 ? 1 : 2);
            if (vy > 0) pogoStage = 1;
            return;
        }
        if (pogoStage == 1) {
            sprite.show("dive", (int) (timer * 14) % 4);
            timer += t;
            physics.setVelocityY(Math.max(vy, 900));
            if (!bounced && entity.isColliding(player) && entity.getBottomY() - player.getY() < 60) {
                bounced = true;
                strike(12 * (1 + SHARD_BONUS * shards), null);
                physics.setVelocityY(-640);
                Sfx.BOUNCE.at(entity.getCenter());
                Impact.shake(5);
                pogoStage = 2;
                return;
            }
            if (justLanded() || (grounded && timer > 0.1)) {
                slamLanding(120, 12);
                startRecoil();
            }
            return;
        }
        // bounced off the head: a short hop, then it lands
        sprite.show("airflow", 17);
        if (grounded) startRecoil();
    }

    private void slamLanding(double radius, double damage) {
        timer = 0;
        stop();
        Sfx.SLAM_IMPACT.at(entity.getCenter(), 0.9, 0.6);
        Impact.shake(9);
        Particles.of(Particles.DUST).count(14).angle(-90, 70).speed(100, 340)
                .life(0.7).size(7).gravity(800).at(new Point2D(entity.getCenter().getX(), entity.getBottomY()));
        Particles.of(Particles.VOID).count(8).angle(-90, 70).speed(100, 300)
                .life(0.5).size(5).gravity(400).at(new Point2D(entity.getCenter().getX(), entity.getBottomY()));
        if (radius > 0 && Math.abs(dxToPlayer()) < radius && player.getBottomY() >= entity.getBottomY() - 30) {
            strike(damage * (1 + SHARD_BONUS * shards), null);
        }
        if (radius > 0) ShockwaveComponent.spawn(entity.getCenter().getX() + facing * 40, entity.getBottomY(), facing, entity);
    }

    private void startHover() {
        state = State.HOVER;
        hoverStage = 0;
        hoverTimer = 0;
        fired = 0;
        hoverStartY = entity.getY();
        physics.setVelocityY(-760);
        grounded = false;
        Sfx.LEVITATE.at(entity.getCenter(), 1, 0.6);
    }

    /** Levitation: it hops up, hovers over the fight (shooting with a ranged copy, if it has one) and dives. */
    private void hover(double t) {
        face(dirToPlayer());
        double vy = physics.getVelocityY();
        if (hoverStage == 0) {                                   // up
            sprite.show("levitate", 18 + (int) (timer * 8) % 6);
            timer += t;
            physics.setVelocityX(Math.max(-250, Math.min(250, dxToPlayer())));
            if (hoverStartY - entity.getY() >= 240 || vy >= 0) {
                hoverStage = 1;
                hoverTimer = 0;
            }
            return;
        }
        if (hoverStage == 1) {                                   // hold
            hoverTimer += t;
            sprite.show("levitate", 18 + (int) (hoverTimer * 8) % 6);
            hoverDir = ((int) (hoverTimer / 0.4)) % 2 == 0 ? 1 : -1;
            physics.setVelocityY(hoverDir * 25);
            physics.setVelocityX(Math.max(-220, Math.min(220, dxToPlayer())));
            SkillMove shooter = shooter();
            if (shooter != null && fired < 2 && hoverTimer >= 0.4 + 0.5 * fired) {
                fired++;
                SkillMove saved = move;
                move = shooter;
                fire(shooter.skill() == SkillType.POISON_DARTS);
                move = saved;
            }
            if (hoverTimer >= 1.5) {
                hoverStage = 2;
                Sfx.SLAM_DIVE.at(entity.getCenter());
            }
            return;
        }
        sprite.show("dive", (int) (timer * 14) % 4);             // dive
        timer += t;
        physics.setVelocityY(1000);
        physics.setVelocityX(Math.max(-300, Math.min(300, dxToPlayer() * 2)));
        if (justLanded() || (grounded && hoverTimer > 1.5)) {
            slamLanding(130, 14);
            combo.clear();
            comboDone();
            startRecoil();
        }
    }

    private SkillMove shooter() {
        for (SkillMove m : kit) if (m.kind() == SkillMove.Kind.SHOT) return m;
        return null;
    }

    //-----landing hits and the stolen embers-----
    /** Position helpers for its weapons. */
    Point2D shoulder() {
        return new Point2D(entity.getCenter().getX() + facing * 6, entity.getY() + 34);
    }

    Point2D chest() {
        return new Point2D(entity.getCenter().getX() + facing * 20, entity.getY() + 62);
    }

    /** What a copy of this skill hurts for: the player's damage turned into a boss blow, buffed by its ember shards. */
    double damageOf(SkillMove m) {
        return Math.min(30, 6 + m.damage() * 1.3) * sharpen * (1 + SHARD_BONUS * shards);
    }

    /** One of its blows reached the player. */
    void strike(double damage, SkillMove m) {
        if (hurtPlayer(damage)) afterHit(m);
    }

    /** It hurt the player: it steals an ember and its copied skill's element (and enchantments) apply. */
    void afterHit(SkillMove m) {
        if (shards < MAX_SHARDS) {
            shards++;
            Sfx.HOLLOW_STEAL.at(player.getCenter());
            Point2D from = player.getCenter(), to = entity.getCenter();
            Point2D dir = to.subtract(from);
            for (int i = 0; i < 5; i++) {
                Particles.spawn(from.add(FXGLMath.random(-10, 10), FXGLMath.random(-14, 14)), dir.multiply(1.6),
                        Particles.EMBER[i % 3], 6, 0.6, 0, 20);
            }
        }
        PlayerStatsComponent ps = player.getComponent(PlayerStatsComponent.class);
        if (m != null) {
            if (m.burn() > 0) ps.burn(m.burn());
            if (m.chill() > 0) ps.chill(m.chill());
            if (m.poison() > 0) ps.poison(m.poison());
        }
        if (imbue != null && m != null && m.kind() != SkillMove.Kind.SHIELD) {
            switch (imbue) {
                case FIRE -> ps.burn(2);
                case ICE -> ps.chill(2);
                case POISON -> ps.poison(3);
            }
        }
        if (lifeSteal) {
            HealthDoubleComponent hp = entity.getComponent(HealthDoubleComponent.class);
            hp.restore(hp.getMaxValue() * STEAL_HEAL);
            Particles.of(Particles.VOID).count(6).angle(-90, 180).speed(30, 120)
                    .life(0.5).size(4).gravity(-40).at(entity.getCenter());
        }
    }

    private void orbitShards(double tpf) {
        orbit += tpf;
        for (int i = 0; i < shardViews.size(); i++) {
            FrameSprite s = shardViews.get(i);
            s.setVisible(i < shards);
            if (i >= shards) continue;
            double a = orbit * 2.4 + i * Math.PI * 2 / MAX_SHARDS;
            s.setTranslateX(entity.getWidth() / 2 + Math.cos(a) * 56 - s.getFrames().width() / 2);
            s.setTranslateY(62 + Math.sin(a) * 26 - s.getFrames().height() / 2);
        }
    }

    /** A hit on it: the hollow's weak point and the shards. Called by Combat before the damage is applied. */
    @Override
    public double guard(Hit h, Entity pl) {
        if (shards > 0 && looseCooldown <= 0) {
            shards--;
            looseCooldown = LOOSE_COOLDOWN;
            double vx = dirToPlayer() * FXGLMath.random(40, 160) + FXGLMath.random(-60, 60);
            HollowShardLoose.spawn(entity.getCenter(), vx, -FXGLMath.random(420, 560));
            Particles.of(Particles.EMBER).count(8).angle(-90, 120).speed(60, 240)
                    .life(0.4).size(4).gravity(300).at(entity.getCenter());
        }
        if (flare <= 0) return 1;
        boolean weapon = h.getKind() == DamageKind.MELEE || h.getKind() == DamageKind.RANGED;
        if (Combat.element(h, weapon, pl.getComponent(LoadoutComponent.class)) == Element.FIRE) {
            stats.stun(FLARE_STUN);                    // a fire hit relights the hollow: it staggers
            Particles.of(Particles.EMBER).count(14).angle(-90, 180).speed(80, 300)
                    .life(0.5).size(5).gravity(200).at(entity.getCenter().add(0, 14));
        }
        return FLARE_MULT;
    }

    //-----echoes-----
    /** Phase 3: ghosts replay the player's own recent path. */
    private void echoes(double t) {
        echoTimer -= t;
        if (echoTimer > 0) return;
        echoTimer = FXGLMath.random(ECHO_MIN, ECHO_MAX);
        double f = hpFraction();
        int n = 1 + (f < 0.22 ? 1 : 0) + (f < 0.1 ? 1 : 0);
        for (int i = 0; i < n && HollowEcho.alive() < HollowEcho.MAX; i++) {
            double age = ECHO_AGE - i * ECHO_STEP;
            HollowEcho.spawnFrom(recorder, (int) (age * 60));
        }
    }

    //-----disabled, interrupted-----
    @Override
    protected void whileDisabled(double tpf) {
        super.whileDisabled(tpf);
        orbitShards(tpf);
        looseCooldown -= tpf;
        flare = Math.max(0, flare - tpf);
        recorder.record(player);
        updateFlare();
    }

    @Override
    protected void disabledPose() {
        sprite.show("hurt", 3);
    }

    @Override
    protected void interrupt() {
        if (state == State.RISE || state == State.ROAR || state == State.DYING) return;
        state = State.MOVE;
        combo.clear();
        sprite.setTelegraph(null);
        cooldown = Math.max(cooldown, 0.6);
        moveTimer = 0;
    }

    //-----death-----
    @Override
    public boolean diesSlowly() {
        return true;
    }

    /** The killing blow: the shards fly back into the player, the hollow fills with ember light, it melts into the glass. */
    @Override
    public void onDeath() {
        dying = true;
        deathT = 0;
        deathCue = 0;
        state = State.DYING;
        physics.setVelocityX(0);
        sprite.setTelegraph(null);
        HollowEcho.clear();
        Sfx.HOLLOW_DEATH.at(entity.getCenter(), 1, 1);
        Impact.quake(14);
        for (int i = 0; i < shards; i++) {
            Point2D from = entity.getCenter().add(FXGLMath.random(-40, 40), FXGLMath.random(-30, 30));
            Point2D to = player.getCenter().subtract(from);
            for (int k = 0; k < 3; k++) Particles.spawn(from, to.multiply(1 / SHARDS_BACK), Particles.VOID[k % 3], 8, SHARDS_BACK, 0, 20);
        }
        shards = 0;
        arena.bossDying(entity);
    }

    @Override
    protected void dying(double tpf) {
        deathT += tpf;
        physics.setVelocityX(0);
        orbitShards(tpf);
        orbit += tpf;
        flare = 1;
        updateFlare();
        Point2D c = entity.getCenter();
        if (deathCue == 0 && deathT >= SHARDS_BACK) {         // the shards are home: the hollow fills with ember light
            deathCue = 1;
            Particles.of(Particles.EMBER).count(20).angle(-90, 180).speed(80, 280)
                    .life(0.6).size(5).gravity(-40).at(player.getCenter());
            flareNode.setFill(new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.WHITE), new Stop(0.45, Color.web("#ffc23d")), new Stop(1, Color.web("#ff7a1f", 0))));
            sprite.once("death", 9, null);
        }
        if (deathCue == 1 && deathT >= SHARDS_BACK + 0.2 && FXGLMath.randomDouble() < tpf * 14) {
            Particles.of(Particles.EMBER).count(2).angle(-90, 40).speed(40, 160)
                    .life(0.7).size(5).gravity(-80).at(c.add(FXGLMath.random(-30, 30), FXGLMath.random(-10, 40)));
        }
        if (deathT >= DEATH_SEEP) {                           // seeps into the floor
            if (deathCue == 1) {
                deathCue = 2;
            }
            double k = Math.min(1, (deathT - DEATH_SEEP) / (DEATH_END - DEATH_SEEP));
            physics.overwritePosition(new Point2D(entity.getX(), entity.getY() + 34 * tpf));
            entity.setOpacity(1 - k);
            if (FXGLMath.randomDouble() < tpf * 16) {
                Particles.of(Particles.VOID).count(1).angle(-90, 40).speed(20, 90)
                        .life(0.6).size(4).gravity(100).at(new Point2D(c.getX() + FXGLMath.random(-50, 50), entity.getBottomY() - 40));
            }
        }
        if (deathT >= DEATH_END) burst();
    }

    private void burst() {
        Point2D c = entity.getCenter();
        Impact.quake(10);
        Particles.of(Particles.VOID).count(40).angle(-90, 180).speed(80, 360)
                .life(1.0).size(6).gravity(300).at(new Point2D(c.getX(), entity.getBottomY() - 30));
        arena.bossExploded(REWARD_SP);
        entity.removeFromWorld();
    }

    /** Gone without its death scene (fell into a pit, a new game): never leave the way barred. */
    @Override
    public void onRemoved() {
        if (!dying) arena.end();
        HollowEcho.clear();
    }
}
