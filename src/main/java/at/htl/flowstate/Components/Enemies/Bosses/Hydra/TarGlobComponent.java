package at.htl.flowstate.Components.Enemies.Bosses.Hydra;

import at.htl.flowstate.Assets;
import at.htl.flowstate.Components.Enemies.Ballistics;
import at.htl.flowstate.Components.Enemies.Bosses.BossArena;
import at.htl.flowstate.Components.Enemies.Bosses.Surface;
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
    private final HydraArena lake = BossArena.active().hydra();
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
            Particles.of(Particles.EMBER).count(1).angle(-90, 180).speed(10, 40)
                    .life(0.4).size(4).gravity(-60).at(entity.getCenter());
        }
        Entity player = Game.getPlayer();
        if (entity.isColliding(player)) {
            PlayerStatsComponent stats = player.getComponent(PlayerStatsComponent.class);
            if (stats.takeDamage(DAMAGE, entity, true)) stats.burn(BURN);
            Surface s = lake.surfaceUnder(x, player.getBottomY() - 8);
            splash(s != null ? new Point2D(x, s.top()) : entity.getCenter(), s);
            return;
        }
        if (vy > 0) {                                         // coming down: the first surface it passes lands it
            Surface hit = null;
            for (Surface s : lake.surfaces()) {
                if (!s.spans(x) || bottom > s.top() + 1 || nb < s.top()) continue;
                if (hit == null || s.top() < hit.top()) hit = s;
            }
            if (hit != null) {
                splash(new Point2D(x, hit.top()), hit);
                return;
            }
            if (nb > lake.surface() + 6 && lake.overTar(x)) {     // into the lake
                Particles.of(HydraArena.TAR).count(10).angle(-90, 50).speed(60, 220)
                        .life(0.6).size(5).gravity(700).at(new Point2D(x, lake.surface()));
                Sfx.TAR_SPLASH.at(entity.getCenter(), 0.4, 1.3);
                entity.removeFromWorld();
                return;
            }
        }
        if (entity.getY() > lake.groundY() + 600) entity.removeFromWorld();
    }

    private void splash(Point2D at, Surface surface) {
        Sfx.TAR_SPLASH.at(at, 0.9, 1.0);
        Particles.of(HydraArena.TAR).count(12).angle(-90, 70).speed(80, 260).life(0.6).size(5).gravity(700).at(at.subtract(0, 4));
        Particles.of(Particles.EMBER).count(10).angle(-90, 60).speed(60, 220)
                .life(0.5).size(4).gravity(-100).at(at.subtract(0, 6));
        if (surface != null && poolLife > 0) TarPoolComponent.spawn(at.getX(), surface, poolLife);
        entity.removeFromWorld();
    }
}
