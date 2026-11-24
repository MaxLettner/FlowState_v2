package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Components.Identifier.EnemyIdentifierComponent;
import at.htl.flowstate.Components.Player.PlayerMovementComponent;
import at.htl.flowstate.Components.Player.PlayerStatsComponent;
import at.htl.flowstate.Game;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Visuals.Impact;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.SpawnData;
import com.almasb.fxgl.physics.PhysicsComponent;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.ArrayList;
import java.util.List;

import static com.almasb.fxgl.dsl.FXGL.*;

/**
 * The Pit Lord's arena. LevelGeneration lays a long flat stretch and {@link #plan plans} the arena on it; when the
 * camera reaches the arena it stops scrolling, a hellfire gate slams down at the right edge of the screen, then one
 * at the left edge, boxing the player in. In a short cutscene (no player control, letterbox bars) the Pit Lord
 * drops in behind the player and roars; then the fight starts. Every other demon and everything they threw or
 * opened is frozen in place for the cutscene.
 * <p>
 * Its death is a cutscene too ({@link #bossDying}): control goes, the letterbox comes back and everything else
 * freezes while the Lord collapses; when it bursts ({@link #bossExploded}) its minions and their projectiles burn
 * away with a flash, the gates open, and control returns. Killing it opens both gates and frees the camera.
 */
public final class BossArena {
    /** Which boss the arena is for. */
    public enum Boss { PIT_LORD, MATRIARCH, HYDRA }

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

    private enum Phase { NONE, PENDING, CUTSCENE, FIGHT, DEATH }
    // death scene, after the burst (s)
    private static final double GATES_OPEN_AFTER = 0.7, CONTROL_AFTER = 1.6, FLASH_TIME = 0.6;

    private static Phase phase = Phase.NONE;
    private static Boss boss = Boss.PIT_LORD;
    private static boolean crumbled, woven;
    private static double lockX, groundY, time, landedAt;
    private static double hpScale = 1;
    private static boolean leftClosed, lordSpawned;
    private static Entity leftGate, rightGate;
    private static Rectangle barTop, barBottom, flash;
    private static double deathTime;
    private static boolean exploded, gatesOpened;
    private static final List<Entity> frozen = new ArrayList<>();

    private BossArena() {}

    /** A new game. */
    public static void reset() {
        phase = Phase.NONE;
        MatriarchArena.reset();
        HydraArena.reset();
        leftGate = rightGate = null;
        barTop = barBottom = flash = null;    // the UI was cleared with the old game
        frozen.clear();               // ... and the world
    }

    /** The ground from lockX to lockX + WIDTH is flat at groundY; the arena closes when the camera gets there. */
    public static void plan(double lockX, double groundY) {
        plan(lockX, groundY, Boss.PIT_LORD, 1);
    }

    /** hpScale: the boss's share of its full HP (the first bosses of a run are weaker). */
    public static void plan(double lockX, double groundY, Boss which, double hpScale) {
        BossArena.lockX = lockX;
        BossArena.groundY = groundY;
        boss = which;
        BossArena.hpScale = hpScale;
        phase = Phase.PENDING;
    }

    public static Boss boss() {
        return boss;
    }

    /** From the planned arena until the boss is dead: no other demons spawn. */
    public static boolean isActive() {
        return phase != Phase.NONE;
    }

    public static boolean inCutscene() {
        return phase == Phase.CUTSCENE;
    }

    /** x pulled at least margin px inside the arena while it is closed (summons stay in the fight). */
    public static double insideX(double x, double margin) {
        if (phase != Phase.CUTSCENE && phase != Phase.FIGHT) return x;
        return Math.max(lockX + margin, Math.min(lockX + WIDTH - margin, x));
    }

    /** How far x is from the inner face of the gate in direction dir; unbounded while the arena is open. */
    public static double wallGap(double x, int dir) {
        if (phase != Phase.CUTSCENE && phase != Phase.FIGHT) return Double.MAX_VALUE;
        double gate = BossGateComponent.WIDTH;
        return dir > 0 ? lockX + WIDTH - gate - x : x - (lockX + gate);
    }

    /** A random x at least margin px inside the closed arena; without one, somewhere around fallback. */
    public static double randomX(double margin, double fallback) {
        if (phase != Phase.CUTSCENE && phase != Phase.FIGHT) return fallback + (Math.random() - 0.5) * 1400;
        return lockX + margin + Math.random() * (WIDTH - 2 * margin);
    }

    /** Where the camera may go: it locks onto the arena once it gets there and stays until the fight is over. */
    public static double clampCamera(double cameraX) {
        if (phase == Phase.PENDING && cameraX >= lockX) close();
        if (phase == Phase.CUTSCENE || phase == Phase.FIGHT || phase == Phase.DEATH) return Math.min(cameraX, lockX);
        return cameraX;
    }

