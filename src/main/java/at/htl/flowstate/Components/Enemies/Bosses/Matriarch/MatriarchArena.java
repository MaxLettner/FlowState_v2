package at.htl.flowstate.Components.Enemies.Bosses.Matriarch;

import at.htl.flowstate.Components.Enemies.Bosses.BossArena;
import at.htl.flowstate.Components.Enemies.Bosses.BossGateComponent;
import at.htl.flowstate.Components.Enemies.Bosses.Surface;
import at.htl.flowstate.Components.Player.PlayerMovementComponent;
import at.htl.flowstate.Components.Player.PlayerStatsComponent;
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

    private final double lockX, groundY;
    private Entity floor;
    private final Entity[] bridges = new Entity[SLOTS.length];
    private boolean open;                        // the chasm is open (floor gone)

    /** {@code chasmFloor}: the ground LevelGeneration laid over the chasm (crumbles in the intro). */
    public MatriarchArena(double lockX, double groundY, Entity chasmFloor) {
        this.lockX = lockX;
        this.groundY = groundY;
        floor = chasmFloor;
    }

    double lockX() { return lockX; }
    double groundY() { return groundY; }

    //-----the intro and the end-----
    /** The floor over the chasm breaks up and falls into the void. */
    public void crumble() {
        if (floor != null && floor.isActive()) {
            double y = floor.getY();
            for (double x = floor.getX(); x < floor.getRightX(); x += 70) {
                Particles.of(Particles.DUST).count(6).angle(90, 50).speed(80, 300)
                        .life(1.1).size(10).gravity(900).at(new Point2D(x + 35, y + 10));
                Particles.of(Particles.VOID).count(3).angle(-90, 70).speed(60, 220)
                        .life(0.9).size(5).gravity(-100).at(new Point2D(x + 35, y + 20));
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
    public int weaveBridges(double weave) {
        int n = 0;
        for (int i = 0; i < SLOTS.length; i++) if (weave(i, weave)) n++;
        return n;
    }

    /** Spins one missing bridge (the one nearest x). @return false if none was missing */
    boolean weaveOne(double x, double weave) {
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

    private boolean weave(int i, double weave) {
        if (alive(i)) return false;
        bridges[i] = ThreadBridgeComponent.spawn(lockX + SLOTS[i][0], lockX + SLOTS[i][1], groundY - SLOTS[i][2], weave);
        if (Darkness.isOn()) lightBridge(bridges[i]);
        return true;
    }

    private boolean alive(int i) {
        return bridges[i] != null && bridges[i].isActive();
    }

    int missingBridges() {
        int n = 0;
        for (int i = 0; i < SLOTS.length; i++) if (!alive(i)) n++;
        return n;
    }

    /** Faint light along each bridge once the arena is dark, so the player can still find their footing. */
    void lightBridges() {
        for (int i = 0; i < SLOTS.length; i++) if (alive(i)) lightBridge(bridges[i]);
    }

    private void lightBridge(Entity b) {
        double w = b.getWidth();
        for (double f = 0.2; f < 0.9; f += 0.3) {
            final double k = f;
            Darkness.light(() -> b.isActive() ? new Point2D(b.getX() + w * k, b.getY()) : null, 110, BRIDGE_LIGHT);
        }
    }

    /** The fight is over (or gone): the bridges and loose silk go, the floor rises out of the void again. */
    public void finish() {
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
                Particles.of(Particles.DUST).count(4).angle(-90, 40).speed(60, 260)
                        .life(0.9).size(9).gravity(600).at(new Point2D(px + 30, groundY + 4));
            }
            Impact.shake(14);
            Sfx.STONE_CRACK.at(new Point2D(x + w / 2, groundY), 0.9, 0.8);
        }
    }

    //-----queries-----
    /** The bridge the entity stands on, or null. */
    ThreadBridgeComponent bridgeUnder(Entity e) {
        for (Entity b : bridges) {
            if (b == null || !b.isActive()) continue;
            ThreadBridgeComponent c = b.getComponent(ThreadBridgeComponent.class);
            if (c.carries(e)) return c;
        }
        return null;
    }

    List<ThreadBridgeComponent> solidBridges() {
        List<ThreadBridgeComponent> out = new ArrayList<>();
        for (Entity b : bridges) {
            if (b == null || !b.isActive()) continue;
            ThreadBridgeComponent c = b.getComponent(ThreadBridgeComponent.class);
            if (c.isSolid()) out.add(c);
        }
        return out;
    }

    /** Surfaces something can stand on, ledges first. */
    List<Surface> surfaces() {
        List<Surface> out = new ArrayList<>();
        out.add(new Surface(lockX + BossGateComponent.WIDTH, lockX + CHASM_FROM, groundY));
        out.add(new Surface(lockX + CHASM_TO, lockX + BossArena.WIDTH - BossGateComponent.WIDTH, groundY));
        for (ThreadBridgeComponent b : solidBridges()) {
            Entity e = b.getEntity();
            out.add(new Surface(e.getX(), e.getRightX(), e.getY()));
        }
        return out;
    }

    /** The surface at x whose top is at or below y (the first one something dropping from y would meet), or null. */
    Surface surfaceUnder(double x, double y) {
        Surface best = null;
        for (Surface s : surfaces()) {
            if (!s.spans(x) || s.top() < y) continue;
            if (best == null || s.top() < best.top()) best = s;
        }
        return best;
    }

    /** The solid bridge whose top is at {@code top} under x, or null (a ledge). */
    Entity bridgeAt(double x, double top) {
        for (ThreadBridgeComponent b : solidBridges()) {
            Entity e = b.getEntity();
            if (x >= e.getX() && x <= e.getRightX() && Math.abs(e.getY() - top) < 1) return e;
        }
        return null;
    }

    /**
     * Where something falling from (x, y) with this half width comes down: the highest surface below it that it
     * overlaps, else the closest one by x (it scrabbles over to it on the way down): (x, top).
     */
    Point2D landing(double x, double y, double halfWidth) {
        Point2D best = null;
        for (Surface s : surfaces()) {
            if (s.top() < y) continue;
            if (x + halfWidth * 0.5 < s.fromX() || x - halfWidth * 0.5 > s.toX()) continue;
            if (best == null || s.top() < best.getY()) best = new Point2D(x, s.top());
        }
        if (best != null) return best;
        double bestD = Double.MAX_VALUE;
        for (Surface s : surfaces()) {
            if (s.top() < y) continue;
            double cx = Math.max(s.fromX() + halfWidth, Math.min(s.toX() - halfWidth, x));
            if (s.toX() - s.fromX() < 2 * halfWidth) cx = (s.fromX() + s.toX()) / 2;
            double d = Math.abs(cx - x);
            if (d < bestD) {
                bestD = d;
                best = new Point2D(cx, s.top());
            }
        }
        return best;
    }

    /** The player fell into the chasm: hurt, and back on the nearest ledge. @return true if caught */
    public boolean catchFall(Entity player) {
        if (!open || player.getY() < groundY + CATCH_BELOW) return false;
        PlayerStatsComponent stats = player.getComponent(PlayerStatsComponent.class);
        if (stats.isDead()) return false;
        double x = player.getCenter().getX();
        double leftEdge = lockX + CHASM_FROM, rightEdge = lockX + CHASM_TO;
        double to = Math.abs(x - leftEdge) < Math.abs(x - rightEdge) ? leftEdge - 90 : rightEdge + 50;
        Particles.of(Particles.VOID).count(20).angle(-90, 60).speed(100, 400)
                .life(0.8).size(6).gravity(200).at(player.getCenter().add(0, -40));
        player.getComponent(PlayerMovementComponent.class).teleport(new Point2D(to, groundY - player.getHeight() - 4));
        stats.takeDamage(FALL_DAMAGE, null, false);
        Sfx.FALL.play(0.6, 1.3);
        Particles.of(Particles.VOID).count(20).angle(-90, 180).speed(80, 300)
                .life(0.6).size(5).gravity(300).at(player.getCenter());
        return true;
    }
}
