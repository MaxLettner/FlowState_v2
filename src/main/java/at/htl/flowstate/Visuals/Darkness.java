package at.htl.flowstate.Visuals;

import at.htl.flowstate.Components.Player.Attacks.BurningGroundComponent;
import at.htl.flowstate.Components.Player.Attacks.PlayerProjectileComponent;
import at.htl.flowstate.Game;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.app.scene.Viewport;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.effect.BlendMode;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static com.almasb.fxgl.dsl.FXGL.getGameScene;
import static com.almasb.fxgl.dsl.FXGL.getGameWorld;

/**
 * The Void Matriarch's last phase: the arena goes dark. A light map is multiplied over the world: near-black
 * everywhere except soft pools of light around the slime, around fire (fireballs, burning ground) and around the
 * lights the fight registers ({@link #light}: her eyes, the silk bridges, the mites).
 * <p>
 * It sits in the content root right after the foreground, so it is scaled with the game and the HUD stays lit.
 */
public final class Darkness {
    private static final double FADE_IN = 1.6, FADE_OUT = 0.8;
    private static final double PLAYER_LIGHT = 270;
    private static final double MAX_OPACITY = 0.94;
    private static final Color DARK = Color.rgb(10, 6, 18);

    /** A light that follows something: where it is (world px) and how far it reaches; null once it's gone. */
    public record Light(Supplier<Point2D> at, double radius, Color color) {}

    private static final Group map = new Group();
    private static final Rectangle base = new Rectangle(1920, 1080, DARK);
    private static final List<Light> lights = new ArrayList<>();
    private static final List<Circle> pool = new ArrayList<>();
    private static boolean on;
    private static double level;      // 0 (off) .. 1 (fully dark)

    private Darkness() {}

    /** Once, from Game: puts the light map into the content root at {@code index}. */
    public static void install(ObservableList<Node> contentChildren, int index) {
        if (!contentChildren.contains(map)) contentChildren.add(Math.min(index, contentChildren.size()), map);
        map.setMouseTransparent(true);
        map.setBlendMode(BlendMode.MULTIPLY);
        if (!map.getChildren().contains(base)) map.getChildren().add(base);
        reset();
    }

    /** A new game: lights out is over. */
    public static void reset() {
        on = false;
        level = 0;
        lights.clear();
        map.setVisible(false);
    }

    public static void setOn(boolean dark) {
        on = dark;
    }

    public static boolean isOn() {
        return on;
    }

    /** A light that lasts while {@code at} returns a point. */
    public static void light(Supplier<Point2D> at, double radius, Color color) {
        lights.add(new Light(at, radius, color));
    }

    public static void light(Entity e, double radius, Color color) {
        light(() -> e.isActive() ? e.getCenter() : null, radius, color);
    }

    /** Every frame, from Game. */
    public static void update(double tpf) {
        level = on ? Math.min(1, level + tpf / FADE_IN) : Math.max(0, level - tpf / FADE_OUT);
        map.setVisible(level > 0);
        if (level <= 0) return;
        map.setOpacity(level * MAX_OPACITY);

        Viewport vp = getGameScene().getViewport();
        Point2D cam = new Point2D(vp.getX(), vp.getY());
        int used = 0;
        used = put(used, Game.getPlayer().getCenter().subtract(cam), PLAYER_LIGHT, Color.rgb(255, 214, 170));
        for (Entity e : getGameWorld().getEntitiesByComponent(BurningGroundComponent.class)) {
            used = put(used, e.getCenter().subtract(cam), Math.max(160, e.getWidth() * 0.9), Color.rgb(255, 170, 90));
        }
        for (Entity e : getGameWorld().getEntitiesByComponent(PlayerProjectileComponent.class)) {
            double r = e.getComponent(PlayerProjectileComponent.class).getLight();
            if (r > 0) used = put(used, e.getCenter().subtract(cam), r, Color.rgb(255, 180, 100));
        }
        for (int i = lights.size() - 1; i >= 0; i--) {
            Light l = lights.get(i);
            Point2D p = l.at().get();
            if (p == null) {
                lights.remove(i);
                continue;
            }
            used = put(used, p.subtract(cam), l.radius(), l.color());
        }
        for (int i = used; i < pool.size(); i++) pool.get(i).setVisible(false);
    }

    private static int put(int i, Point2D screen, double radius, Color color) {
        if (screen.getX() < -radius || screen.getX() > 1920 + radius || screen.getY() < -radius || screen.getY() > 1080 + radius) return i;
        Circle c;
        if (i < pool.size()) {
            c = pool.get(i);
        } else {
            c = new Circle();
            c.setBlendMode(BlendMode.ADD);
            pool.add(c);
            map.getChildren().add(c);
        }
        if (c.getRadius() != radius || !color.equals(c.getUserData())) {
            c.setRadius(radius);
            c.setUserData(color);
            c.setFill(new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                    new Stop(0, color), new Stop(0.45, color.deriveColor(0, 1, 0.55, 1)), new Stop(1, Color.BLACK)));
        }
        c.setCenterX(screen.getX());
        c.setCenterY(screen.getY());
        c.setVisible(true);
        return i + 1;
    }
}