    private static void close() {
        phase = Phase.CUTSCENE;
        time = 0;
        landedAt = -1;
        leftClosed = false;
        lordSpawned = false;
        // ends held actions (running, charging) before input goes deaf, so nothing sticks once it's back
        getInput().clearAll();
        getInput().setProcessInput(false);
        freezeOthers(null);
        crumbled = woven = false;
        boolean silk = boss == Boss.MATRIARCH;
        rightGate = BossGateComponent.spawn(lockX + WIDTH - BossGateComponent.WIDTH, groundY, silk);
        Impact.shake(16);
        if (silk) Sfx.MATRIARCH_SCREECH.at(new Point2D(lockX + WIDTH / 2, 0), 0.3, 0.6);
        else if (boss == Boss.HYDRA) Sfx.TAR_WAVE.at(new Point2D(lockX + WIDTH / 2, groundY), 0.5, 0.6);
        else Sfx.BOSS_ROAR.at(new Point2D(lockX + WIDTH, groundY), 0.35, 0.6);   // something stirs
        Sfx.ambient(arenaLoop());
        letterbox();
    }

    /** Every frame, from Game. */
    public static void update(double tpf) {
        updateLetterbox(tpf);
        updateFlash(tpf);
        if (phase == Phase.DEATH && exploded) {
            deathTime += tpf;
            if (!gatesOpened && deathTime >= GATES_OPEN_AFTER) {
                gatesOpened = true;
                BossGateComponent.open(leftGate);
                BossGateComponent.open(rightGate);
                leftGate = rightGate = null;
            }
            if (deathTime >= CONTROL_AFTER) {
                phase = Phase.NONE;
                restoreInput();
            }
            return;
        }
        if (phase != Phase.CUTSCENE) return;
        time += tpf;
        if (!leftClosed && time >= LEFT_GATE_AT) {
            leftClosed = true;
            leftGate = BossGateComponent.spawn(lockX, groundY, boss == Boss.MATRIARCH);
            Impact.shake(16);
        }
        if (boss == Boss.MATRIARCH) {
            matriarchIntro();
        } else if (boss == Boss.HYDRA) {
            hydraIntro();
        } else if (!lordSpawned && time >= DROP_AT) {
            lordSpawned = true;
            DemonType lord = DemonType.LORD;
            spawn(lord.spawnName(), new SpawnData(lockX + LORD_X, groundY - lord.height - 2 - DROP_HEIGHT)
                    .put("hpScale", hpScale));
            Sfx.BOSS_ROAR.at(new Point2D(lockX + LORD_X, groundY), 0.6, 0.8);
            // the player hears it coming and turns round
            movement().faceAttack(-1, 3);
        }
        if ((landedAt >= 0 && time >= landedAt + CONTROL_AFTER_LANDING) || time >= MAX_CUTSCENE) startFight();
    }

    private static void matriarchIntro() {
        if (!crumbled && time >= CRUMBLE_AT) {
            crumbled = true;
            MatriarchArena.crumble();
        }
        if (!woven && time >= WEAVE_AT) {
            woven = true;
            MatriarchArena.weaveBridges(BRIDGE_WEAVE);
        }
        if (!lordSpawned && time >= MATRIARCH_AT) {
            lordSpawned = true;
            DemonType m = DemonType.MATRIARCH;
            spawn(m.spawnName(), new SpawnData(lockX + MatriarchBehaviour.INTRO_SPOT - m.width / 2, -m.height - 260)
                    .put("hpScale", hpScale));
            int dir = lockX + MatriarchBehaviour.INTRO_SPOT < Game.getPlayer().getCenter().getX() ? -1 : 1;
            movement().faceAttack(dir, 3);
        }
    }

    private static void hydraIntro() {
        if (!crumbled && time >= TAR_AT) {
            crumbled = true;
            HydraArena.open();
        }
        if (!lordSpawned && time >= HYDRA_AT) {
            lordSpawned = true;
            DemonType h = DemonType.HYDRA;
            spawn(h.spawnName(), new SpawnData(lockX + HydraBehaviour.CENTRE_X - h.width / 2,
                    groundY + HydraBehaviour.BODY_BELOW - h.height / 2).put("hpScale", hpScale));
            movement().faceAttack(-1, 3);
        }
    }

    /** The Pit Lord hit the ground. */
    static void landed(Entity lord) {
        if (phase != Phase.CUTSCENE) return;
        landedAt = time;
        int dir = lord.getCenter().getX() < Game.getPlayer().getCenter().getX() ? -1 : 1;
        movement().faceAttack(dir, CONTROL_AFTER_LANDING);
    }

    /**
     * A short cutscene in the middle of the fight (the Tar Hydra's last head coming up): control goes, the letterbox
     * comes back and everything but the boss freezes, until the boss calls {@link #landed} again.
     */
    static void midFightCutscene(Entity boss) {
        if (phase != Phase.FIGHT) return;
        phase = Phase.CUTSCENE;
        time = 0;
        landedAt = -1;
        getInput().clearAll();
        getInput().setProcessInput(false);
        freezeOthers(boss);
        letterbox();
    }

