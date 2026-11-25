package at.htl.flowstate.Components.Enemies.Bosses;

import at.htl.flowstate.Components.Enemies.Bosses.Hollow.HollowArena;
import at.htl.flowstate.Components.Enemies.Bosses.Hollow.HollowBehaviour;
import at.htl.flowstate.Components.Enemies.Bosses.Hydra.HydraArena;
import at.htl.flowstate.Components.Enemies.Bosses.Hydra.HydraBehaviour;
import at.htl.flowstate.Components.Enemies.Bosses.Hydra.TarGlobComponent;
import at.htl.flowstate.Components.Enemies.Bosses.Hydra.TarPoolComponent;
import at.htl.flowstate.Components.Enemies.Bosses.Hydra.TarWaveComponent;
import at.htl.flowstate.Components.Enemies.Bosses.Matriarch.AcidGlobComponent;
import at.htl.flowstate.Components.Enemies.Bosses.Matriarch.AcidPoolComponent;
import at.htl.flowstate.Components.Enemies.Bosses.Matriarch.MatriarchArena;
import at.htl.flowstate.Components.Enemies.Bosses.Matriarch.MatriarchBehaviour;
import at.htl.flowstate.Components.Enemies.Bosses.Matriarch.SilkHazardComponent;
import at.htl.flowstate.Components.Enemies.Bosses.PitLord.FissureComponent;
import at.htl.flowstate.Components.Enemies.Bosses.PitLord.MeteorComponent;
import at.htl.flowstate.Components.Enemies.DemonType;
import at.htl.flowstate.Components.Enemies.EnemyProjectileComponent;
import at.htl.flowstate.Components.Enemies.EnemyStatsComponent;
import at.htl.flowstate.Components.Enemies.GravityWellComponent;
import at.htl.flowstate.Components.Enemies.MagmaPoolComponent;
import at.htl.flowstate.Components.Enemies.PortalComponent;
import at.htl.flowstate.Components.Enemies.ShockwaveComponent;
import at.htl.flowstate.Components.Identifier.EnemyIdentifierComponent;
import at.htl.flowstate.Components.Player.PlayerMovementComponent;
import at.htl.flowstate.Components.Player.PlayerStatsComponent;
import at.htl.flowstate.Game;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Visuals.Impact;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.core.math.FXGLMath;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.SpawnData;
import com.almasb.fxgl.physics.PhysicsComponent;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

import static com.almasb.fxgl.dsl.FXGL.*;

/**
 * One boss fight's arena. LevelGeneration lays a long flat stretch and {@link #plan plans} the arena on it; when the
 * camera reaches the arena it stops scrolling, a hellfire gate slams down at the right edge of the screen, then one
 * at the left edge, boxing the player in. In a short cutscene (no player control, letterbox bars) the boss
 * drops in behind the player and roars; then the fight starts. Every other demon and everything they threw or
 * opened is frozen in place for the cutscene.
 * <p>
 * Its death is a cutscene too ({@link #bossDying}): control goes, the letterbox comes back and everything else
 * freezes while the boss collapses; when it bursts ({@link #bossExploded}) the player gets the reward, its minions
 * and their projectiles burn away with a flash, the gates open, and control returns.
 * <p>
 * All of a fight's state lives in its arena object, so a new fight or a new game starts clean by dropping the old
 * one. Only {@link #active} is static: the arena from planning until its fight is over and its UI has faded.
 */
public final class BossArena {
    /** Which boss the arena is for. */
    public enum Boss { PIT_LORD, MATRIARCH, HYDRA, HOLLOW }

    /** The arena is exactly one screen wide. */
    public static final double WIDTH = 1920;
    // cutscene timeline (s after the camera locks)
    private static final double LEFT_GATE_AT = 0.6;
    private static final double DROP_AT = 1.4;
    private static final double CONTROL_AFTER_LANDING = 1.3;   // the landing roar plays out first
    private static final double MAX_CUTSCENE = 7;              // whatever happens, control comes back
    private static final double LORD_X = 380;                  // the Lord's left edge, from the arena's left edge
    private static final double DROP_HEIGHT = 800;             // above its standing height
    private static final double BAR_H = 90, BAR_SPEED = 260;   // letterbox
    // the Void Matriarch's intro: the floor over the chasm crumbles, she spins her bridges and lowers herself in
    private static final double CRUMBLE_AT = 1.0, WEAVE_AT = 1.25, BRIDGE_WEAVE = 1.0, MATRIARCH_AT = 1.4;
    // the Tar Hydra's: the floor sinks into a tar lake, the pillars push up, then its body comes and the heads rise
    private static final double TAR_AT = 0.9, HYDRA_AT = 1.5;
    // the Hollow Ember's: the floor turns to black glass, the player's reflection lags, stops, and it climbs out of it
    private static final double GLASS_AT = 0.9, HOLLOW_AT = 2.3;
    // death scene, after the burst (s)
    private static final double GATES_OPEN_AFTER = 0.7, CONTROL_AFTER = 1.6, FLASH_TIME = 0.6;
    private static final double REWARD_SOUND_DELAY = 0.9;

