package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Assets;
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

import java.util.List;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;
import static com.almasb.fxgl.dsl.FXGL.getGameWorld;

/**
 * A puddle of burning tar on a pillar or a ledge (where an aimed glob landed): it hurts the player standing in it for a
 * few seconds, then burns out. At most {@link #MAX_POOLS} burn at once, and a glob landing in one feeds it instead of
 * adding another. One on a ledge goes under when the tar floods the ledges.
 */
public class TarPoolComponent extends Component {
    static final double WIDTH = 90;
    private static final int MAX_POOLS = 3;              // more and the oldest burns out
    private static final double DAMAGE = 3;
    private static final double HEIGHT = 18;               // the hitbox above the surface
    private static final int FRAMES = 4;
    private static final double TILE_W = 64, TILE_H = 32;  // TarFirePool.png 32x16 at x2
    private static final double FPS = 8, GROW = 0.25, FADE = 0.8;
    private static ImagePattern[] patterns;

    private final double life;
    private final Rectangle view;
    private double age, crackle, ember;

    private TarPoolComponent(double life, Rectangle view) {
        this.life = life;
        this.view = view;
    }

    /** A pool centred at x on the surface {x from, x to, top} (clipped to it). */
    public static void spawn(double x, double[] surface, double life) {
        double x1 = Math.max(surface[0], x - WIDTH / 2), x2 = Math.min(surface[1], x + WIDTH / 2);
        if (x2 - x1 < 24) return;
        List<Entity> pools = getGameWorld().getEntitiesByComponent(TarPoolComponent.class);
        for (Entity p : pools) {                         // landing in a burning pool feeds it instead of adding one
            if (Math.abs(p.getBottomY() - surface[2] - 4) < 8 && p.getRightX() > x1 && p.getX() < x2) {
                p.getComponent(TarPoolComponent.class).age = Math.min(p.getComponent(TarPoolComponent.class).age, GROW);
                return;
            }
        }
        if (pools.size() >= MAX_POOLS) {
            Entity oldest = pools.get(0);
            for (Entity p : pools) if (p.getComponent(TarPoolComponent.class).age > oldest.getComponent(TarPoolComponent.class).age) oldest = p;
            oldest.removeFromWorld();
        }
        if (patterns == null) {
            patterns = new ImagePattern[FRAMES];
            for (int i = 0; i < FRAMES; i++) {
                patterns[i] = new ImagePattern(Assets.frameImage("TarFirePool.png", FRAMES, 2, i), 0, 0, TILE_W, TILE_H, false);
            }
        }
        double top = surface[2];
        Rectangle r = new Rectangle(x2 - x1, TILE_H, patterns[0]);
        r.setTranslateY(HEIGHT - TILE_H + 8);                 // the puddle sits on the surface, its flames above
        entityBuilder()
                .at(x1, top - HEIGHT)
                .view(r)
                .bbox(new HitBox(BoundingShape.box(x2 - x1, HEIGHT + 4)))
                .with(new TarPoolComponent(life, r))
                .zIndex(11)
                .buildAndAttach();
    }

    @Override
    public void onAdded() {
        entity.setScaleOrigin(new Point2D(entity.getWidth() / 2, HEIGHT));
    }

    @Override
    public void onUpdate(double tpf) {
        age += tpf;
        if (entity.getBottomY() > HydraArena.surface() + 4) {       // the tar rose over it
            entity.removeFromWorld();
            return;
        }
        view.setFill(patterns[(int) (age * FPS) % FRAMES]);
        entity.setScaleX(Math.min(1, 0.3 + 0.7 * age / GROW));
        entity.setOpacity(Math.max(0, Math.min(1, (life - age) / FADE)));
        if ((ember -= tpf) <= 0) {
            ember = 0.1;
            Particles.burst(new Point2D(entity.getX() + Math.random() * entity.getWidth(), entity.getY() + HEIGHT - 6),
                    Particles.EMBER, 1, -90, 20, 30, 90, 0.6, 4, -60);
        }
        if ((crackle -= tpf) <= 0) {
            crackle = 0.6;
            Sfx.CRACKLE.at(entity.getCenter(), 0.8 * entity.getOpacity(), 0.8);
        }
        Entity player = Game.getPlayer();
        if (age > GROW && entity.isColliding(player)) {
            player.getComponent(PlayerStatsComponent.class).takeDamage(DAMAGE, entity, false);
        }
        if (age >= life) entity.removeFromWorld();
    }
}