    private static void startFight() {
        phase = Phase.FIGHT;
        restoreInput();
        thaw();
    }

    /** The killing blow: the death scene starts. Control goes and everything but the Lord freezes. */
    static void bossDying(Entity lord) {
        if (phase != Phase.FIGHT && phase != Phase.CUTSCENE) return;
        if (phase == Phase.CUTSCENE) thaw();
        phase = Phase.DEATH;
        deathTime = 0;
        exploded = false;
        gatesOpened = false;
        getInput().clearAll();
        getInput().setProcessInput(false);
        freezeOthers(lord);
        letterbox();
    }

    /** The Lord burst: its minions and whatever they threw burn away, a flash, then the gates and control. */
    static void bossExploded() {
        if (phase != Phase.DEATH) {                 // died outside a closed arena (tests): just clear the way
            end();
            finishArena();
            return;
        }
        for (Entity e : frozen) {
            if (!e.isActive()) continue;
            if (e.hasComponent(EnemyIdentifierComponent.class)) {
                Particles.burst(e.getCenter(), boss == Boss.MATRIARCH ? Particles.VOID : Particles.EMBER, 14, -90, 80, 60, 260, 0.8, 5, -100);
            }
            e.removeFromWorld();
        }
        frozen.clear();
        exploded = true;
        deathTime = 0;
        finishArena();
        screenFlash();
    }

    /** The Pit Lord is gone without its death scene (fell, a new game): never leave the way barred. */
    static void end() {
        if (phase == Phase.NONE) return;
        if (phase == Phase.CUTSCENE || phase == Phase.DEATH) {
            restoreInput();
            thaw();
        }
        phase = Phase.NONE;
        BossGateComponent.open(leftGate);
        BossGateComponent.open(rightGate);
        leftGate = rightGate = null;
        finishArena();
    }

    private static Sfx arenaLoop() {
        return switch (boss) {
            case PIT_LORD -> Sfx.AMB_LORD;
            case MATRIARCH -> Sfx.AMB_MATRIARCH;
            case HYDRA -> Sfx.AMB_HYDRA;
        };
    }

    /** What is special about the boss's arena goes back to plain ground, and the abyss's drone comes back. */
    private static void finishArena() {
        Sfx.ambient(Sfx.AMB_ABYSS);
        if (boss == Boss.MATRIARCH) MatriarchArena.finish();
        if (boss == Boss.HYDRA) HydraArena.finish();
    }

    private static void restoreInput() {
        if (!Game.getPlayer().getComponent(PlayerStatsComponent.class).isDead()) getInput().setProcessInput(true);
    }

    //-----freezing-----
    /** Every demon (the Pit Lord isn't there yet) and every projectile, hazard and portal they made. */
    private static void freezeOthers(Entity except) {
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

    private static void thaw() {
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

    //-----flash (the Lord bursting)-----
    private static void screenFlash() {
        if (flash == null) {
            flash = new Rectangle(getAppWidth(), getAppHeight(), boss == Boss.MATRIARCH ? Color.rgb(225, 205, 255)
                    : boss == Boss.HYDRA ? Color.rgb(255, 205, 150) : Color.rgb(255, 230, 170));
            flash.setMouseTransparent(true);
            addUINode(flash);
        }
        flash.setOpacity(0.85);
    }

    private static void updateFlash(double tpf) {
        if (flash == null) return;
        flash.setOpacity(Math.max(0, flash.getOpacity() - tpf * 0.85 / FLASH_TIME));
        if (flash.getOpacity() <= 0) {
            removeUINode(flash);
            flash = null;
        }
    }

    //-----letterbox-----
    private static void letterbox() {
        if (barTop != null) return;
        double w = getAppWidth();
        barTop = new Rectangle(w, 0, Color.BLACK);
        barBottom = new Rectangle(w, 0, Color.BLACK);
        barBottom.setTranslateY(getAppHeight());
        addUINode(barTop);
        addUINode(barBottom);
    }

    private static void updateLetterbox(double tpf) {
        if (barTop == null) return;
        double target = phase == Phase.CUTSCENE || (phase == Phase.DEATH && deathTime < CONTROL_AFTER - 0.35) ? BAR_H : 0;
        double h = barTop.getHeight();
        h = h < target ? Math.min(target, h + BAR_SPEED * tpf) : Math.max(target, h - BAR_SPEED * tpf);
        barTop.setHeight(h);
        barBottom.setHeight(h);
        barBottom.setTranslateY(getAppHeight() - h);
        if (h <= 0 && target == 0) {
            removeUINode(barTop);
            removeUINode(barBottom);
            barTop = barBottom = null;
        }
    }
}
