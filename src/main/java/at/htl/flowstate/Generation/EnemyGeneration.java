package at.htl.flowstate.Generation;

import at.htl.flowstate.Components.Enemies.DemonType;
import at.htl.flowstate.Components.Enemies.EliteComponent;
import at.htl.flowstate.Components.Enemies.PitLordBehaviour;
import com.almasb.fxgl.core.math.FXGLMath;
import com.almasb.fxgl.entity.SpawnData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.almasb.fxgl.dsl.FXGL.spawn;

/**
 * Danger budget: every ground segment adds danger in proportion to its width, faster the further the run
 * goes. When the budget covers the next group (picked at random from the groups unlocked at this distance)
 * the group spawns on that segment and its cost is paid. Early on that means a pack of imps or a hound now
 * and then; later on brutes, cultists, bat swarms and mixed groups, closer together.
 * <p>
 * Single demons can roll an elite affix (more often the further you go), and every so often the Pit Lord
 * blocks the way; nothing else spawns while it lives.
 * <p>
 * Testing: {@code FLOWSTATE_DEMONS=imp,bat} spawns only those demons (all distances, one kind per group;
 * {@code lord} brings the boss right away), {@code FLOWSTATE_ELITES=1} makes every single demon an elite.
 */
public class EnemyGeneration {
    private static final double START_X = 1200;          // the first screen stays empty
    private static final double DANGER_PER_PX = 0.0019;  // at the start; grows with the distance
    private static final double DANGER_GROWTH = 12000;   // +100 % danger every this many px
    private static final double MAX_BUDGET = 9;           // a long empty stretch doesn't store up a horde
    private static final double ELITE_FROM = 9000;        // elite chance grows from 0 here ...
    private static final double ELITE_PER_PX = 1 / 70000.0;
    private static final double ELITE_MAX = 0.22;          // ... up to this
    private static final double BOSS_FIRST = 18000;        // the first Pit Lord, then one every BOSS_EVERY px
    private static final double BOSS_EVERY = 24000;
    private static final double BOSS_MIN_WIDTH = 200;

    /** A spawnable group: demons with a count range, its cost, from which distance on, how often. */
    private record Group(String name, double cost, double unlockX, double weight, Pack... packs) {}

    private record Pack(DemonType type, int min, int max) {}

    private static final List<Group> GROUPS = List.of(
            new Group("imps", 2.5, 0, 3.0, new Pack(DemonType.IMP, 3, 5)),
            new Group("hound", 2.0, 2000, 2.5, new Pack(DemonType.HOUND, 1, 1)),
            new Group("hurler", 2.5, 4000, 2.0, new Pack(DemonType.HURLER, 1, 1)),
            new Group("bats", 3.0, 7000, 2.0, new Pack(DemonType.BAT, 4, 6)),
            new Group("brute", 5.5, 10000, 1.5, new Pack(DemonType.BRUTE, 1, 1)),
            new Group("cultist", 4.0, 13000, 1.5, new Pack(DemonType.CULTIST, 1, 1)),
            new Group("big imps", 4.0, 15000, 1.0, new Pack(DemonType.IMP, 6, 8)),
            new Group("hound pair", 4.0, 15000, 1.0, new Pack(DemonType.HOUND, 2, 2)),
            new Group("brute escort", 8.0, 18000, 1.0, new Pack(DemonType.BRUTE, 1, 1), new Pack(DemonType.HURLER, 1, 1)),
            new Group("coven", 6.5, 20000, 1.0, new Pack(DemonType.CULTIST, 1, 1), new Pack(DemonType.IMP, 3, 4)),
            new Group("night sky", 6.5, 22000, 1.0, new Pack(DemonType.BAT, 5, 7), new Pack(DemonType.HURLER, 1, 1)),
            // wave 2
            new Group("warden", 3.5, 8000, 1.5, new Pack(DemonType.WARDEN, 1, 1)),
            new Group("gargoyles", 4.0, 9000, 1.2, new Pack(DemonType.GARGOYLE, 1, 2)),
            new Group("watcher", 3.5, 11000, 1.2, new Pack(DemonType.WATCHER, 1, 1)),
            new Group("gatekeeper", 5.0, 14000, 0.9, new Pack(DemonType.GATEKEEPER, 1, 1)),
            new Group("priest", 2.0, 14000, 0, new Pack(DemonType.PRIEST, 1, 1)),     // only with an escort (tests: alone)
            new Group("priest + imps", 6.0, 14000, 1.0, new Pack(DemonType.PRIEST, 1, 1), new Pack(DemonType.IMP, 3, 5)),
            new Group("priest + warden", 7.5, 16000, 1.0, new Pack(DemonType.PRIEST, 1, 1), new Pack(DemonType.WARDEN, 1, 1)),
            new Group("phalanx", 8.5, 20000, 1.0, new Pack(DemonType.WARDEN, 2, 2), new Pack(DemonType.HURLER, 1, 1)),
            new Group("watchtower", 8.0, 21000, 1.0, new Pack(DemonType.WATCHER, 2, 2), new Pack(DemonType.GARGOYLE, 1, 1))
    );