    private enum Phase { PENDING, CUTSCENE, FIGHT, DEATH, OVER }

    private static BossArena active;

    private final Boss boss;
    private final double lockX, groundY, hpScale;
    private final MatriarchArena matriarch;
    private final HydraArena hydra;
    private final HollowArena hollow;
    private final List<Entity> frozen = new ArrayList<>();
    private Phase phase = Phase.PENDING;
    private Entity bossEntity;
    private boolean introStarted, woven, leftClosed, bossSpawned;
    private double time, landedAt;
    private Entity leftGate, rightGate;
    private Rectangle barTop, barBottom, flash;
    private double deathTime;
    private boolean exploded, gatesOpened;

    private BossArena(double lockX, double groundY, Boss boss, double hpScale, Entity pitFloor) {
        this.lockX = lockX;
        this.groundY = groundY;
        this.boss = boss;
        this.hpScale = hpScale;
        matriarch = boss == Boss.MATRIARCH ? new MatriarchArena(lockX, groundY, pitFloor) : null;
        hydra = boss == Boss.HYDRA ? new HydraArena(this, lockX, groundY, pitFloor) : null;
        hollow = boss == Boss.HOLLOW ? new HollowArena(this, lockX, groundY, lockX + HollowBehaviour.RISE_X) : null;
    }

    //-----the arena in play-----
    /**
     * The ground from lockX to lockX + WIDTH is flat at groundY; the arena closes when the camera gets there.
     * hpScale: the boss's share of its full HP (the first bosses of a run are weaker). pitFloor: the slab over the
     * Matriarch's chasm or the Hydra's lake (null for the others).
     */
    public static BossArena plan(double lockX, double groundY, Boss which, double hpScale, Entity pitFloor) {
        if (active != null) active.removeUi();
        active = new BossArena(lockX, groundY, which, hpScale, pitFloor);
        return active;
    }

    /** The arena from planning until its fight is over, or null. */
    public static BossArena active() {
        return active;
    }

    /** A new game: the old world and UI are gone, and so is any arena. */
    public static void reset() {
        active = null;
    }

    /** From the planned arena until the boss is dead: no other demons spawn. */
    public static boolean isActive() {
        return active != null && active.phase != Phase.OVER;
    }

    public Boss boss() {
        return boss;
    }

    /** The boss while it fights (not while it dies), or null. */
    public Entity fightingBoss() {
        boolean fighting = phase == Phase.CUTSCENE || phase == Phase.FIGHT;
        return fighting && bossEntity != null && bossEntity.isActive() ? bossEntity : null;
    }

    public MatriarchArena matriarch() {
        return matriarch;
    }

    public HydraArena hydra() {
        return hydra;
    }

    public HollowArena hollow() {
        return hollow;
    }

    public boolean inCutscene() {
        return phase == Phase.CUTSCENE;
    }

    private boolean closed() {
        return phase == Phase.CUTSCENE || phase == Phase.FIGHT;
    }

    /** x pulled at least margin px inside the arena while it is closed (summons stay in the fight). */
    public double insideX(double x, double margin) {
        if (!closed()) return x;
        return Math.max(lockX + margin, Math.min(lockX + WIDTH - margin, x));
    }

    /** How far x is from the inner face of the gate in direction dir; unbounded while the arena is open. */
    public double wallGap(double x, int dir) {
        if (!closed()) return Double.MAX_VALUE;
        double gate = BossGateComponent.WIDTH;
        return dir > 0 ? lockX + WIDTH - gate - x : x - (lockX + gate);
    }

    /** A random x at least margin px inside the closed arena; without one, somewhere around fallback. */
    public double randomX(double margin, double fallback) {
        if (!closed()) return fallback + FXGLMath.random(-700.0, 700.0);
        return lockX + FXGLMath.random(margin, WIDTH - margin);
    }

    /** Where the camera may go: it locks onto the arena once it gets there and stays until the fight is over. */
    public double clampCamera(double cameraX) {
        if (phase == Phase.PENDING && cameraX >= lockX) close();
        if (closed() || phase == Phase.DEATH) return Math.min(cameraX, lockX);
        return cameraX;
    }

