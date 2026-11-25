package at.htl.flowstate.Generation;

import at.htl.flowstate.Components.Enemies.Bosses.BossArena;
import at.htl.flowstate.Components.Enemies.Bosses.Hydra.HydraArena;
import at.htl.flowstate.Components.Enemies.Bosses.Matriarch.MatriarchArena;
import at.htl.flowstate.DevFlags;
import com.almasb.fxgl.core.math.FXGLMath;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.SpawnData;
import javafx.scene.paint.Color;

import static com.almasb.fxgl.dsl.FXGL.spawn;

public class LevelGeneration {

    private final StructureGeneration structureGeneration;
    private final EnemyGeneration enemyGeneration;

    //the floor of each ground segment fills from its top surface all the way
    //down to DEEP_FLOOR which is twice the window height well off-screen
    //so the player never sees the bottom edge
    public static final double DEEP_FLOOR = 1500;

    //surface decorations (GroundSurface.png: 6 frames of 24x24 art, drawn at x2)
    private static final int DECOR_FRAMES = 6;
    private static final double DECOR_SIZE = 48;

    //terrain parameters
    private static final double BASE_Y = 885;  //the base level the terrain hovers around
    private static final double MIN_Y = 735;  //highest the terrain can rise
    private static final double MAX_Y = 935; //lowest the terrain can sink before its a wall
    private static final double MIN_STEP = 12; //height changes smaller than this are flattened out

    //no structures before this x, so the player always spawns on plain ground
    private static final double SAFE_START = 800;

    //how far ahead the world is generated
    private static final double SPAWN_DISTANCE = 1400;

    //the Pit Lord's arena: a long empty flat run-up, the arena (one screen), then more flat ground past the
    //right gate, long enough that nothing generated while the player is boxed in reaches past it
    private static final double ARENA_RUNWAY = 2400;
    private static final double ARENA_MARGIN = SPAWN_DISTANCE + 200;
    private static final double ARENA_SEGMENT = 400;

    //generation state
    private double lastGeneratedX = 0;
    private double currentY = BASE_Y; //current terrain surface height

    //terrain drift gives hills their momentum so they rise and fall naturally
    //rather than randomly jumping around every segment
    //positive drift = terrain going lower = downhill
    //negative drift = going higher = uphill
    private double terrainDrift = 0;

    //how many more segments the current slope should continue before reconsidering
    private int slopeSegmentsLeft = 0;


    //-----INIT-----
    public LevelGeneration() {
        structureGeneration = new StructureGeneration();
        enemyGeneration = new EnemyGeneration(DevFlags.ONLY_DEMONS, DevFlags.ALL_ELITES);
    }


    //-----GENERATON-----
    public void generateLevel(double playerX) {
        while (playerX + SPAWN_DISTANCE > lastGeneratedX) {
            spawnNextSegment();
        }
    }

    private void spawnNextSegment() {
        //the start (player spawns at x = 300) is always plain ground
        boolean safeStart = lastGeneratedX < SAFE_START;

        if (!safeStart && enemyGeneration.bossDue(lastGeneratedX)) {
            spawnArena();
            return;
        }

        Double structureEnd = safeStart ? null : structureGeneration.trySpawn(lastGeneratedX, currentY);
        if (structureEnd != null) {
            lastGeneratedX = structureEnd;
            return;
        }

        //segment witdh
        double segmentW = FXGLMath.random(180, 420);

        //update slope
        currentY = nextTerrainY(segmentW);

        spawnGroundSegment(lastGeneratedX, currentY, segmentW);
        enemyGeneration.trySpawn(lastGeneratedX, currentY, segmentW);
        lastGeneratedX += segmentW;
    }

