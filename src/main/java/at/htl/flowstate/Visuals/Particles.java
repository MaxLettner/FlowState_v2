package at.htl.flowstate.Visuals;

import com.almasb.fxgl.core.math.FXGLMath;
import com.almasb.fxgl.entity.component.Component;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;

/** Tiny square "pixel" particles: hit sparks, splats, dust and embers. Purely visual. */
public final class Particles {
    public static final Color[] SPARK = {Color.WHITE, Color.web("#fff3a0"), Color.web("#ffc23d")};
    public static final Color[] DUST = {Color.web("#c4a77a"), Color.web("#9b7f57"), Color.web("#e8d2a8")};
    public static final Color[] EMBER = {Color.web("#ffc23d"), Color.web("#ff7a1f"), Color.web("#d1361a")};
    public static final Color[] ICE = {Color.WHITE, Color.web("#a2e0f7"), Color.web("#62b6df")};
    public static final Color[] POISON = {Color.web("#a8f046"), Color.web("#5fc22a")};
    public static final Color[] BLOOD = {Color.web("#e63946"), Color.web("#b51f2e")};

    private Particles() {}

    /**
     * Spawns {@code count} particles flying out from {@code at}.
     * @param angle   main direction in degrees (0 = right, -90 = up)
     * @param spread  +- degrees around the main direction (180 = all around)
     */
    public static void burst(Point2D at, Color[] colors, int count, double angle, double spread,
                             double speedMin, double speedMax, double life, double size, double gravity) {
        for (int i = 0; i < count; i++) {
            double a = Math.toRadians(angle + FXGLMath.random(-spread, spread));
            double speed = FXGLMath.random(speedMin, speedMax);
            Point2D v = new Point2D(Math.cos(a) * speed, Math.sin(a) * speed);
            double s = Math.max(2, Math.round(size * FXGLMath.random(0.7, 1.2) / 2) * 2);
            spawn(at, v, colors[FXGLMath.random(0, colors.length - 1)], s, life * FXGLMath.random(0.7, 1.2), gravity, 20);
        }
    }

    public static void spawn(Point2D at, Point2D velocity, Color color, double size, double life, double gravity, int zIndex) {
        Rectangle r = new Rectangle(size, size, color);
        r.setTranslateX(-size / 2);
        r.setTranslateY(-size / 2);
        entityBuilder()
                .at(at)
                .view(r)
                .with(new Particle(velocity, life, gravity))
                .zIndex(zIndex)
                .buildAndAttach();
    }

    /** Hit sparks in the direction of the hit. */
    public static void hit(Point2D at, int direction) {
        burst(at, SPARK, 7, direction >= 0 ? 0 : 180, 55, 150, 380, 0.22, 4, 300);
    }

    /** Slime splat when an enemy dies. */
    public static void splat(Point2D at, Color[] colors) {
        burst(at, colors, 18, -90, 80, 120, 420, 0.7, 6, 900);
    }

    public static void landingDust(Point2D feet) {
        burst(feet, DUST, 4, 180, 12, 40, 110, 0.35, 4, -40);
        burst(feet, DUST, 4, 0, 12, 40, 110, 0.35, 4, -40);
    }

    private static class Particle extends Component {
        private Point2D velocity;
        private final double life;
        private final double gravity;
        private double age;

        Particle(Point2D velocity, double life, double gravity) {
            this.velocity = velocity;
            this.life = life;
            this.gravity = gravity;
        }

        @Override
        public void onUpdate(double tpf) {
            age += tpf;
            velocity = velocity.add(0, gravity * tpf).multiply(Math.max(0, 1 - 1.5 * tpf));
            entity.translate(velocity.multiply(tpf));
            double t = age / life;
            entity.setOpacity(t < 0.6 ? 1 : Math.max(0, 1 - (t - 0.6) / 0.4));
            if (age >= life) entity.removeFromWorld();
        }
    }
}
