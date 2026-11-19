package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Assets;
import at.htl.flowstate.Components.Player.Helpers.DeleteAfterTimeComponent;
import at.htl.flowstate.Game;
import at.htl.flowstate.Visuals.FrameSprite;
import at.htl.flowstate.Visuals.Frames;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import com.almasb.fxgl.physics.BoundingShape;
import com.almasb.fxgl.physics.HitBox;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;

/** Hellfire Cultist orb: slow, turns toward the player a little at a time, fizzles out after a while. */
public class HomingOrbComponent extends Component {
    private static final double SPEED = 220;
    private static final double TURN_RATE = 2.3;     // rad/s
    private static final double LIFE = 4.5;
    private static final double SIZE = 14;
    private static final Color[] FEL = {Color.web("#f4ffd0"), Color.web("#b8ff6a"), Color.web("#5fe83a")};

    private final Entity player = Game.getPlayer();
    private double angle;
    private double age;
    private double trailTimer;

    private HomingOrbComponent(double angle) {
        this.angle = angle;
    }

    public static void spawn(Point2D at, double angle, double damage) {
        Frames frames = Assets.frames("HellfireOrb.png", 4, 2);
        FrameSprite view = new FrameSprite(frames).loop(frames, 12);
        view.setTranslateX(SIZE / 2 - frames.width() / 2);
        view.setTranslateY(SIZE / 2 - frames.height() / 2);
        entityBuilder()
                .at(at.subtract(SIZE / 2, SIZE / 2))
                .view(view)
                .bbox(new HitBox(BoundingShape.box(SIZE, SIZE)))
                .with(new HomingOrbComponent(angle))
                .with(new EnemyProjectileComponent(damage, HomingOrbComponent::fizzle))
                .with(new DeleteAfterTimeComponent(LIFE + 0.1))
                .zIndex(12)
                .buildAndAttach();
    }

    @Override
    public void onAdded() {
        entity.setRotationOrigin(new Point2D(SIZE / 2, SIZE / 2));
    }

    @Override
    public void onUpdate(double tpf) {
        age += tpf;
        Point2D to = player.getCenter().subtract(entity.getCenter());
        double want = Math.atan2(to.getY(), to.getX());
        double diff = Math.atan2(Math.sin(want - angle), Math.cos(want - angle));
        angle += Math.max(-TURN_RATE * tpf, Math.min(TURN_RATE * tpf, diff));
        entity.translate(Math.cos(angle) * SPEED * tpf, Math.sin(angle) * SPEED * tpf);
        entity.setRotation(Math.toDegrees(angle));

        trailTimer -= tpf;
        if (trailTimer <= 0) {
            trailTimer = 0.05;
            Particles.burst(entity.getCenter(), FEL, 1, Math.toDegrees(angle) + 180, 25, 20, 60, 0.4, 4, -30);
        }
        if (age > LIFE - 0.4) entity.setOpacity(Math.max(0, (LIFE - age) / 0.4));
        if (age >= LIFE) entity.removeFromWorld();
    }

    private static void fizzle(Entity orb) {
        Particles.burst(orb.getCenter(), FEL, 8, -90, 180, 60, 180, 0.4, 4, 0);
    }
}
