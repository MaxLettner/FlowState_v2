package at.htl.flowstate.Generation;

import at.htl.flowstate.Factories.LevelFactory;
import com.almasb.fxgl.core.math.FXGLMath;
import com.almasb.fxgl.entity.SpawnData;
import javafx.scene.paint.Color;

import static com.almasb.fxgl.dsl.FXGL.spawn;

/**
 * Structures replace a normal ground segment. Each spawner places its own ground
 * at currentY and returns the x where the next segment starts.
 *
 * Reachability: a plain jump (600 up, gravity 800) rises ~225px, so every
 * challenge step rises at most 180px and gaps stay under ~200px.
 */
public class StructureGeneration {
    private static final double CHALLENGE_CHANCE = 0.035;
    private static final double RUIN_CHANCE = 0.05;

    private static final double PLATFORM_H = 50;
    private static final double CHEST_W = 80, CHEST_H = 50;
    /** About one chest in six is a Mimic, once the run is past the first stretch. */
    private static final double MIMIC_CHANCE = 1 / 6.0;
    private static final double MIMIC_FROM = 6000;
    /** Testing: {@code FLOWSTATE_MIMICS=1} makes every chest a Mimic. */
    private static final boolean ALL_MIMICS = "1".equals(System.getenv("FLOWSTATE_MIMICS"));

    //ruins sink a little into the ground so they don't float on uneven crust
    private static final double RUIN_SINK = 6;

    public StructureGeneration() { }

    public Double trySpawn(double x, double groundY) {
        double roll = Math.random();
        if (roll < CHALLENGE_CHANCE) return spawnChallenge(x, groundY);
        if (roll < CHALLENGE_CHANCE + RUIN_CHANCE) return spawnRuin(x, groundY);
        return null;
    }

    private double spawnChallenge(double x, double y) {
        return switch (FXGLMath.random(0, 5)) {
            case 0 -> spawnChestStructure(x, y);
            case 1 -> spawnZigzagTower(x, y);
            case 2 -> spawnStaircase(x, y);
            case 3 -> spawnSkyBridge(x, y);
            case 4 -> spawnPillarHop(x, y);
            default -> spawnPyramid(x, y);
        };
    }

    //-----CHALLENGES-----
    private double spawnChestStructure(double x, double y) {
        spawnChest(x + 10, y - CHEST_H);
        LevelGeneration.spawnGroundSegment(x, y, 100);
        return x + 100;
    }

    /** Four small blocks zigzagging up, chest on the top one. */
    private double spawnZigzagTower(double x, double y) {
        spawnAirPlatform(x, y - 170, 60);
        spawnAirPlatform(x + 200, y - 340, 60);
        spawnAirPlatform(x, y - 510, 60);
        spawnAirPlatform(x + 200, y - 680, 110);
        spawnChest(x + 215, y - 680 - CHEST_H);

        LevelGeneration.spawnGroundSegment(x, y, 400);
        return x + 400;
    }

    /** A staircase of floating steps going up and to the right, then a drop back down. */
    private double spawnStaircase(double x, double y) {
        int steps = FXGLMath.random(4, 6);
        double stepX = 170, rise = 150;
        for (int i = 0; i < steps; i++) {
            spawnAirPlatform(x + 80 + i * stepX, y - rise * (i + 1), 100);
        }
        double topX = x + 80 + (steps - 1) * stepX;
        spawnChest(topX + 10, y - rise * steps - CHEST_H);

        double w = topX + 100 + 200 - x;
        LevelGeneration.spawnGroundSegment(x, y, w);
        return x + w;
    }

    /** Climb two steps, then cross a high wavy bridge of narrow platforms; reward at the far end. */
    private double spawnSkyBridge(double x, double y) {
        spawnAirPlatform(x + 60, y - 160, 90);
        spawnAirPlatform(x + 230, y - 320, 90);

        double px = x + 420;
        int hops = FXGLMath.random(4, 6);
        for (int i = 0; i < hops; i++) {
            double h = 320 + (i % 2 == 0 ? 60 : -20) + FXGLMath.random(-20, 20);
            spawnAirPlatform(px, y - h, FXGLMath.random(50, 80));
            px += FXGLMath.random(170, 200);
        }
        spawnAirPlatform(px, y - 340, 140);
        spawnChest(px + 30, y - 340 - CHEST_H);

        double w = px + 140 + 100 - x;
        LevelGeneration.spawnGroundSegment(x, y, w);
        return x + w;
    }

    /**
     * Rock pillars of rising height growing out of the ground. The notch between two pillars sits
     * at the previous pillar's height, so falling in never walls the player off (each rise is 110).
     */
    private double spawnPillarHop(double x, double y) {
        double cx = x;
        LevelGeneration.spawnGroundSegment(cx, y, 120);
        cx += 120;
        int pillars = FXGLMath.random(3, 5);
        for (int i = 1; i <= pillars; i++) {
            double top = y - 110 * i;
            double pw = i == pillars ? 140 : FXGLMath.random(70, 100);
            LevelGeneration.spawnGroundSegment(cx, top, pw);
            if (i == pillars) spawnChest(cx + 30, top - CHEST_H);
            cx += pw;
            double gap = FXGLMath.random(120, 170);
            //notch floor at the previous pillar's height; after the last pillar drop back to the ground
            double notchY = i == pillars ? y : y - 110 * (i - 1) + 40;
            LevelGeneration.spawnGroundSegment(cx, notchY, gap);
            cx += gap;
        }
        return cx;
    }

    /** A floating stepped pyramid: wide base, narrowing tiers, chest on the peak. */
    private double spawnPyramid(double x, double y) {
        double[] widths = {420, 280, 150};
        double cx = x + 100 + widths[0] / 2;
        for (int i = 0; i < widths.length; i++) {
            spawnAirPlatform(cx - widths[i] / 2, y - 170 * (i + 1), widths[i]);
        }
        spawnChest(cx - CHEST_W / 2, y - 170 * widths.length - CHEST_H);

        double w = widths[0] + 200;
        LevelGeneration.spawnGroundSegment(x, y, w);
        return x + w;
    }

    //-----AMBIENT-----
    /** A ruin standing on a flat stretch of ground, purely visual. */
    private double spawnRuin(double x, double y) {
        double w = LevelFactory.RUIN_WIDTH + FXGLMath.random(60, 160);
        LevelGeneration.spawnGroundSegment(x, y, w);
        double rx = x + (w - LevelFactory.RUIN_WIDTH) / 2;
        spawn("ruin", new SpawnData(rx, y - LevelFactory.RUIN_HEIGHT + RUIN_SINK)
                .put("frame", FXGLMath.random(0, LevelFactory.RUIN_FRAMES - 1))
                .put("flip", Math.random() < 0.5));
        return x + w;
    }

    //-----HELPERS-----
    private void spawnAirPlatform(double x, double y, double width) {
        spawn("platform", new SpawnData(x, y)
                .put("color", Color.SADDLEBROWN)
                .put("width", width)
                .put("height", PLATFORM_H));
    }

    private void spawnChest(double x, double y) {
        if (ALL_MIMICS || x >= MIMIC_FROM && Math.random() < MIMIC_CHANCE) {
            spawn("mimic", new SpawnData(x, y - 2));      // same box, same spot: it can't be told apart
            return;
        }
        spawn("chest", new SpawnData(x, y)
                .put("width", CHEST_W)
                .put("height", CHEST_H));
    }
}
