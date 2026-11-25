package at.htl.flowstate.Generation;

import at.htl.flowstate.Components.Enemies.Bosses.BossArena.Boss;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class EnemyGenerationTest {
    @Test
    void testBossesComeRoundInOrder() {
        EnemyGeneration gen = new EnemyGeneration(null, false);
        List<Boss> order = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            order.add(gen.nextBoss());
            gen.bossPlaced(0);
        }
        Assertions.assertEquals(List.of(Boss.PIT_LORD, Boss.MATRIARCH, Boss.HYDRA, Boss.HOLLOW, Boss.PIT_LORD, Boss.MATRIARCH), order);
    }

    @Test
    void testEarlyBossesAreWeaker() {
        EnemyGeneration gen = new EnemyGeneration(null, false);
        List<Double> scales = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            scales.add(gen.nextBossHpScale());
            gen.bossPlaced(0);
        }
        Assertions.assertEquals(List.of(1 / 3.0, 2 / 3.0, 1.0, 1.0), scales);
    }

    @Test
    void testNextBossIsDueOnlyFurtherOn() {
        EnemyGeneration gen = new EnemyGeneration(null, false);
        double first = firstDue(gen, 0);
        Assertions.assertTrue(first > 0);
        gen.bossPlaced(first + 2000);
        Assertions.assertFalse(gen.bossDue(first + 2000));
        Assertions.assertTrue(firstDue(gen, first + 2000) > first + 2000);
    }

    @Test
    void testNamedBossIsTheOnlyOneAtFullHealth() {
        EnemyGeneration gen = new EnemyGeneration("hydra", false);
        for (int i = 0; i < 3; i++) {
            Assertions.assertEquals(Boss.HYDRA, gen.nextBoss());
            Assertions.assertEquals(1.0, gen.nextBossHpScale());
            gen.bossPlaced(0);
        }
    }

    @Test
    void testUnknownDemonNameIsRejected() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new EnemyGeneration("nonsense", false));
    }

    @Test
    void testBlankNameMeansNormalSpawning() {
        Assertions.assertEquals(Boss.PIT_LORD, new EnemyGeneration(" ", false).nextBoss());
    }

    /** The first x (in 100 px steps) from where a boss is due. */
    private static double firstDue(EnemyGeneration gen, double from) {
        for (double x = from; x < from + 1_000_000; x += 100) {
            if (gen.bossDue(x)) return x;
        }
        return Assertions.fail("no boss due within 1,000,000 px of " + from);
    }
}
