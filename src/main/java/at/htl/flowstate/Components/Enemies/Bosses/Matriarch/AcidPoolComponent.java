package at.htl.flowstate.Components.Enemies.Bosses.Matriarch;

import at.htl.flowstate.Assets;
import at.htl.flowstate.Components.Enemies.Bosses.BossArena;
import at.htl.flowstate.Components.Enemies.Bosses.Surface;
import at.htl.flowstate.Components.Player.PlayerStatsComponent;
import at.htl.flowstate.Game;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Visuals.Darkness;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.core.math.FXGLMath;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import com.almasb.fxgl.physics.BoundingShape;
import com.almasb.fxgl.physics.HitBox;
import javafx.geometry.Point2D;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Rectangle;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;

/**
 * A puddle of the Void Matriarch's acid on a ledge or a bridge: it hurts the player standing in it for a few seconds,
 * then dries up. One on a bridge goes when the bridge snaps. It glows in the dark of her last phase.
 */
public class AcidPoolComponent extends Component {
    private final MatriarchArena chasm = BossArena.active().matriarch();
    static final double WIDTH = 130;
    private static final double DAMAGE = 6;
    private static final double HEIGHT = 14;               // the hitbox above the surface
    private static final int FRAMES = 4;
    private static final double TILE_W = 64, TILE_H = 32;  // AcidPool.png 32x16 at x2
    private static final double FPS = 7, GROW = 0.25, FADE = 0.8;
    private static ImagePattern[] patterns;

    private final double life;
    private final Entity bridge;
    private final Rectangle view;
    private double age, sizzle, bubble;

    private AcidPoolComponent(double life, Entity bridge, Rectangle view) {
        this.life = life;
        this.bridge = bridge;
        this.view = view;
    }

    /** A pool centred at x on the surface {x from, x to, top} (clipped to it). */
    public static void spawn(double x, Surface surface, double life) {
        double x1 = Math.max(surface.fromX(), x - WIDTH / 2), x2 = Math.min(surface.toX(), x + WIDTH / 2);
        if (x2 - x1 < 24) return;
        if (patterns == null) {
            patterns = new ImagePattern[FRAMES];
            for (int i = 0; i < FRAMES; i++) {
                patterns[i] = new ImagePattern(Assets.frameImage("AcidPool.png", FRAMES, 2, i), 0, 0, TILE_W, TILE_H, false);
            }
        }
        double top = surface.top();
        Rectangle r = new Rectangle(x2 - x1, TILE_H, patterns[0]);
        r.setTranslateY(HEIGHT - TILE_H + 6);                 // the puddle sits on the surface, its haze above
        Entity e = entityBuilder()
                .at(x1, top - HEIGHT)
                .view(r)
                .bbox(new HitBox(BoundingShape.box(x2 - x1, HEIGHT + 4)))
                .with(new AcidPoolComponent(life, BossArena.active().matriarch().bridgeAt(x, top), r))
                .zIndex(11)
                .buildAndAttach();
        if (Darkness.isOn()) Darkness.light(e, 110, AcidGlobComponent.LIGHT);
    }

    @Override
    public void onAdded() {
        entity.setScaleOrigin(new Point2D(entity.getWidth() / 2, HEIGHT));
    }

    @Override
    public void onUpdate(double tpf) {
        age += tpf;
        if (bridge != null && !bridge.isActive()) {              // its bridge snapped: the acid drips into the void
            Particles.of(Particles.ACID).count(10).angle(90, 40).speed(40, 160)
                    .life(0.7).size(4).gravity(600).at(entity.getCenter());
            entity.removeFromWorld();
            return;
        }
        view.setFill(patterns[(int) (age * FPS) % FRAMES]);
        entity.setScaleX(Math.min(1, 0.3 + 0.7 * age / GROW));
        entity.setOpacity(Math.max(0, Math.min(1, (life - age) / FADE)));
        if ((bubble -= tpf) <= 0) {
            bubble = 0.12;
            Particles.of(Particles.ACID).count(1).angle(-90, 20).speed(20, 70)
                    .life(0.6).size(4).gravity(-50).at(new Point2D(entity.getX() + FXGLMath.randomDouble() * entity.getWidth(), entity.getY() + HEIGHT - 4));
        }
        if ((sizzle -= tpf) <= 0) {
            sizzle = 0.7;
            Sfx.ACID_SIZZLE.at(entity.getCenter(), 0.5 * entity.getOpacity(), 1.0);
        }
        Entity player = Game.getPlayer();
        if (age > GROW && entity.isColliding(player)) {
            player.getComponent(PlayerStatsComponent.class).takeDamage(DAMAGE, entity, false);
        }
        if (age >= life) entity.removeFromWorld();
    }
}
