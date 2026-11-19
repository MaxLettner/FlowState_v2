package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Assets;
import at.htl.flowstate.Combat.Combat;
import at.htl.flowstate.Components.Player.PlayerStatsComponent;
import at.htl.flowstate.Game;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import com.almasb.fxgl.physics.BoundingShape;
import com.almasb.fxgl.physics.HitBox;
import javafx.geometry.Point2D;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Rectangle;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;

/** Brimstone Hurler splash: a puddle of magma that hurts the player standing in it (enemies don't mind). */
public class MagmaPoolComponent extends Component {
    private static final double DAMAGE = 7;
    private static final double HEIGHT = 12;
    private static final int FRAMES = 4;
    private static final double TILE_W = 64, TILE_H = 32;   // MagmaPool.png 32x16 at x2
    private static final double FPS = 8;
    private static ImagePattern[] patterns;

    private final Entity player = Game.getPlayer();
    private final double duration;
    private double left, animTime, crackleTimer;

    private MagmaPoolComponent(double duration) {
        this.duration = duration;
        this.left = duration;
    }

    /** A pool centred at x on the ground below y; nothing over a pit. */
    public static void spawn(double x, double y, double width, double duration) {
        double groundY = Combat.groundYBelow(x, y);
        if (Double.isNaN(groundY)) return;
        if (patterns == null) {
            patterns = new ImagePattern[FRAMES];
            for (int i = 0; i < FRAMES; i++) {
                patterns[i] = new ImagePattern(Assets.frameImage("MagmaPool.png", FRAMES, 2, i), 0, 0, TILE_W, TILE_H, false);
            }
        }
        Rectangle r = new Rectangle(width, TILE_H, patterns[0]);
        r.setTranslateY(HEIGHT - TILE_H + 4);    // the puddle sits in the ground, flames above it
        entityBuilder()
                .at(x - width / 2, groundY - HEIGHT)
                .view(r)
                .bbox(new HitBox(BoundingShape.box(width, HEIGHT + 6)))
                .with(new MagmaPoolComponent(duration))
                .zIndex(11)
                .buildAndAttach();
    }

    @Override
    public void onUpdate(double tpf) {
        animTime += tpf;
        Rectangle r = (Rectangle) entity.getViewComponent().getChildren().get(0);
        r.setFill(patterns[(int) (animTime * FPS) % FRAMES]);
        if (Math.random() < tpf * 5) {
            Particles.burst(new Point2D(entity.getX() + Math.random() * r.getWidth(), entity.getY() + 4),
                    Particles.EMBER, 1, -90, 25, 30, 90, 0.6, 4, -40);
        }
        if (entity.isColliding(player)) player.getComponent(PlayerStatsComponent.class).takeDamage(DAMAGE, entity, false);
        crackleTimer -= tpf;
        if (crackleTimer <= 0) {
            crackleTimer = 0.6;
            Sfx.CRACKLE.at(entity.getCenter(), 0.6 * entity.getOpacity(), 0.8);
        }
        left -= tpf;
        entity.setOpacity(Math.min(1, left / Math.min(0.8, duration)));
        if (left <= 0) entity.removeFromWorld();
    }
}