    /** The player fell into the boss's pit: hurt, and back on footing. @return true if caught */
    public boolean catchFall(Entity player) {
        if (matriarch != null) return matriarch.catchFall(player);
        if (hydra != null) return hydra.catchFall(player);
        return false;
    }

    private void close() {
        phase = Phase.CUTSCENE;
        time = 0;
        landedAt = -1;
        // ends held actions (running, charging) before input goes deaf, so nothing sticks once it's back
        getInput().clearAll();
        getInput().setProcessInput(false);
        freezeOthers(null);
        boolean silk = boss == Boss.MATRIARCH;
        rightGate = BossGateComponent.spawn(lockX + WIDTH - BossGateComponent.WIDTH, groundY, silk, boss == Boss.HOLLOW);
        Impact.shake(16);
        if (silk) Sfx.MATRIARCH_SCREECH.at(new Point2D(lockX + WIDTH / 2, 0), 0.3, 0.6);
        else if (boss == Boss.HYDRA) Sfx.TAR_WAVE.at(new Point2D(lockX + WIDTH / 2, groundY), 0.5, 0.6);
        else if (boss == Boss.HOLLOW) Sfx.GATE_SLAM.at(new Point2D(lockX + WIDTH / 2, groundY), 0.3, 0.5);
        else Sfx.BOSS_ROAR.at(new Point2D(lockX + WIDTH, groundY), 0.35, 0.6);   // something stirs
        Sfx.ambient(arenaLoop());
        letterbox();
    }

    /** Every frame, from Game. */
    public void update(double tpf) {
        updateLetterbox(tpf);
        updateFlash(tpf);
        if (phase == Phase.OVER && barTop == null && flash == null && active == this) active = null;
        if (phase == Phase.DEATH && exploded) {
            updateDeathScene(tpf);
            return;
        }
        if (phase != Phase.CUTSCENE) return;
        time += tpf;
        if (!leftClosed && time >= LEFT_GATE_AT) {
            leftClosed = true;
            leftGate = BossGateComponent.spawn(lockX, groundY, boss == Boss.MATRIARCH, boss == Boss.HOLLOW);
            Impact.shake(16);
        }
        switch (boss) {
            case PIT_LORD -> lordIntro();
            case MATRIARCH -> matriarchIntro();
            case HYDRA -> hydraIntro();
            case HOLLOW -> hollowIntro();
        }
        if ((landedAt >= 0 && time >= landedAt + CONTROL_AFTER_LANDING) || time >= MAX_CUTSCENE) startFight();
    }

    private void updateDeathScene(double tpf) {
        deathTime += tpf;
        if (!gatesOpened && deathTime >= GATES_OPEN_AFTER) {
            gatesOpened = true;
            BossGateComponent.open(leftGate);
            BossGateComponent.open(rightGate);
            leftGate = rightGate = null;
        }
        if (deathTime >= CONTROL_AFTER) {
            phase = Phase.OVER;
            restoreInput();
        }
    }

    //-----intros-----
    private void lordIntro() {
        if (bossSpawned || time < DROP_AT) return;
        bossSpawned = true;
        DemonType lord = DemonType.LORD;
        bossEntity = spawn(lord.spawnName(), new SpawnData(lockX + LORD_X, groundY - lord.height - 2 - DROP_HEIGHT)
                .put("hpScale", hpScale));
        Sfx.BOSS_ROAR.at(new Point2D(lockX + LORD_X, groundY), 0.6, 0.8);
        // the player hears it coming and turns round
        movement().faceAttack(-1, 3);
    }

    private void matriarchIntro() {
        if (!introStarted && time >= CRUMBLE_AT) {
            introStarted = true;
            matriarch.crumble();
        }
        if (!woven && time >= WEAVE_AT) {
            woven = true;
            matriarch.weaveBridges(BRIDGE_WEAVE);
        }
        if (!bossSpawned && time >= MATRIARCH_AT) {
            bossSpawned = true;
            DemonType m = DemonType.MATRIARCH;
            bossEntity = spawn(m.spawnName(), new SpawnData(lockX + MatriarchBehaviour.INTRO_SPOT - m.width / 2, -m.height - 260)
                    .put("hpScale", hpScale));
            int dir = lockX + MatriarchBehaviour.INTRO_SPOT < Game.getPlayer().getCenter().getX() ? -1 : 1;
            movement().faceAttack(dir, 3);
        }
    }

