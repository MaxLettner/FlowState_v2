package at.htl.flowstate.Components.Enemies.Bosses;

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
 * A wall of chained hellfire that boxes the player into the Pit Lord's arena (BossArena) while it lives. A static
 * terrain body (it collides like the ground); far taller than the screen.
 */
public class BossGateComponent extends Component {
    public static final double WIDTH = 48;
    private static final double W = WIDTH, H = 1600;
    private static final int FRAMES = 4;
    private static final double TILE_W = 48, TILE_H = 128;      // BossGate.png 24x64 at x2
    private static final double FPS = 10;
    private static ImagePattern[] fire, web, glass;

    private final ImagePattern[] patterns;
    private double time;

    private BossGateComponent(ImagePattern[] patterns) {
        this.patterns = patterns;
    }

    private static ImagePattern[] load(String file) {
        ImagePattern[] p = new ImagePattern[FRAMES];
        for (int i = 0; i < FRAMES; i++) p[i] = new ImagePattern(Assets.frameImage(file, FRAMES, 2, i), 0, 0, TILE_W, TILE_H, false);
        return p;
    }

    /** A gate of hellfire standing on groundY at x (its left edge). */
    public static Entity spawn(double x, double groundY) {
        return spawn(x, groundY, false);
    }

    /** A gate standing on groundY at x (its left edge): hellfire, or a curtain of void silk (the Void Matriarch's). */
    public static Entity spawn(double x, double groundY, boolean silk) {
        return spawn(x, groundY, silk, false);
    }

    /** As above; {@code glass}: the Hollow Ember's violet glass shards (void particles, no silk sounds). */
    public static Entity spawn(double x, double groundY, boolean silk, boolean glassGate) {
        if (fire == null) fire = load("BossGate.png");
        if (web == null) web = load("WebGate.png");
        if (glassGate && glass == null) glass = load("HollowGate.png");
        ImagePattern[] patterns = glassGate ? glass : silk ? web : fire;
        boolean voidy = silk || glassGate;
        FixtureDef fd = new FixtureDef();
        fd.getFilter().categoryBits = 0x0001;           // terrain, like LevelFactory's platforms
        fd.getFilter().maskBits = (short) 0xFFFF;
        PhysicsComponent physics = new PhysicsComponent();
        physics.setBodyType(BodyType.STATIC);
        physics.setFixtureDef(fd);

        Rectangle wall = new Rectangle(W, H, patterns[0]);
        wall.setEffect(new DropShadow(30, voidy ? Color.rgb(140, 77, 255, 0.8) : Color.rgb(255, 110, 30, 0.8)));
        if (silk) {
            Sfx.SILK_SPIN.at(new Point2D(x, groundY), 1, 0.8);
            Sfx.GATE_SLAM.at(new Point2D(x, groundY), 0.5, 1.4);
        } else {
            Sfx.GATE_SLAM.at(new Point2D(x, groundY));
        }
        Particles.of(voidy ? Particles.VOID : Particles.EMBER).count(30).angle(-90, 60).speed(150, 500)
                .life(0.8).size(6).gravity(300).at(new Point2D(x + W / 2, groundY - 10));
        return entityBuilder()
                .at(x, groundY - H)
                .bbox(BoundingShape.box(W, H))
                .view(wall)
                .with(physics)
                .with(new PlatformIdentifierComponent())
                .with(new BossGateComponent(patterns))
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
        boolean silk = gate.getComponent(BossGateComponent.class).patterns != fire;
        for (int k = 0; k < 8; k++) {
            Particles.of(silk ? Particles.VOID : Particles.EMBER).count(8).angle(-90, 80).speed(80, 300)
                    .life(0.8).size(6).gravity(200).at(new Point2D(gate.getCenter().getX(), bottom - 60 - k * 90));
        }
        Sfx.GATE_SLAM.at(gate.getCenter(), 0.8, 1.3);
        gate.removeFromWorld();
    }
}
