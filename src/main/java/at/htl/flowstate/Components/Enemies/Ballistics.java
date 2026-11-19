package at.htl.flowstate.Components.Enemies;

import javafx.geometry.Point2D;

/** Aiming for lobbed enemy projectiles (formerly the Hunter's spear, now the Hurler's magma). */
public final class Ballistics {
    /** Must match Game.initPhysics and BallisticProjectileComponent. */
    public static final double GRAVITY = 800;

    private Ballistics() {}

    /**
     * Launch velocity that lands a projectile thrown at {@code speed} from {@code from} on {@code to}
     * (the faster, flatter of the two arcs), or null when the target is out of reach.
     */
    //DO NOT TOUCH THIS UNLESS YOU KNOW EXACTLY HOW MATH MATHING
    public static Point2D velocity(Point2D from, Point2D to, double speed, double gravity) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double s = speed;
        double g = gravity;

        double a = 0.25 * g * g;
        double b = -(s * s + g * dy);
        double c = dx * dx + dy * dy;

        double discriminant = b * b - 4 * a * c;
        if (discriminant < 0) return null;

        double sqrt = Math.sqrt(discriminant);
        double u1 = (-b + sqrt) / (2 * a);
        double u2 = (-b - sqrt) / (2 * a);

        double u;
        if (u1 > 0 && u2 > 0) u = Math.min(u1, u2);
        else if (u1 > 0) u = u1;
        else if (u2 > 0) u = u2;
        else return null;

        double T = Math.sqrt(u);
        double vx = dx / T;
        double vy = (dy - 0.5 * g * T * T) / T;

        return new Point2D(vx, vy);
    }
}
