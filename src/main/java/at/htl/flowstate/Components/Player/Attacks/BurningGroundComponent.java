package at.htl.flowstate.Components.Player.Attacks;

import at.htl.flowstate.Components.Enemies.EnemyStatsComponent;
import at.htl.flowstate.Combat.Combat;
import com.almasb.fxgl.core.math.FXGLMath;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import com.almasb.fxgl.physics.BoundingShape;
import com.almasb.fxgl.physics.HitBox;
import at.htl.flowstate.Assets;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Visuals.Particles;
import javafx.geometry.Point2D;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Rectangle;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;

/** Fire Ball fire patch: enemies standing in it get Burn. */
public class BurningGroundComponent extends Component {
    private static final double BURN_DURATION = 3.0;
    private static final double HEIGHT = 14;

    private final double duration;
    private double left;

    private BurningGroundComponent(double duration) {
        this.duration = duration;
        this.left = duration;
    }

    /** Spawns a patch centered at x on the ground below y. Does nothing over a pit. */
    public static void spawn(double x, double y, double width, double duration) {
        double groundY = Combat.groundYBelow(x, y);
        if (Double.isNaN(groundY)) return;
        entityBuilder()
                .at(x - width / 2, groundY - HEIGHT)
                .view(flames(width))
                .bbox(new HitBox(BoundingShape.box(width, HEIGHT + 20)))
                .with(new BurningGroundComponent(duration))
                .zIndex(11)
                .buildAndAttach();
    }

    private static final int FRAMES = 4;
    private static final double TILE = 32;
    private static final double FPS = 10;
    private static ImagePattern[] patterns;
    private double animTime = 0;
    private double crackleTimer = 0;
    private static final double CRACKLE_INTERVAL = 0.5;

    private static Rectangle flames(double width) {
        if (patterns == null) {
            patterns = new ImagePattern[FRAMES];
            for (int i = 0; i < FRAMES; i++) {
                patterns[i] = new ImagePattern(Assets.frameImage("BurningGround.png", FRAMES, 2, i), 0, 0, TILE, TILE, false);
            }
        }
        Rectangle r = new Rectangle(width, TILE, patterns[0]);
        r.setTranslateY(HEIGHT - TILE + 4); // flames stand on the ground, roots slightly sunk in
        return r;
    }

    @Override
    public void onUpdate(double tpf) {
        animTime += tpf;
        Rectangle r = (Rectangle) entity.getViewComponent().getChildren().get(0);
        r.setFill(patterns[(int) (animTime * FPS) % FRAMES]);
        if (FXGLMath.randomDouble() < tpf * 6) {
            Particles.of(Particles.EMBER).count(1).angle(-90, 20).speed(40, 120)
                    .life(0.7).size(4).gravity(-60).at(new Point2D(entity.getX() + FXGLMath.randomDouble() * r.getWidth(), entity.getY()));
        }
        for (Entity e : Combat.enemies()) {
            if (e.isColliding(entity)) e.getComponent(EnemyStatsComponent.class).burn(BURN_DURATION);
        }
        left -= tpf;
        entity.setOpacity(Math.min(1, left / Math.min(1, duration)));
        crackleTimer -= tpf;
        if (crackleTimer <= 0) {
            crackleTimer = CRACKLE_INTERVAL;
            Sfx.CRACKLE.at(entity.getCenter(), entity.getOpacity(), 1);
        }
        if (left <= 0) entity.removeFromWorld();
    }
}
