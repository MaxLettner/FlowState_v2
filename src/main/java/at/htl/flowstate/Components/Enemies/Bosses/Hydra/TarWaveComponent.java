package at.htl.flowstate.Components.Enemies.Bosses.Hydra;

import at.htl.flowstate.Assets;
import at.htl.flowstate.Components.Enemies.Bosses.BossArena;
import at.htl.flowstate.Components.Player.PlayerStatsComponent;
import at.htl.flowstate.Game;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Visuals.FrameSprite;
import at.htl.flowstate.Visuals.Frames;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import javafx.geometry.Point2D;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;

/**
 * A wave of tar the Crowned Head slams up out of the lake: it rolls across the surface toward the player and hurts
 * whoever is low enough in its way (on a low pillar without jumping, or wading). High pillars and a jump clear it.
 */
public class TarWaveComponent extends Component {
    private final HydraArena lake = BossArena.active().hydra();
    private static final double SPEED = 430;
    private static final double W = 240, H = 120, SINK = 6;      // TarWave.png 120x60 at x2
    private static final double REACH = 105;                     // it hurts below this height above the surface
    private static final double DAMAGE = 18, KNOCK = 520;

    private final int dir;
    private boolean struck;
    private double spray;

    private TarWaveComponent(int dir) {
        this.dir = dir;
    }

    /** A wave rising at x, rolling in direction dir. */
    public static Entity spawn(double x, int dir) {
        Frames frames = Assets.frames("TarWave.png", 4, 2);
        FrameSprite view = new FrameSprite(frames).loop(frames, 10);
        if (dir < 0) view.setScaleX(-1);
        HydraArena lake = BossArena.active().hydra();
        Sfx.TAR_WAVE.at(new Point2D(x, lake.surface()), 1, 0.9);
        return entityBuilder()
                .at(x - W / 2, lake.surface() + SINK - H)
                .view(view)
                .with(new TarWaveComponent(dir))
                .zIndex(13)
                .buildAndAttach();
    }

    @Override
    public void onUpdate(double tpf) {
        entity.translateX(dir * SPEED * tpf);
        entity.setY(lake.surface() + SINK - H);
        double front = entity.getX() + (dir > 0 ? W * 0.85 : W * 0.15), back = entity.getX() + (dir > 0 ? W * 0.2 : W * 0.8);
        if ((spray -= tpf) <= 0) {
            spray = 0.05;
            Particles.of(Particles.EMBER).count(1).angle(-90 - dir * 40, 30).speed(60, 160)
                    .life(0.5).size(4).gravity(500).at(new Point2D(front, entity.getY() + 40));
            Particles.of(HydraArena.TAR).count(1).angle(-90 - dir * 30, 30).speed(80, 200)
                    .life(0.6).size(5).gravity(700).at(new Point2D(front, lake.surface()));
        }
        Entity player = Game.getPlayer();
        double px = player.getCenter().getX();
        if (!struck && px > Math.min(front, back) && px < Math.max(front, back)
                && player.getBottomY() > lake.surface() - REACH) {
            struck = true;
            PlayerStatsComponent stats = player.getComponent(PlayerStatsComponent.class);
            if (stats.takeDamage(DAMAGE, entity, false)) {
                player.getComponent(at.htl.flowstate.Components.Player.PlayerMovementComponent.class).pull(dir * 24);
                Particles.of(HydraArena.TAR).count(14).angle(-90, 60).speed(80, 260)
                        .life(0.6).size(5).gravity(600).at(player.getCenter());
            }
        }
        double lo = lake.lockX(), hi = lo + BossArena.WIDTH;
        if (entity.getRightX() < lo - 40 || entity.getX() > hi + 40) entity.removeFromWorld();
    }
}
