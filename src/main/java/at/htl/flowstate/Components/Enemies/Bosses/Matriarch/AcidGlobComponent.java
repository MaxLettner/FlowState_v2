package at.htl.flowstate.Components.Enemies.Bosses.Matriarch;

import at.htl.flowstate.Assets;
import at.htl.flowstate.Components.Enemies.Ballistics;
import at.htl.flowstate.Components.Enemies.Bosses.BossArena;
import at.htl.flowstate.Components.Enemies.Bosses.Surface;
import at.htl.flowstate.Components.Player.PlayerStatsComponent;
import at.htl.flowstate.Game;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Visuals.Darkness;
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

/**
 * A glob of the Void Matriarch's purple acid, lobbed in an arc. It splashes on the first ledge or bridge it comes
 * down on and leaves an {@link AcidPoolComponent acid pool} there; a direct hit hurts the player (and still leaves
 * the pool under them). Globs that miss everything sink into the chasm.
 */
public class AcidGlobComponent extends Component {
    private final MatriarchArena chasm = BossArena.active().matriarch();
    private static final double SIZE = 18;                 // hitbox (world px)
    private static final double DAMAGE = 10;
    static final Color LIGHT = Color.rgb(220, 100, 255);

    private final double poolLife;
    private double vx, vy;
    private double trail;

    private AcidGlobComponent(double vx, double vy, double poolLife) {
        this.vx = vx;
        this.vy = vy;
        this.poolLife = poolLife;
    }

    /** Lobs a glob from {@code from} onto {@code to} at about {@code speed}. */
    public static Entity lob(Point2D from, Point2D to, double speed, double poolLife) {
        Point2D v = Ballistics.velocity(from, to, speed, Ballistics.GRAVITY);
        if (v == null) v = Ballistics.velocity(from, to, speed * 1.6, Ballistics.GRAVITY);
        if (v == null) v = new Point2D((to.getX() - from.getX()) / 1.1, 0);
        Frames frames = Assets.frames("AcidGlob.png", 4, 2);
        FrameSprite view = new FrameSprite(frames).loop(frames, 12);
        view.setTranslateX(SIZE / 2 - frames.width() / 2);
        view.setTranslateY(SIZE / 2 - frames.height() / 2);
        Entity e = entityBuilder()
                .at(from.subtract(SIZE / 2, SIZE / 2))
                .view(view)
                .bbox(new HitBox(BoundingShape.box(SIZE, SIZE)))
                .with(new AcidGlobComponent(v.getX(), v.getY(), poolLife))
                .zIndex(12)
                .buildAndAttach();
        if (Darkness.isOn()) Darkness.light(e, 70, LIGHT);
        return e;
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
            Particles.of(Particles.ACID).count(1).angle(-90, 180).speed(10, 40)
                    .life(0.4).size(4).gravity(300).at(entity.getCenter());
        }
        Entity player = Game.getPlayer();
        if (entity.isColliding(player)) {
            player.getComponent(PlayerStatsComponent.class).takeDamage(DAMAGE, entity, true);
            Surface s = chasm.surfaceUnder(x, player.getBottomY() - 8);
            splash(s != null ? new Point2D(x, s.top()) : entity.getCenter(), s);
            return;
        }
        if (vy > 0) {                                         // coming down: the first surface it passes lands it
            Surface hit = null;
            for (Surface s : chasm.surfaces()) {
                if (!s.spans(x) || bottom > s.top() + 1 || nb < s.top()) continue;
                if (hit == null || s.top() < hit.top()) hit = s;
            }
            if (hit != null) {
                splash(new Point2D(x, hit.top()), hit);
                return;
            }
        }
        if (entity.getY() > chasm.groundY() + 500) {           // lost in the chasm
            Particles.of(Particles.VOID).count(6).angle(-90, 60).speed(30, 100)
                    .life(0.6).size(4).gravity(-60).at(entity.getCenter());
            entity.removeFromWorld();
        }
    }

    private void splash(Point2D at, Surface surface) {
        Sfx.ACID_SIZZLE.at(at, 1, 0.9);
        Sfx.MAGMA_SPLASH.at(at, 0.5, 1.3);
        Particles.of(Particles.ACID).count(16).angle(-90, 70).speed(90, 300).life(0.6).size(5).gravity(700).at(at.subtract(0, 4));
        if (surface != null) AcidPoolComponent.spawn(at.getX(), surface, poolLife);
        entity.removeFromWorld();
    }
}
