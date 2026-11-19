package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Assets;
import at.htl.flowstate.Components.Player.Helpers.DeleteAfterTimeComponent;
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
import javafx.scene.paint.Color;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;

/**
 * A slow projectile that turns toward the player a little at a time and fizzles out after a while:
 * the Hellfire Cultist's orbs and the Hex Priest's cursing bolt.
 */
public class HomingOrbComponent extends Component {
    /** Texture, flight and colours of one kind of homing projectile. */
    public record Style(String texture, double speed, double turnRate, double life, double size, Color[] trail) {}

    public static final Style HELLFIRE = new Style("HellfireOrb.png", 220, 2.3, 4.5, 14,
            new Color[]{Color.web("#f4ffd0"), Color.web("#b8ff6a"), Color.web("#5fe83a")});
    public static final Style HEX = new Style("HexBolt.png", 300, 1.3, 3.5, 12,
            new Color[]{Color.web("#f6d8ff"), Color.web("#d08cff"), Color.web("#a050f0")});

    private static final double CURSE_TIME = 4.0;

    private final Entity player = Game.getPlayer();
    private final Style style;
    private double angle;
    private double age;
    private double trailTimer;

    private HomingOrbComponent(Style style, double angle) {
        this.style = style;
        this.angle = angle;
    }

    /** A Hellfire Cultist orb. */
    public static void spawn(Point2D at, double angle, double damage, Entity owner) {
        spawn(HELLFIRE, at, angle, damage, owner, null);
    }

    /** A Hex Priest bolt: curses the player (lower jumps, no mana regen) when it hurts them. */
    public static void spawnHex(Point2D at, double angle, double damage, Entity owner) {
        spawn(HEX, at, angle, damage, owner,
                () -> Game.getPlayer().getComponent(PlayerStatsComponent.class).curse(CURSE_TIME));
    }

    private static void spawn(Style style, Point2D at, double angle, double damage, Entity owner, Runnable onHurt) {
        Frames frames = Assets.frames(style.texture(), 4, 2);
        FrameSprite view = new FrameSprite(frames).loop(frames, 12);
        double size = style.size();
        view.setTranslateX(size / 2 - frames.width() / 2);
        view.setTranslateY(size / 2 - frames.height() / 2);
        HomingOrbComponent orb = new HomingOrbComponent(style, angle);
        entityBuilder()
                .at(at.subtract(size / 2, size / 2))
                .view(view)
                .bbox(new HitBox(BoundingShape.box(size, size)))
                .with(orb)
                .with(new EnemyProjectileComponent(damage, orb::fizzle).owner(owner).onHurt(onHurt))
                .with(new DeleteAfterTimeComponent(style.life() + 0.1))
                .zIndex(12)
                .buildAndAttach();
    }

    @Override
    public void onAdded() {
        entity.setRotationOrigin(new Point2D(style.size() / 2, style.size() / 2));
    }

    @Override
    public void onUpdate(double tpf) {
        age += tpf;
        Point2D to = player.getCenter().subtract(entity.getCenter());
        double want = Math.atan2(to.getY(), to.getX());
        double diff = Math.atan2(Math.sin(want - angle), Math.cos(want - angle));
        double turn = style.turnRate() * tpf;
        angle += Math.max(-turn, Math.min(turn, diff));
        entity.translate(Math.cos(angle) * style.speed() * tpf, Math.sin(angle) * style.speed() * tpf);
        entity.setRotation(Math.toDegrees(angle));

        trailTimer -= tpf;
        if (trailTimer <= 0) {
            trailTimer = 0.05;
            Particles.burst(entity.getCenter(), style.trail(), 1, Math.toDegrees(angle) + 180, 25, 20, 60, 0.4, 4, -30);
        }
        double life = style.life();
        if (age > life - 0.4) entity.setOpacity(Math.max(0, (life - age) / 0.4));
        if (age >= life) entity.removeFromWorld();
    }

    private void fizzle(Entity orb) {
        Particles.burst(orb.getCenter(), style.trail(), 8, -90, 180, 60, 180, 0.4, 4, 0);
    }
}