    /**
     *a boss arena: flat ground at the current height with no structures and no demons on it;
     *BossArena closes it once the camera reaches lockX. The Void Matriarch's has a chasm in it: until the fight
     *the chasm is covered by one plain floor slab (no decorations) that crumbles in her intro; the Tar Hydra's
     *has its tar lake under such a slab (it sinks in the Hydra's intro)
     */
    private void spawnArena() {
        BossArena.Boss boss = enemyGeneration.nextBoss();
        double start = lastGeneratedX;
        double lockX = start + ARENA_RUNWAY;
        double end = lockX + BossArena.WIDTH + ARENA_MARGIN;
        boolean hydra = boss == BossArena.Boss.HYDRA;
        double chasmFrom = lockX + (hydra ? HydraArena.LAKE_FROM : MatriarchArena.CHASM_FROM);
        double chasmTo = lockX + (hydra ? HydraArena.LAKE_TO : MatriarchArena.CHASM_TO);
        Entity chasmFloor = null;
        for (double x = start; x < end; ) {
            double w = Math.min(ARENA_SEGMENT, end - x);
            if (boss == BossArena.Boss.MATRIARCH || hydra) {
                if (x < chasmFrom && x + w > chasmFrom) w = chasmFrom - x;
                if (Math.abs(x - chasmFrom) < 0.5) {
                    chasmFloor = spawn("platform", new SpawnData(x, currentY)
                            .put("color", Color.DARKSLATEGRAY)
                            .put("width", chasmTo - chasmFrom)
                            .put("height", DEEP_FLOOR - currentY));
                    x = chasmTo;
                    continue;
                }
            }
            spawnGroundSegment(x, currentY, w);
            x += w;
        }
        lastGeneratedX = end;
        terrainDrift = 0;
        slopeSegmentsLeft = 0;
        BossArena.plan(lockX, currentY, boss, enemyGeneration.nextBossHpScale(), chasmFloor);
        enemyGeneration.bossPlaced(end);
    }

    /**
     *Computes the next terrain Y using a momentum based drift system
     *
     *the terrain has a drift value that represents how fast and in which
     *direction it is currently sloping the drift persists for several segments
     *before randomly being reconsidered, producing smooth hills and valleys
     *rather than chaotic random noise
     *
     *most of the time the drift is tiny (relatively flat) sometimes a bigger
     *slope kicks in for a hill or valley
     */
    private double nextTerrainY(double segmentW) {
        if (slopeSegmentsLeft <= 0) {
            //pick a new slope direction and length
            //70% of the time: nearly flat
            //30% of the time: a real slope
            double roll = FXGLMath.randomDouble();

            if (roll < 0.70) {
                //nearly flat
                int drift =FXGLMath.random(-12, 12);
                if(drift < 3 && drift > -3) drift = 0;
                terrainDrift = drift + (BASE_Y - currentY) * 0.05;
                slopeSegmentsLeft = FXGLMath.random(3, 7);
            } else if (roll < 0.90) {
                //gentle slope
                int drift = FXGLMath.random(-30, 30);
                if(drift < 3 && drift > -3) drift = 0;
                terrainDrift = drift;
                slopeSegmentsLeft = FXGLMath.random(4, 9);
            } else {
                //hill or valley
                int drift = FXGLMath.random(-60, 60);
                if(drift < 3 && drift > -3) drift = 0;
                terrainDrift = drift;
                slopeSegmentsLeft = FXGLMath.random(3, 6);
            }

            //if near a boundary slope back towards BASE_Y
            if (currentY < MIN_Y + 30)  terrainDrift = Math.abs(terrainDrift);  //force downward (higher Y)
            if (currentY > MAX_Y - 30)  terrainDrift = -Math.abs(terrainDrift); //force upward (lower Y)
        }

        slopeSegmentsLeft--;

        double newY = currentY + terrainDrift;

        //hard clamp
        newY = Math.max(MIN_Y, Math.min(MAX_Y, newY));

        //a step of only a few pixels just looks like a glitch: keep the ground flat instead
        if (Math.abs(newY - currentY) < MIN_STEP) newY = currentY;

        return newY;
    }

    /**
     *spawns a single ground segment: a solid rectangle whose top surface is at
     *the given Y and whose bottom extends to DEEP_FLOOR
     *this way no matter how the camera angles the player never sees the bottom
     */




    //-----HELPERS-----
    public static Entity spawnGroundSegment(double x, double y, double width) {
        double height = DEEP_FLOOR - y;
        Entity e = spawn("platform", new SpawnData(x, y)
                .put("color", Color.DARKSLATEGRAY)
                .put("width", width)
                .put("height", height));
        spawnDecor(x, y, width);
        return e;
    }

    //bones, thorns, skulls on stakes ... standing on the surface, purely visual
    private static void spawnDecor(double x, double y, double width) {
        if (width < DECOR_SIZE * 2) return;
        int count = (int) (width / 220) + (FXGLMath.randomDouble() < 0.5 ? 1 : 0);
        for (int i = 0; i < count; i++) {
            double dx = FXGLMath.randomDouble() * (width - DECOR_SIZE);
            spawn("decor", new SpawnData(x + dx, y - DECOR_SIZE + 4)
                    .put("frame", FXGLMath.random(0, DECOR_FRAMES - 1)));
        }
    }

    //-----GETTERS-----

    public double getBaseY() {
        return BASE_Y;
    }
}
