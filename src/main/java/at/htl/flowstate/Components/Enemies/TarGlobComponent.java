package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Assets;
import at.htl.flowstate.Components.Player.PlayerStatsComponent;
import at.htl.flowstate.Game;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Visuals.FrameSprite;
import at.htl.flowstate.Visuals.Frames;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import com.almasb.fxgl.physics.BoundingShape;
import com.almasb.fxgl.physics.HitBox;
import javafx.geometry.Point2D;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;

/**
 * A glob of burning tar, lobbed in an arc by the Tar Hydra's spitting heads. It lands on the first pillar or ledge it
 * comes down on; the aimed one of a volley leaves a {@link TarPoolComponent burning pool} there. A direct hit hurts
 * the player and sets them alight. Globs that miss everything sink into the lake.
 */
public class TarGlobComponent extends Component {
    private static final double SIZE = 22;                 // hitbox (world px)
    private static final double DAMAGE = 6, BURN = 0.75;

    private final double poolLife;
    private double vx, vy;
    private double trail;

    private TarGlobComponent(double vx, double vy, double poolLife) {
        this.vx = vx;
        this.vy = vy;
        this.poolLife = poolLife;
    }

    /** Lobs a glob from {@code from} onto {@code to} at about {@code speed}. */
    public static Entity lob(Point2D from, Point2D to, double speed, double poolLife) {
        Point2D v = Ballistics.velocity(from, to, speed, Ballistics.GRAVITY);
        if (v == null) v = Ballistics.velocity(from, to, speed * 1.6, Ballistics.GRAVITY);
        if (v == null) v = new Point2D((to.getX() - from.getX()) / 1.1, 0);
        Frames frames = Assets.frames("TarGlob.png", 4, 2);
        FrameSprite view = new FrameSprite(frames).loop(frames, 12);
        view.setTranslateX(SIZE / 2 - frames.width() / 2);
        view.setTranslateY(SIZE / 2 - frames.height() / 2);
        return entityBuilder()
                .at(from.subtract(SIZE / 2, SIZE / 2))
                .view(view)
                .bbox(new HitBox(BoundingShape.box(SIZE, SIZE)))
                .with(new TarGlobComponent(v.getX(), v.getY(), poolLife))
                .zIndex(13)
                .buildAndAttach();
    }

    @Override
    public void onUpdate(double tpf) {
        double bottom = entity.getBottomY();
        vy += Ballistics.GRAVITY * tpf;
        entity.translateX(vx * tpf);
        entity.translateY(vy * tpf);
        double x = entity.getCenter().getX(), nb = entity.getBottomY();

        if ((trail -= tpf) <= 0) {
            trail = 0.03;
            Particles.burst(entity.getCenter(), Particles.EMBER, 1, -90, 180, 10, 40, 0.4, 4, -60);
        }
        Entity player = Game.getPlayer();
        if (entity.isColliding(player)) {
            PlayerStatsComponent stats = player.getComponent(PlayerStatsComponent.class);
            if (stats.takeDamage(DAMAGE, entity, true)) stats.burn(BURN);
            double[] s = HydraArena.surfaceUnder(x, player.getBottomY() - 8);
            splash(s != null ? new Point2D(x, s[2]) : entity.getCenter(), s);
            return;
        }
        if (vy > 0) {                                         // coming down: the first surface it passes lands it
            double[] hit = null;
            for (double[] s : HydraArena.surfaces()) {
                if (x < s[0] || x > s[1] || bottom > s[2] + 1 || nb < s[2]) continue;
                if (hit == null || s[2] < hit[2]) hit = s;
            }
            if (hit != null) {
                splash(new Point2D(x, hit[2]), hit);
                return;
            }
            if (nb > HydraArena.surface() + 6 && HydraArena.overTar(x)) {     // into the lake
                Particles.burst(new Point2D(x, HydraArena.surface()), HydraArena.TAR, 10, -90, 50, 60, 220, 0.6, 5, 700);
                Sfx.TAR_SPLASH.at(entity.getCenter(), 0.4, 1.3);
                entity.removeFromWorld();
                return;
            }
        }
        if (entity.getY() > HydraArena.groundY() + 600) entity.removeFromWorld();
    }

    private void splash(Point2D at, double[] surface) {
        Sfx.TAR_SPLASH.at(at, 0.9, 1.0);
        Particles.burst(at.subtract(0, 4), HydraArena.TAR, 12, -90, 70, 80, 260, 0.6, 5, 700);
        Particles.burst(at.subtract(0, 6), Particles.EMBER, 10, -90, 60, 60, 220, 0.5, 4, -100);
        if (surface != null && poolLife > 0) TarPoolComponent.spawn(at.getX(), surface, poolLife);
        entity.removeFromWorld();
    }
}
