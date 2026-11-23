package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Components.Player.PlayerMovementComponent;
import at.htl.flowstate.Components.Player.PlayerStatsComponent;
import at.htl.flowstate.Game;
import at.htl.flowstate.Generation.LevelGeneration;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Visuals.Darkness;
import at.htl.flowstate.Visuals.Impact;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.entity.Entity;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

import static com.almasb.fxgl.dsl.FXGL.getGameWorld;

/**
 * The Void Matriarch's arena (BossArena runs the gates, camera and cutscenes; this is what is special about hers).
 * <pre>
 *   0 ........ 400 ............................. 1240 ......... 1920   (x from the arena's left edge)
 *   left ledge |        void chasm               | right ledge
 *              |  bridge A ~~~~   ~~~~ bridge B   |     (150 px up)
 *              |        ~~~~ bridge C ~~~~        |     (300 px up)
 * </pre>
 * Until the fight the chasm is solid ground, so the player can walk to the right ledge, where the camera locks.
 * In the intro the floor crumbles into the void and she spins the three bridges; when she dies the floor rises again.
 * Falling into the chasm costs {@link #FALL_DAMAGE} and puts the player back on the nearest ledge.
 */
public final class MatriarchArena {
    public static final double CHASM_FROM = 400, CHASM_TO = 1240;
    /** The bridge slots: x from, x to (arena px), height above the floor. */
    private static final double[][] SLOTS = {{470, 770, 150}, {870, 1170, 150}, {600, 1040, 300}};
    private static final double FALL_DAMAGE = 15;
    private static final double CATCH_BELOW = 60;      // the player's top this far below the floor: caught
    private static final Color BRIDGE_LIGHT = Color.rgb(120, 70, 220);

    private static double lockX, groundY;
    private static Entity floor;
    private static final Entity[] bridges = new Entity[SLOTS.length];
    private static boolean open;                        // the chasm is open (floor gone)

    private MatriarchArena() {}

    public static void reset() {
        floor = null;
        open = false;
        for (int i = 0; i < bridges.length; i++) bridges[i] = null;
    }

    /** LevelGeneration laid the arena: {@code chasmFloor} is the ground over the chasm (crumbles in the intro). */
    public static void plan(double lockX, double groundY, Entity chasmFloor) {
        MatriarchArena.lockX = lockX;
        MatriarchArena.groundY = groundY;
        floor = chasmFloor;
        open = false;
    }

    public static double lockX() { return lockX; }
    public static double groundY() { return groundY; }

    //-----the intro and the end-----
    /** The floor over the chasm breaks up and falls into the void. */
    static void crumble() {
        if (floor != null && floor.isActive()) {
            double y = floor.getY();
            for (double x = floor.getX(); x < floor.getRightX(); x += 70) {
                Particles.burst(new Point2D(x + 35, y + 10), Particles.DUST, 6, 90, 50, 80, 300, 1.1, 10, 900);
                Particles.burst(new Point2D(x + 35, y + 20), Particles.VOID, 3, -90, 70, 60, 220, 0.9, 5, -100);
            }
            floor.removeFromWorld();
        }
        floor = null;
        open = true;
        Impact.shake(26);
        Sfx.STONE_CRACK.at(new Point2D(lockX + (CHASM_FROM + CHASM_TO) / 2, groundY), 1, 0.6);
        Sfx.BRUTE_SLAM.at(new Point2D(lockX + (CHASM_FROM + CHASM_TO) / 2, groundY), 1, 0.45);
    }

    /** Spins every missing bridge; each is solid after {@code weave} s. @return how many were spun */
    static int weaveBridges(double weave) {
        int n = 0;
        for (int i = 0; i < SLOTS.length; i++) if (weave(i, weave)) n++;
        return n;
    }

    /** Spins one missing bridge (the one nearest x). @return false if none was missing */
    static boolean weaveOne(double x, double weave) {
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < SLOTS.length; i++) {
            if (alive(i)) continue;
            double d = Math.abs(lockX + (SLOTS[i][0] + SLOTS[i][1]) / 2 - x);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best >= 0 && weave(best, weave);
    }

    private static boolean weave(int i, double weave) {
        if (alive(i)) return false;
        bridges[i] = ThreadBridgeComponent.spawn(lockX + SLOTS[i][0], lockX + SLOTS[i][1], groundY - SLOTS[i][2], weave);
        if (Darkness.isOn()) lightBridge(bridges[i]);
        return true;
    }

    private static boolean alive(int i) {
        return bridges[i] != null && bridges[i].isActive();
    }

    static int missingBridges() {
        int n = 0;
        for (int i = 0; i < SLOTS.length; i++) if (!alive(i)) n++;
        return n;
    }

    /** Faint light along each bridge once the arena is dark, so the player can still find their footing. */
    static void lightBridges() {
        for (int i = 0; i < SLOTS.length; i++) if (alive(i)) lightBridge(bridges[i]);
    }

    private static void lightBridge(Entity b) {
        double w = b.getWidth();
        for (double f = 0.2; f < 0.9; f += 0.3) {
            final double k = f;
            Darkness.light(() -> b.isActive() ? new Point2D(b.getX() + w * k, b.getY()) : null, 110, BRIDGE_LIGHT);
        }
    }