    private void hydraIntro() {
        if (!introStarted && time >= TAR_AT) {
            introStarted = true;
            hydra.open();
        }
        if (!bossSpawned && time >= HYDRA_AT) {
            bossSpawned = true;
            DemonType h = DemonType.HYDRA;
            bossEntity = spawn(h.spawnName(), new SpawnData(lockX + HydraBehaviour.CENTRE_X - h.width / 2,
                    groundY + HydraBehaviour.BODY_BELOW - h.height / 2).put("hpScale", hpScale));
            movement().faceAttack(-1, 3);
        }
    }

    private void hollowIntro() {
        double riseX = lockX + HollowBehaviour.RISE_X;
        if (!introStarted && time >= GLASS_AT) {
            introStarted = true;
            hollow.open();
        }
        if (!bossSpawned && time >= HOLLOW_AT) {
            bossSpawned = true;
            DemonType h = DemonType.HOLLOW;
            bossEntity = spawn(h.spawnName(), new SpawnData(riseX - h.width / 2, groundY - h.height - 2).put("hpScale", hpScale));
            hollow.reflectionLeft();
            movement().faceAttack(-1, 3);
        }
    }

    /** Seconds since the camera locked (the cutscene clock). */
    public double time() {
        return time;
    }

    /** The boss hit the ground. */
    public void landed(Entity boss) {
        if (phase != Phase.CUTSCENE) return;
        landedAt = time;
        int dir = boss.getCenter().getX() < Game.getPlayer().getCenter().getX() ? -1 : 1;
        movement().faceAttack(dir, CONTROL_AFTER_LANDING);
    }

    /**
     * A short cutscene in the middle of the fight (the Tar Hydra's last head coming up): control goes, the letterbox
     * comes back and everything but the boss freezes, until the boss calls {@link #landed} again.
     */
    public void midFightCutscene(Entity boss) {
        if (phase != Phase.FIGHT) return;
        phase = Phase.CUTSCENE;
        time = 0;
        landedAt = -1;
        getInput().clearAll();
        getInput().setProcessInput(false);
        freezeOthers(boss);
        letterbox();
    }

    private void startFight() {
        phase = Phase.FIGHT;
        restoreInput();
        thaw();
    }

    //-----the end-----
    /** The killing blow: the death scene starts. Control goes and everything but the boss freezes. */
    public void bossDying(Entity boss) {
        if (!closed()) return;
        if (phase == Phase.CUTSCENE) thaw();
        phase = Phase.DEATH;
        deathTime = 0;
        exploded = false;
        gatesOpened = false;
        getInput().clearAll();
        getInput().setProcessInput(false);
        freezeOthers(boss);
        letterbox();
    }

    /** The boss burst: the player's reward, its minions and whatever they threw burn away, a flash, then the gates. */
    public void bossExploded(int rewardSkillPoints) {
        PlayerStatsComponent stats = Game.getPlayer().getComponent(PlayerStatsComponent.class);
        for (int i = 0; i < rewardSkillPoints; i++) stats.addSkillPoint();
        runOnce(Sfx.LEVEL_UP::play, Duration.seconds(REWARD_SOUND_DELAY));
        if (phase != Phase.DEATH) {                 // died without its death scene: just clear the way
            end();
            return;
        }
        for (Entity e : frozen) {
            if (!e.isActive()) continue;
            if (e.hasComponent(EnemyIdentifierComponent.class)) {
                Particles.of(voidBoss() ? Particles.VOID : Particles.EMBER).count(14).angle(-90, 80).speed(60, 260)
                        .life(0.8).size(5).gravity(-100).at(e.getCenter());
            }
            e.removeFromWorld();
        }
        frozen.clear();
        exploded = true;
        deathTime = 0;
        finishArena();
        screenFlash();
    }

    /** The boss is gone without its death scene (fell): never leave the way barred. */
    public void end() {
        if (phase == Phase.OVER) return;
        if (phase == Phase.CUTSCENE || phase == Phase.DEATH) {
            restoreInput();
            thaw();
        }
        phase = Phase.OVER;
        BossGateComponent.open(leftGate);
        BossGateComponent.open(rightGate);
        leftGate = rightGate = null;
        finishArena();
    }

    private boolean voidBoss() {
        return boss == Boss.MATRIARCH || boss == Boss.HOLLOW;
    }

    private Sfx arenaLoop() {
        return switch (boss) {
            case PIT_LORD -> Sfx.AMB_LORD;
            case MATRIARCH -> Sfx.AMB_MATRIARCH;
            case HYDRA -> Sfx.AMB_HYDRA;
            case HOLLOW -> Sfx.AMB_HOLLOW;
        };
    }

