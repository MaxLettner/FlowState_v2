package at.htl.flowstate.Components.Enemies.Bosses;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SurfaceTest {
    @Test
    void testSpansItsWholeWidthIncludingTheEdges() {
        Surface s = new Surface(100, 400, 50);
        for (double x = 0; x <= 500; x += 0.5) {
            Assertions.assertEquals(x >= 100 && x <= 400, s.spans(x), "x = " + x);
        }
    }
}
