package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Components.Player.PlayerMovementComponent;
import at.htl.flowstate.Components.Player.PlayerStatsComponent;
import at.htl.flowstate.Game;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Visuals.Impact;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.SpawnData;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import static com.almasb.fxgl.dsl.FXGL.*;

/**
 * The Pit Lord's arena. LevelGeneration lays a long flat stretch and {@link #plan plans} the arena on it; when the
 * camera reaches the arena it stops scrolling, a hellfire gate slams down at the right edge of the screen, then one
 * at the left edge, boxing the player in. In a short cutscene (no player control, letterbox bars) the Pit Lord
 * drops in behind the player and roars; then the fight starts. Killing it opens both gates and frees the camera.
 */
public final class BossArena {
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

    private enum Phase { NONE, PENDING, CUTSCENE, FIGHT }

    private static Phase phase = Phase.NONE;
    private static double lockX, groundY, time, landedAt;
    private static boolean leftClosed, lordSpawned;
    private static Entity leftGate, rightGate;
    private static Rectangle barTop, barBottom;

    private BossArena() {}

    /** A new game. */
    public static void reset() {
        phase = Phase.NONE;
        leftGate = rightGate = null;
        barTop = barBottom = null;    // the UI was cleared with the old game
    }

    /** The ground from lockX to lockX + WIDTH is flat at groundY; the arena closes when the camera gets there. */
    public static void plan(double lockX, double groundY) {
        BossArena.lockX = lockX;
        BossArena.groundY = groundY;
        phase = Phase.PENDING;
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

    /** Where the camera may go: it locks onto the arena once it gets there and stays until the fight is over. */
    public static double clampCamera(double cameraX) {
        if (phase == Phase.PENDING && cameraX >= lockX) close();
        if (phase == Phase.CUTSCENE || phase == Phase.FIGHT) return Math.min(cameraX, lockX);
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
        rightGate = BossGateComponent.spawn(lockX + WIDTH - BossGateComponent.WIDTH, groundY);
        Impact.shake(16);
        Sfx.BOSS_ROAR.at(new Point2D(lockX + WIDTH, groundY), 0.35, 0.6);   // something stirs
        letterbox();
    }

    /** Every frame, from Game. */
    public static void update(double tpf) {
        updateLetterbox(tpf);
        if (phase != Phase.CUTSCENE) return;
        time += tpf;
        if (!leftClosed && time >= LEFT_GATE_AT) {
            leftClosed = true;
            leftGate = BossGateComponent.spawn(lockX, groundY);
            Impact.shake(16);
        }
        if (!lordSpawned && time >= DROP_AT) {
            lordSpawned = true;
            DemonType lord = DemonType.LORD;
            spawn(lord.spawnName(), new SpawnData(lockX + LORD_X, groundY - lord.height - 2 - DROP_HEIGHT));
            Sfx.BOSS_ROAR.at(new Point2D(lockX + LORD_X, groundY), 0.6, 0.8);
            // the player hears it coming and turns round
            movement().faceAttack(-1, 3);
        }
        if ((landedAt >= 0 && time >= landedAt + CONTROL_AFTER_LANDING) || time >= MAX_CUTSCENE) startFight();
    }

    /** The Pit Lord hit the ground. */
    static void landed(Entity lord) {
        if (phase != Phase.CUTSCENE) return;
        landedAt = time;
        int dir = lord.getCenter().getX() < Game.getPlayer().getCenter().getX() ? -1 : 1;
        movement().faceAttack(dir, CONTROL_AFTER_LANDING);
    }

    private static void startFight() {
        phase = Phase.FIGHT;
        restoreInput();
    }

    /** The Pit Lord is gone (dead, or removed some other way): never leave the way barred. */
    static void end() {
        if (phase == Phase.NONE) return;
        if (phase == Phase.CUTSCENE) restoreInput();
        phase = Phase.NONE;
        BossGateComponent.open(leftGate);
        BossGateComponent.open(rightGate);
        leftGate = rightGate = null;
    }

    private static void restoreInput() {
        if (!Game.getPlayer().getComponent(PlayerStatsComponent.class).isDead()) getInput().setProcessInput(true);
    }

    private static PlayerMovementComponent movement() {
        return Game.getPlayer().getComponent(PlayerMovementComponent.class);
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
        double target = phase == Phase.CUTSCENE ? BAR_H : 0;
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
