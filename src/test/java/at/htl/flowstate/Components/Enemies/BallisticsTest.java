package at.htl.flowstate.Components.Enemies;

import javafx.geometry.Point2D;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class BallisticsTest {
    private static final double SPEED = 700;
    private static final double EPS = 1e-6;

    @Test
    void testLaunchHasTheGivenSpeed() {
        for (Point2D to : targets()) {
            Point2D v = Ballistics.velocity(Point2D.ZERO, to, SPEED, Ballistics.GRAVITY);
            Assertions.assertNotNull(v, "in reach: " + to);
            Assertions.assertEquals(SPEED, v.magnitude(), EPS * SPEED, "speed for " + to);
        }
    }

    @Test
    void testArcPassesThroughTheTarget() {
        for (Point2D to : targets()) {
            Point2D v = Ballistics.velocity(Point2D.ZERO, to, SPEED, Ballistics.GRAVITY);
            double t = to.getX() / v.getX();                        // when it is above the target
            double y = v.getY() * t + 0.5 * Ballistics.GRAVITY * t * t;   // y grows downwards, like the screen
            Assertions.assertTrue(t > 0, "flies towards " + to);
            Assertions.assertEquals(to.getY(), y, 1e-6 * (1 + Math.abs(to.getY())), "height at " + to);
        }
    }

    @Test
    void testOutOfReachGivesNull() {
        double maxRange = SPEED * SPEED / Ballistics.GRAVITY;     // 45° on flat ground
        Assertions.assertNull(Ballistics.velocity(Point2D.ZERO, new Point2D(maxRange * 1.1, 0), SPEED, Ballistics.GRAVITY));
        Assertions.assertNull(Ballistics.velocity(Point2D.ZERO, new Point2D(0, -SPEED * SPEED), SPEED, Ballistics.GRAVITY));
    }

    @Test
    void testStartingPointDoesNotMatter() {
        Point2D from = new Point2D(1234, -56);
        Point2D to = new Point2D(300, -80);
        Assertions.assertEquals(Ballistics.velocity(Point2D.ZERO, to, SPEED, Ballistics.GRAVITY),
                Ballistics.velocity(from, from.add(to), SPEED, Ballistics.GRAVITY));
    }

    /** Targets left and right, above and below, all in reach of SPEED. */
    private static Point2D[] targets() {
        return new Point2D[]{new Point2D(300, 0), new Point2D(-300, 0), new Point2D(400, -150),
                new Point2D(-250, 200), new Point2D(50, -20), new Point2D(500, 300)};
    }
}