    /** What is special about the boss's arena goes back to plain ground, and the abyss's drone comes back. */
    private void finishArena() {
        Sfx.ambient(Sfx.AMB_ABYSS);
        if (matriarch != null) matriarch.finish();
        if (hydra != null) hydra.finish();
        if (hollow != null) hollow.finish();
    }

    private static void restoreInput() {
        if (!Game.getPlayer().getComponent(PlayerStatsComponent.class).isDead()) getInput().setProcessInput(true);
    }

    //-----freezing-----
    /** Every demon (the boss isn't there yet) and every projectile, hazard and portal they made. */
    private void freezeOthers(Entity except) {
        frozen.clear();
        frozen.addAll(getGameWorld().getEntitiesFiltered(e -> e != except && !isHydraPart(e) && (
                e.hasComponent(EnemyIdentifierComponent.class)
                        || e.hasComponent(EnemyProjectileComponent.class)
                        || e.hasComponent(ShockwaveComponent.class)
                        || e.hasComponent(MagmaPoolComponent.class)
                        || e.hasComponent(GravityWellComponent.class)
                        || e.hasComponent(PortalComponent.class)
                        || e.hasComponent(FissureComponent.class)
                        || e.hasComponent(MeteorComponent.class)
                        || e.hasComponent(SilkHazardComponent.class)
                        || e.hasComponent(AcidGlobComponent.class)
                        || e.hasComponent(AcidPoolComponent.class)
                        || e.hasComponent(TarGlobComponent.class)
                        || e.hasComponent(TarPoolComponent.class)
                        || e.hasComponent(TarWaveComponent.class))));
        // no AI, timers or animation, and out of the physics simulation (no falling, no drifting)
        for (Entity e : frozen) setFrozen(e, true);
    }

    /** The Tar Hydra's heads and stumps play out its death scene themselves. */
    private static boolean isHydraPart(Entity e) {
        return e.getComponentOptional(EnemyStatsComponent.class).map(s -> s.getType().isHydraPart()).orElse(false);
    }

    private void thaw() {
        for (Entity e : frozen) if (e.isActive()) setFrozen(e, false);
        frozen.clear();
    }

    private static void setFrozen(Entity e, boolean frozen) {
        e.setUpdateEnabled(!frozen);
        if (e.hasComponent(PhysicsComponent.class)) {
            PhysicsComponent physics = e.getComponent(PhysicsComponent.class);
            if (physics.getBody() != null) physics.getBody().setActive(!frozen);
        }
    }

    private static PlayerMovementComponent movement() {
        return Game.getPlayer().getComponent(PlayerMovementComponent.class);
    }

    //-----flash (the boss bursting)-----
    private void screenFlash() {
        if (flash == null) {
            flash = new Rectangle(getAppWidth(), getAppHeight(), voidBoss() ? Color.rgb(225, 205, 255)
                    : boss == Boss.HYDRA ? Color.rgb(255, 205, 150) : Color.rgb(255, 230, 170));
            flash.setMouseTransparent(true);
            addUINode(flash);
        }
        flash.setOpacity(0.85);
    }

    private void updateFlash(double tpf) {
        if (flash == null) return;
        flash.setOpacity(Math.max(0, flash.getOpacity() - tpf * 0.85 / FLASH_TIME));
        if (flash.getOpacity() <= 0) {
            removeUINode(flash);
            flash = null;
        }
    }

    //-----letterbox-----
    private void letterbox() {
        if (barTop != null) return;
        double w = getAppWidth();
        barTop = new Rectangle(w, 0, Color.BLACK);
        barBottom = new Rectangle(w, 0, Color.BLACK);
        barBottom.setTranslateY(getAppHeight());
        addUINode(barTop);
        addUINode(barBottom);
    }

    private void updateLetterbox(double tpf) {
        if (barTop == null) return;
        double target = phase == Phase.CUTSCENE || (phase == Phase.DEATH && deathTime < CONTROL_AFTER - 0.35) ? BAR_H : 0;
        double h = barTop.getHeight();
        h = h < target ? Math.min(target, h + BAR_SPEED * tpf) : Math.max(target, h - BAR_SPEED * tpf);
        barTop.setHeight(h);
        barBottom.setHeight(h);
        barBottom.setTranslateY(getAppHeight() - h);
        if (h <= 0 && target == 0) removeUi();
    }

    /** The letterbox and flash, gone at once (a new arena replaced this one before they faded). */
    private void removeUi() {
        if (barTop != null) {
            removeUINode(barTop);
            removeUINode(barBottom);
            barTop = barBottom = null;
        }
        if (flash != null) {
            removeUINode(flash);
            flash = null;
        }
    }
}
