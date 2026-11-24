package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Assets;
import at.htl.flowstate.Combat.Combat;
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

/** An ember shard knocked loose from the Hollow Ember: it arcs to the ground, heals the player a little when touched, fades after a while. */
public class HollowShardLoose extends Component {
    public static final double HEAL = 0.05;          // of the player's max health
    private static final double LIFE = 6, BLINK = 1.5, GRAVITY = 900, SIZE = 26;

    private double vx, vy, age;
    private boolean landed;

    private HollowShardLoose(double vx, double vy) {
        this.vx = vx;
        this.vy = vy;
    }

    public static void spawn(Point2D at, double vx, double vy) {
        Frames frames = Assets.frames("HollowShardLoose.png", 4, 2);
        FrameSprite view = new FrameSprite(frames).loop(frames, 8);
        view.setTranslateX(SIZE / 2 - frames.width() / 2);
        view.setTranslateY(SIZE / 2 - frames.height() / 2);
        entityBuilder()
                .at(at.subtract(SIZE / 2, SIZE / 2))
                .bbox(new HitBox(BoundingShape.box(SIZE, SIZE)))
                .view(view)
                .with(new HollowShardLoose(vx, vy))
                .zIndex(14)
                .buildAndAttach();
    }

    @Override
    public void onUpdate(double tpf) {
        age += tpf;
        if (!landed) {
            vy += GRAVITY * tpf;
            entity.translate(vx * tpf, vy * tpf);
            double ground = Combat.groundYBelow(entity.getCenter().getX(), entity.getCenter().getY());
            if (!Double.isNaN(ground) && entity.getBottomY() >= ground && vy > 0) {
                entity.setY(ground - entity.getHeight() - 2);
                landed = true;
            }
        } else {
            entity.setY(entity.getY() + Math.sin(age * 6) * 0.4);
        }
        entity.setOpacity(age > LIFE - BLINK ? (((int) (age * 12)) % 2 == 0 ? 1 : 0.3) : 1);
        Entity player = Game.getPlayer();
        if (entity.isColliding(player)) {
            PlayerStatsComponent stats = player.getComponent(PlayerStatsComponent.class);
            stats.heal(stats.getMaxHealth() * HEAL);
            Sfx.HOLLOW_SHARD_PICKUP.at(entity.getCenter());
            Particles.burst(entity.getCenter(), Particles.EMBER, 12, -90, 180, 60, 200, 0.5, 4, -60);
            entity.removeFromWorld();
        } else if (age >= LIFE) {
            Particles.burst(entity.getCenter(), Particles.VOID, 5, -90, 180, 30, 100, 0.4, 4, 0);
            entity.removeFromWorld();
        }
    }
}