    /** The fight is over (or gone): the bridges and loose silk go, the floor rises out of the void again. */
    static void finish() {
        for (int i = 0; i < bridges.length; i++) {
            if (alive(i)) bridges[i].getComponent(ThreadBridgeComponent.class).snap();
            bridges[i] = null;
        }
        getGameWorld().getEntitiesByComponent(SilkHazardComponent.class).forEach(Entity::removeFromWorld);
        getGameWorld().getEntitiesByComponent(AcidGlobComponent.class).forEach(Entity::removeFromWorld);
        getGameWorld().getEntitiesByComponent(AcidPoolComponent.class).forEach(Entity::removeFromWorld);
        if (open) {
            open = false;
            double x = lockX + CHASM_FROM, w = CHASM_TO - CHASM_FROM;
            floor = LevelGeneration.spawnGroundSegment(x, groundY, w);
            for (double px = x; px < x + w; px += 60) {
                Particles.burst(new Point2D(px + 30, groundY + 4), Particles.DUST, 4, -90, 40, 60, 260, 0.9, 9, 600);
            }
            Impact.shake(14);
            Sfx.STONE_CRACK.at(new Point2D(x + w / 2, groundY), 0.9, 0.8);
        }
    }

    //-----queries-----
    /** The bridge the entity stands on, or null. */
    static ThreadBridgeComponent bridgeUnder(Entity e) {
        for (Entity b : bridges) {
            if (b == null || !b.isActive()) continue;
            ThreadBridgeComponent c = b.getComponent(ThreadBridgeComponent.class);
            if (c.carries(e)) return c;
        }
        return null;
    }

    static List<ThreadBridgeComponent> solidBridges() {
        List<ThreadBridgeComponent> out = new ArrayList<>();
        for (Entity b : bridges) {
            if (b == null || !b.isActive()) continue;
            ThreadBridgeComponent c = b.getComponent(ThreadBridgeComponent.class);
            if (c.isSolid()) out.add(c);
        }
        return out;
    }

    /** Surfaces something can stand on: {x from, x to, top}, ledges first. */
    static List<double[]> surfaces() {
        List<double[]> out = new ArrayList<>();
        out.add(new double[]{lockX + BossGateComponent.WIDTH, lockX + CHASM_FROM, groundY});
        out.add(new double[]{lockX + CHASM_TO, lockX + BossArena.WIDTH - BossGateComponent.WIDTH, groundY});
        for (ThreadBridgeComponent b : solidBridges()) {
            Entity e = b.getEntity();
            out.add(new double[]{e.getX(), e.getRightX(), e.getY()});
        }
        return out;
    }

    /** The surface at x whose top is at or below y (the first one something dropping from y would meet), or null. */
    static double[] surfaceUnder(double x, double y) {
        double[] best = null;
        for (double[] s : surfaces()) {
            if (x < s[0] || x > s[1] || s[2] < y) continue;
            if (best == null || s[2] < best[2]) best = s;
        }
        return best;
    }

    /** The solid bridge whose top is at {@code top} under x, or null (a ledge). */
    static Entity bridgeAt(double x, double top) {
        for (ThreadBridgeComponent b : solidBridges()) {
            Entity e = b.getEntity();
            if (x >= e.getX() && x <= e.getRightX() && Math.abs(e.getY() - top) < 1) return e;
        }
        return null;
    }

    /**
     * Where something falling from (x, y) with this half width comes down: the highest surface below it that it
     * overlaps, else the closest one by x (it scrabbles over to it on the way down). {x, top}.
     */
    static double[] landing(double x, double y, double halfWidth) {
        double[] best = null;
        for (double[] s : surfaces()) {
            if (s[2] < y) continue;
            if (x + halfWidth * 0.5 < s[0] || x - halfWidth * 0.5 > s[1]) continue;
            if (best == null || s[2] < best[1]) best = new double[]{x, s[2]};
        }
        if (best != null) return best;
        double bestD = Double.MAX_VALUE;
        for (double[] s : surfaces()) {
            if (s[2] < y) continue;
            double cx = Math.max(s[0] + halfWidth, Math.min(s[1] - halfWidth, x));
            if (s[1] - s[0] < 2 * halfWidth) cx = (s[0] + s[1]) / 2;
            double d = Math.abs(cx - x);
            if (d < bestD) {
                bestD = d;
                best = new double[]{cx, s[2]};
            }
        }
        return best;
    }

    /** The player fell into the chasm: hurt, and back on the nearest ledge. @return true if caught */
    public static boolean catchFall(Entity player) {
        if (!open || player.getY() < groundY + CATCH_BELOW) return false;
        PlayerStatsComponent stats = player.getComponent(PlayerStatsComponent.class);
        if (stats.isDead()) return false;
        double x = player.getCenter().getX();
        double leftEdge = lockX + CHASM_FROM, rightEdge = lockX + CHASM_TO;
        double to = Math.abs(x - leftEdge) < Math.abs(x - rightEdge) ? leftEdge - 90 : rightEdge + 50;
        Particles.burst(player.getCenter().add(0, -40), Particles.VOID, 20, -90, 60, 100, 400, 0.8, 6, 200);
        player.getComponent(PlayerMovementComponent.class).teleport(new Point2D(to, groundY - player.getHeight() - 4));
        stats.takeDamage(FALL_DAMAGE, null, false);
        Sfx.FALL.play(0.6, 1.3);
        Particles.burst(player.getCenter(), Particles.VOID, 20, -90, 180, 80, 300, 0.6, 5, 300);
        return true;
    }
}
