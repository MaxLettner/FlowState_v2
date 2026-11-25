package at.htl.flowstate.Components.Enemies.Bosses.PitLord;

import at.htl.flowstate.Assets;
import at.htl.flowstate.Components.Enemies.BallisticProjectileComponent;
import at.htl.flowstate.Components.Enemies.EnemyProjectileComponent;
import at.htl.flowstate.Components.Enemies.MagmaPoolComponent;
import at.htl.flowstate.Components.Player.Helpers.DeleteAfterTimeComponent;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Visuals.FrameSprite;
import at.htl.flowstate.Visuals.Frames;
import at.htl.flowstate.Visuals.Impact;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import com.almasb.fxgl.physics.BoundingShape;
import com.almasb.fxgl.physics.HitBox;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import javafx.scene.shape.Ellipse;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;
import static com.almasb.fxgl.dsl.FXGL.getGameScene;

/**
 * Pit Lord hellfire rain: a glowing mark on the ground shows where a meteor will land; a moment later it comes
 * down from above the screen and splashes into a short-lived magma pool. This entity is the mark.
 */
public class MeteorComponent extends Component {
    private static final double LEAD = 0.4;             // the mark glows this long before the meteor appears
    private static final double FALL_SPEED = 600;       // initial downward speed (then gravity)
    private static final double GRAVITY = 800;          // BallisticProjectileComponent's
    private static final double RX = 50, RY = 10;
    private static final double SIZE = 28;              // MagmaBall.png drawn at x4
    private static final double POOL_WIDTH = 110, POOL_TIME = 1.6;

    private final double damage;
    private final Entity owner;
    private Ellipse mark;
    private Entity meteor;
    private double age, fallTime;

    private MeteorComponent(double damage, Entity owner) {
        this.damage = damage;
        this.owner = owner;
    }

    /** A meteor that lands centred on x, on the ground at groundY. */
    public static void spawn(double x, double groundY, double damage, Entity owner) {
        MeteorComponent m = new MeteorComponent(damage, owner);
        m.mark = new Ellipse(RX, RY, RX, RY);
        m.mark.setFill(Color.rgb(255, 90, 20, 0.35));
        m.mark.setStroke(Color.rgb(255, 194, 61, 0.9));
        m.mark.setStrokeWidth(2);
        entityBuilder()
                .at(x - RX, groundY - RY - 2)
                .view(m.mark)
                .with(m)
                .zIndex(10)
                .buildAndAttach();
    }

    @Override
    public void onUpdate(double tpf) {
        age += tpf;
        // the mark tightens and brightens until the meteor hits
        double total = LEAD + Math.max(fallTime, 1);
        double k = Math.min(1, age / total);
        mark.setScaleX(1.3 - 0.4 * k);
        mark.setScaleY(1.3 - 0.4 * k);
        mark.setOpacity(0.5 + 0.5 * Math.abs(Math.sin(age * (8 + 14 * k))));
        if (meteor == null && age >= LEAD) launch();
        if (meteor != null) {
            if (meteor.isActive()) {
                Particles.of(Particles.EMBER).count(2).angle(-90, 40).speed(20, 80)
                        .life(0.35).size(5).gravity(-60).at(meteor.getCenter());
            } else {
                entity.removeFromWorld();
                return;
            }
        }
        if (age > 5) entity.removeFromWorld();     // never linger
    }

    private void launch() {
        double x = entity.getX() + RX;
        double groundY = entity.getBottomY();
        double skyY = getGameScene().getViewport().getY() - SIZE - 20;
        double drop = groundY - skyY;
        // drop = v t + g t^2 / 2
        fallTime = (-FALL_SPEED + Math.sqrt(FALL_SPEED * FALL_SPEED + 2 * GRAVITY * drop)) / GRAVITY;
        Frames frames = Assets.frames("MagmaBall.png", 4, 4);
        FrameSprite view = new FrameSprite(frames).loop(frames, 12);
        view.setTranslateX(SIZE / 2 - frames.width() / 2);
        view.setTranslateY(SIZE / 2 - frames.height() / 2);
        meteor = entityBuilder()
                .at(x - SIZE / 2, skyY)
                .view(view)
                .bbox(new HitBox(BoundingShape.box(SIZE, SIZE)))
                .with(new BallisticProjectileComponent(0, FALL_SPEED))
                .with(new EnemyProjectileComponent(damage, MeteorComponent::splash).owner(owner))
                .with(new DeleteAfterTimeComponent(5))
                .zIndex(12)
                .buildAndAttach();
    }

    private static void splash(Entity ball) {
        Point2D c = ball.getCenter();
        Sfx.MAGMA_SPLASH.at(c, 1, 0.8);
        Impact.shake(4);
        Particles.of(Particles.EMBER).count(18).angle(-90, 75).speed(140, 420).life(0.6).size(6).gravity(700).at(c);
        Particles.of(Particles.DUST).count(8).angle(-90, 70).speed(80, 220).life(0.5).size(6).gravity(700).at(c);
        MagmaPoolComponent.spawn(c.getX(), c.getY() - 10, POOL_WIDTH, POOL_TIME);
    }
}
