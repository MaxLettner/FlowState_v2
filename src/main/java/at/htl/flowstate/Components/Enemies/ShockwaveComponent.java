package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Assets;
import at.htl.flowstate.Combat.Combat;
import at.htl.flowstate.Components.Player.PlayerStatsComponent;
import at.htl.flowstate.Game;
import at.htl.flowstate.Visuals.FrameSprite;
import at.htl.flowstate.Visuals.Frames;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import com.almasb.fxgl.physics.BoundingShape;
import com.almasb.fxgl.physics.HitBox;
import javafx.geometry.Point2D;

import java.util.HashSet;
import java.util.Set;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;

/**
 * Pit Brute slam: a crest of rock rolling along the floor. Follows slopes, breaks against walls and
 * at pits; hits the player once unless they jump over it. The Pit Lord's waves also hit other demons.
 */
public class ShockwaveComponent extends Component {
    private static final double SPEED = 520;
    private static final double RANGE = 650;
    private static final double DAMAGE = 16;
    private static final double DEMON_KNOCKBACK = 380;
    private static final double W = 100, H = 60;
    private static final double MAX_CLIMB = 24;   // a step higher than this is a wall
    private static final int SCALE = 2;

    private final int dir;
    private final Entity owner;
    private final boolean hurtsDemons;
    private final Set<Entity> struck = new HashSet<>();
    private final Entity player = Game.getPlayer();
    private double travelled;
    private double groundY;
    private boolean hit;
    private double dustTimer;

    private ShockwaveComponent(int dir, double groundY, Entity owner, boolean hurtsDemons) {
        this.dir = dir;
        this.owner = owner;
        this.hurtsDemons = hurtsDemons;
        this.groundY = groundY;
    }

    public static void spawn(double x, double groundY, int dir, Entity owner) {
        spawn(x, groundY, dir, owner, false);
    }

    /** {@code hurtsDemons}: every demon but the owner caught in the wave is struck once as well. */
    public static void spawn(double x, double groundY, int dir, Entity owner, boolean hurtsDemons) {
        Frames frames = Assets.frames("BruteShockwave.png", 4, SCALE);
        FrameSprite view = new FrameSprite(frames).loop(frames, 14);
        view.setTranslateX(W / 2 - frames.width() / 2);
        view.setTranslateY(H - frames.height() + 2);
        view.setScaleX(dir);
        entityBuilder()
                .at(x - W / 2, groundY - H)
                .view(view)
                .bbox(new HitBox(BoundingShape.box(W, H)))
                .with(new ShockwaveComponent(dir, groundY, owner, hurtsDemons))
                .zIndex(11)
                .buildAndAttach();
    }

    @Override
    public void onUpdate(double tpf) {
        double step = SPEED * tpf * dir;
        double nextX = entity.getCenter().getX() + step + dir * W / 2;
        double ground = Combat.groundYBelow(nextX, groundY);
        if (Double.isNaN(ground) || groundY - ground > MAX_CLIMB || ground - groundY > 60) {
            breakUp();
            return;
        }
        groundY = ground;
        entity.translateX(step);
        entity.setY(groundY - H);
        travelled += Math.abs(step);

        if (!hit && entity.isColliding(player)) {
            hit = player.getComponent(PlayerStatsComponent.class).takeDamage(DAMAGE, entity, false);
            if (hit) EliteComponent.onHit(owner);
        }
        if (hurtsDemons) {
            for (Entity e : Combat.enemies()) {
                if (e == owner || struck.contains(e) || !entity.isColliding(e)) continue;
                struck.add(e);
                e.getComponentOptional(EnemyStatsComponent.class).ifPresent(s -> s.struckByDemon(DAMAGE, dir, DEMON_KNOCKBACK));
            }
        }
        dustTimer -= tpf;
        if (dustTimer <= 0) {
            dustTimer = 0.05;
            Particles.burst(new Point2D(entity.getCenter().getX(), groundY - 2), Particles.DUST, 3, -90 - dir * 40, 30, 80, 220, 0.45, 6, 500);
        }
        double left = RANGE - travelled;
        entity.setOpacity(Math.min(1, left / 120));
        if (left <= 0) entity.removeFromWorld();
    }

    private void breakUp() {
        Particles.burst(new Point2D(entity.getCenter().getX() + dir * W / 2, groundY - 16), Particles.DUST, 18, -90, 80, 100, 340, 0.55, 7, 800);
        entity.removeFromWorld();
    }
}