    private final List<Group> groups;
    private final boolean testing;
    private final boolean allElite = "1".equals(System.getenv("FLOWSTATE_ELITES"));
    private double nextBossX = BOSS_FIRST;
    private double budget = 1.5;                         // the first group comes soon after the empty start
    private Group next;

    public EnemyGeneration() {
        String only = System.getenv("FLOWSTATE_DEMONS");
        testing = only != null && !only.isBlank();
        if (testing) {
            Set<String> names = Stream.of(only.split(",")).map(s -> s.trim().toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
            groups = new ArrayList<>();
            for (Group g : GROUPS) {
                if (g.packs().length == 1 && names.contains(g.packs()[0].type().name()) && g.unlockX() < 15000) {
                    groups.add(new Group(g.name(), Math.min(g.cost(), 2.5), 0, 1, g.packs()));
                }
            }
            boolean lord = names.contains(DemonType.LORD.name());
            nextBossX = lord ? START_X * 0.6 + 300 : Double.MAX_VALUE;
            if (groups.isEmpty() && !lord) throw new IllegalArgumentException("FLOWSTATE_DEMONS: no demon named in " + only);
            budget = MAX_BUDGET;    // something to look at right away
        } else {
            groups = GROUPS;
        }
    }

    /** Called for every plain ground segment: (x, groundY) is its top left corner. */
    public void trySpawn(double x, double groundY, double width) {
        if (x < (testing ? START_X * 0.6 : START_X)) return;
        if (PitLordBehaviour.current() != null) return;            // the boss fight has the stage
        if (x >= nextBossX && width >= BOSS_MIN_WIDTH) {
            DemonType lord = DemonType.LORD;
            spawn(lord.spawnName(), new SpawnData(x + width / 2 - lord.width / 2, groundY - lord.height - 2));
            nextBossX = testing ? Double.MAX_VALUE : x + BOSS_EVERY;
            budget = 0;
            next = null;
            return;
        }
        if (groups.isEmpty()) return;
        double danger = DANGER_PER_PX * (1 + x / DANGER_GROWTH);
        if (testing) danger *= 1.5;
        budget = Math.min(MAX_BUDGET, budget + width * danger);
        if (next == null) next = pick(x);
        if (next == null || budget < next.cost()) return;
        budget -= next.cost();
        spawnGroup(next, x, groundY, width);
        next = null;
        if (testing && budget >= 2.5) trySpawn(x, groundY, 0);   // a full test budget spends itself on one segment
    }

    private Group pick(double x) {
        double total = 0;
        for (Group g : groups) if (g.unlockX() <= x) total += g.weight();
        double roll = Math.random() * total;
        for (Group g : groups) {
            if (g.unlockX() > x) continue;
            roll -= g.weight();
            if (roll <= 0) return g;
        }
        return null;
    }

    private void spawnGroup(Group group, double x, double groundY, double width) {
        for (Pack pack : group.packs()) {
            DemonType type = pack.type();
            int count = FXGLMath.random(pack.min(), pack.max());
            double eliteChance = allElite ? 1 : Math.min(ELITE_MAX, Math.max(0, (x - ELITE_FROM) * ELITE_PER_PX));
            for (int i = 0; i < count; i++) {
                double room = Math.max(0, width - type.width);
                double sx = x + Math.random() * room;
                // gargoyles fly but start as statues standing on the ground
                boolean airborne = type.flying && type != DemonType.GARGOYLE;
                double sy = airborne ? groundY - 280 - Math.random() * 120 : groundY - type.height - 2;
                SpawnData data = new SpawnData(sx, sy);
                if (pack.max() == 1 && Math.random() < eliteChance) data.put("elite", EliteComponent.random());
                spawn(type.spawnName(), data);
            }
        }
    }
}
