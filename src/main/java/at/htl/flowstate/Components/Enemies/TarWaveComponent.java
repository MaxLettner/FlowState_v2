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
import javafx.geometry.Point2D;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;

/**
 * A wave of tar the Crowned Head slams up out of the lake: it rolls across the surface toward the player and hurts
 * whoever is low enough in its way (on a low pillar without jumping, or wading). High pillars and a jump clear it.
 */
public class TarWaveComponent extends Component {
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
        Sfx.TAR_WAVE.at(new Point2D(x, HydraArena.surface()), 1, 0.9);
        return entityBuilder()
                .at(x - W / 2, HydraArena.surface() + SINK - H)
                .view(view)
                .with(new TarWaveComponent(dir))
                .zIndex(13)
                .buildAndAttach();
    }

    @Override
    public void onUpdate(double tpf) {
        entity.translateX(dir * SPEED * tpf);
        entity.setY(HydraArena.surface() + SINK - H);
        double front = entity.getX() + (dir > 0 ? W * 0.85 : W * 0.15), back = entity.getX() + (dir > 0 ? W * 0.2 : W * 0.8);
        if ((spray -= tpf) <= 0) {
            spray = 0.05;
            Particles.burst(new Point2D(front, entity.getY() + 40), Particles.EMBER, 1, -90 - dir * 40, 30, 60, 160, 0.5, 4, 500);
            Particles.burst(new Point2D(front, HydraArena.surface()), HydraArena.TAR, 1, -90 - dir * 30, 30, 80, 200, 0.6, 5, 700);
        }
        Entity player = Game.getPlayer();
        double px = player.getCenter().getX();
        if (!struck && px > Math.min(front, back) && px < Math.max(front, back)
                && player.getBottomY() > HydraArena.surface() - REACH) {
            struck = true;
            PlayerStatsComponent stats = player.getComponent(PlayerStatsComponent.class);
            if (stats.takeDamage(DAMAGE, entity, false)) {
                player.getComponent(at.htl.flowstate.Components.Player.PlayerMovementComponent.class).pull(dir * 24);
                Particles.burst(player.getCenter(), HydraArena.TAR, 14, -90, 60, 80, 260, 0.6, 5, 600);
            }
        }
        double lo = HydraArena.lockX(), hi = lo + BossArena.WIDTH;
        if (entity.getRightX() < lo - 40 || entity.getX() > hi + 40) entity.removeFromWorld();
    }
}
