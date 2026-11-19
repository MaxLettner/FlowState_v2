package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Assets;
import at.htl.flowstate.Components.Identifier.PlatformIdentifierComponent;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import com.almasb.fxgl.physics.BoundingShape;
import com.almasb.fxgl.physics.PhysicsComponent;
import com.almasb.fxgl.physics.box2d.dynamics.BodyType;
import com.almasb.fxgl.physics.box2d.dynamics.FixtureDef;
import javafx.geometry.Point2D;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Rectangle;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;

/**
 * A wall of chained hellfire that bars the way on while the Pit Lord lives, so the fight can't be skipped by
 * running past. A static terrain body (it collides like the ground); far taller than the screen.
 */
public class BossGateComponent extends Component {
    private static final double W = 48, H = 1600;
    private static final int FRAMES = 4;
    private static final double TILE_W = 48, TILE_H = 128;      // BossGate.png 24x64 at x2
    private static final double FPS = 10;
    private static ImagePattern[] patterns;

    private double time;

    /** A gate standing on groundY at x (its left edge). */
    public static Entity spawn(double x, double groundY) {
        if (patterns == null) {
            patterns = new ImagePattern[FRAMES];
            for (int i = 0; i < FRAMES; i++) {
                patterns[i] = new ImagePattern(Assets.frameImage("BossGate.png", FRAMES, 2, i), 0, 0, TILE_W, TILE_H, false);
            }
        }
        FixtureDef fd = new FixtureDef();
        fd.getFilter().categoryBits = 0x0001;           // terrain, like LevelFactory's platforms
        fd.getFilter().maskBits = (short) 0xFFFF;
        PhysicsComponent physics = new PhysicsComponent();
        physics.setBodyType(BodyType.STATIC);
        physics.setFixtureDef(fd);

        Rectangle fire = new Rectangle(W, H, patterns[0]);
        fire.setEffect(new DropShadow(30, Color.rgb(255, 110, 30, 0.8)));
        Sfx.GATE_SLAM.at(new Point2D(x, groundY));
        Particles.burst(new Point2D(x + W / 2, groundY - 10), Particles.EMBER, 30, -90, 60, 150, 500, 0.8, 6, 300);
        return entityBuilder()
                .at(x, groundY - H)
                .bbox(BoundingShape.box(W, H))
                .view(fire)
                .with(physics)
                .with(new PlatformIdentifierComponent())
                .with(new BossGateComponent())
                .zIndex(13)
                .buildAndAttach();
    }

    @Override
    public void onUpdate(double tpf) {
        time += tpf;
        Rectangle r = (Rectangle) entity.getViewComponent().getChildren().get(0);
        r.setFill(patterns[(int) (time * FPS) % FRAMES]);
    }

    /** The boss died: the fire gutters out. */
    public static void open(Entity gate) {
        if (gate == null || !gate.isActive()) return;
        double bottom = gate.getBottomY();
        for (int k = 0; k < 8; k++) {
            Particles.burst(new Point2D(gate.getCenter().getX(), bottom - 60 - k * 90), Particles.EMBER, 8, -90, 80, 80, 300, 0.8, 6, 200);
        }
        Sfx.GATE_SLAM.at(gate.getCenter(), 0.8, 1.3);
        gate.removeFromWorld();
    }
}
