package at.htl.flowstate.Components.Enemies.Bosses.Matriarch;

import at.htl.flowstate.Components.Player.PlayerMovementComponent;
import at.htl.flowstate.Game;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import com.almasb.fxgl.physics.BoundingShape;
import com.almasb.fxgl.physics.PhysicsComponent;
import com.almasb.fxgl.physics.box2d.dynamics.BodyType;
import com.almasb.fxgl.physics.box2d.dynamics.FixtureDef;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.StrokeLineCap;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;

/**
 * A bridge of void silk across the Void Matriarch's chasm. It is spun in (a faint flicker, then solid), can be
 * {@link #sever severed} (it glows and trembles, then snaps), and the player can jump up through it from below and
 * drop through it holding S.
 * <p>
 * One-way for the player only: the demons stand on a body in their own category, the player on a second body that is
 * switched off while the player is below the strand (or dropping).
 */
public class ThreadBridgeComponent extends Component {
    /** Physics categories: what demons stand on, what the player stands on (Game and EnemyFactory mask them in). */
    public static final short CATEGORY_DEMON_FLOOR = 0x0010, CATEGORY_PLAYER_FLOOR = 0x0020;
    private static final double THICK = 10;
    private static final double SEVER_TELL = 1.0;
    private static final double DROP_TIME = 0.35;
    private static final Color GLOW = Color.rgb(140, 77, 255, 0.45), CORE = Color.rgb(222, 204, 255), HOT = Color.WHITE;

    private final double width;
    private double weaveLeft;
    private double severLeft = -1;
    private double dropLeft;
    private double time;
    private Entity playerFloor;
    private Line glow, core;
    private Group view;
    private boolean solid;

    private ThreadBridgeComponent(double width, double weave) {
        this.width = width;
        this.weaveLeft = weave;
    }

    /** A bridge from x1 to x2 whose top is at y; solid after {@code weave} seconds. */
    public static Entity spawn(double x1, double x2, double y, double weave) {
        double w = x2 - x1;
        return entityBuilder()
                .at(x1, y)
                .bbox(BoundingShape.box(w, THICK))
                .with(staticBody(CATEGORY_DEMON_FLOOR))
                .with(new ThreadBridgeComponent(w, weave))
                .zIndex(9)
                .buildAndAttach();
    }

    private static PhysicsComponent staticBody(short category) {
        FixtureDef fd = new FixtureDef().friction(0);
        fd.getFilter().categoryBits = category;
        fd.getFilter().maskBits = (short) 0xFFFF;
        PhysicsComponent physics = new PhysicsComponent();
        physics.setBodyType(BodyType.STATIC);
        physics.setFixtureDef(fd);
        return physics;
    }

    @Override
    public void onAdded() {
        glow = new Line(0, 2, width, 2);
        glow.setStroke(GLOW);
        glow.setStrokeWidth(7);
        glow.setStrokeLineCap(StrokeLineCap.ROUND);
        core = new Line(0, 2, width, 2);
        core.setStroke(CORE);
        core.setStrokeWidth(2.4);
        core.setStrokeLineCap(StrokeLineCap.ROUND);
        view = new Group(glow, core);
        for (double x = 18; x < width - 10; x += 46 + (x * 7) % 17) {        // beads of void dew along the strand
            Circle bead = new Circle(x, 4 + (x % 3), 2.2, Color.rgb(232, 220, 255, 0.9));
            view.getChildren().add(bead);
        }
        entity.getViewComponent().addChild(view);
        playerFloor = entityBuilder()
                .at(entity.getPosition())
                .bbox(BoundingShape.box(width, THICK))
                .with(staticBody(CATEGORY_PLAYER_FLOOR))
                .buildAndAttach();
        setSolid(false);
        Sfx.SILK_SPIN.at(entity.getCenter(), 0.8, 1.0);
    }

    @Override
    public void onUpdate(double tpf) {
        time += tpf;
        if (weaveLeft > 0) {                                     // being spun: a faint, flickering line
            weaveLeft -= tpf;
            double k = 0.25 + 0.2 * Math.sin(time * 30);
            entity.getViewComponent().setOpacity(k);
            if (weaveLeft <= 0) {
                entity.getViewComponent().setOpacity(1);
                setSolid(true);
                Sfx.THREAD_TWANG.at(entity.getCenter(), 0.7, 1.3);
                Particles.of(Particles.VOID).count(10).angle(-90, 180).speed(40, 140)
                        .life(0.5).size(4).gravity(100).at(entity.getCenter());
            }
            return;
        }
        if (severLeft >= 0) {                                    // about to snap: glows white and trembles
            severLeft -= tpf;
            double k = 1 - Math.max(0, severLeft) / SEVER_TELL;
            core.setStroke(CORE.interpolate(HOT, 0.5 + 0.5 * Math.sin(time * (20 + 30 * k))));
            view.setTranslateY(Math.sin(time * 70) * 2 * k);
            if (severLeft <= 0) {
                snap();
                return;
            }
        }
        updatePlayerFloor(tpf);
    }

    /** The player's body is on while the player is above the strand and not dropping through it. */
    private void updatePlayerFloor(double tpf) {
        Entity player = Game.getPlayer();
        PlayerMovementComponent movement = player.getComponent(PlayerMovementComponent.class);
        double top = entity.getY();
        double bottom = player.getBottomY();
        boolean over = player.getRightX() > entity.getX() && player.getX() < entity.getRightX();
        if (over && Math.abs(bottom - top) < 4 && movement.isGrounded() && movement.isDownHeld()) dropLeft = DROP_TIME;
        dropLeft = Math.max(0, dropLeft - tpf);
        setPlayerFloor(solid && dropLeft <= 0 && bottom <= top + 2);
    }

    private void setSolid(boolean on) {
        solid = on;
        setActive(entity, on);
        setPlayerFloor(on);
    }

    private void setPlayerFloor(boolean on) {
        if (playerFloor != null && playerFloor.isActive()) setActive(playerFloor, on);
    }

    /** Switches a static body on or off, now or as soon as it exists. */
    private static void setActive(Entity e, boolean on) {
        PhysicsComponent physics = e.getComponent(PhysicsComponent.class);
        try {
            if (physics.getBody().isActive() != on) physics.getBody().setActive(on);
        } catch (IllegalStateException notYet) {
            physics.setOnPhysicsInitialized(() -> physics.getBody().setActive(on));
        }
    }

    public boolean isSolid() {
        return solid && severLeft < 0;
    }

    public boolean isSevering() {
        return severLeft >= 0;
    }

    /** Whether the player is standing on this strand. */
    public boolean carries(Entity e) {
        return solid && e.getRightX() > entity.getX() && e.getX() < entity.getRightX()
                && Math.abs(e.getBottomY() - entity.getY()) < 6;
    }

    public double top() {
        return entity.getY();
    }

    /** Glows and trembles for a second, then snaps. */
    public void sever() {
        if (!solid || severLeft >= 0) return;
        severLeft = SEVER_TELL;
        Sfx.THREAD_TWANG.at(entity.getCenter(), 1, 0.8);
    }

    /** Snaps now. */
    public void snap() {
        Sfx.THREAD_SNAP.at(entity.getCenter());
        for (double x = 0; x < width; x += 40) {
            Particles.of(Particles.VOID).count(2).angle(90, 60).speed(40, 160)
                    .life(0.7).size(4).gravity(500).at(new Point2D(entity.getX() + x, entity.getY() + 3));
        }
        entity.removeFromWorld();
    }

    @Override
    public void onRemoved() {
        if (playerFloor != null && playerFloor.isActive()) playerFloor.removeFromWorld();
    }
}
