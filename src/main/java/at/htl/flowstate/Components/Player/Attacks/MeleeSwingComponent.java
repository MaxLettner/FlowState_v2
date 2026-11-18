package at.htl.flowstate.Components.Player.Attacks;

import at.htl.flowstate.Combat.Combat;
import at.htl.flowstate.Combat.Hit;
import at.htl.flowstate.Components.Player.PlayerMovementComponent;
import at.htl.flowstate.Game;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import com.almasb.fxgl.dsl.FXGL;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;

import java.util.HashSet;
import java.util.Set;

/** An eased arc swing attached to the player. Each enemy is hit at most once. */
public class MeleeSwingComponent extends Component {
    private static final double START_ANGLE = 220.0;
    private static final double END_ANGLE = 45.0;

    private final Entity player;
    private final Hit hit;
    private final double duration;
    private final boolean reverse;
    private final Set<Entity> alreadyHit = new HashSet<>();
    private double elapsed = 0;
    private static final double TRAIL_DEGREES = 80;
    private double startRotation;
    private Arc trail;
    private Entity trailEntity;

    public MeleeSwingComponent(Hit hit, double duration, boolean reverse) {
        this.player = Game.getPlayer();
        this.hit = hit;
        this.duration = duration;
        this.reverse = reverse;
    }

    @Override
    public void onAdded() {
        entity.setRotationOrigin(new Point2D(entity.getWidth() / 2, 0));
        place(0);
        startRotation = entity.getRotation();
        double radius = entity.getHeight() * 0.8;
        trail = new Arc(0, 0, radius, radius, 0, 0);
        trail.setType(ArcType.OPEN);
        trail.setFill(null);
        trail.setStroke(Color.rgb(255, 255, 255, 0.35));
        trail.setStrokeWidth(Math.max(6, entity.getHeight() * 0.28));
        trail.setStrokeLineCap(StrokeLineCap.ROUND);
        trailEntity = FXGL.entityBuilder().view(trail).zIndex(12).buildAndAttach();
        updateTrail(0);
    }

    @Override
    public void onRemoved() {
        if (trailEntity != null) trailEntity.removeFromWorld();
    }

    /** Arc behind the blade covering the last part of the swing. JavaFX arcs are counter-clockwise from +x. */
    private void updateTrail(double t) {
        double current = entity.getRotation();
        double swept = current - startRotation;
        double tail = Math.signum(swept) * Math.min(Math.abs(swept), TRAIL_DEGREES);
        trail.setStartAngle(-90 - current);
        trail.setLength(tail);
        trailEntity.setPosition(entity.getX() + entity.getWidth() / 2, entity.getY());
        trailEntity.setOpacity(t < 0.7 ? 1 : Math.max(0, 1 - (t - 0.7) / 0.3));
    }

    @Override
    public void onUpdate(double tpf) {
        elapsed += tpf;
        double t = Math.min(elapsed / duration, 1.0);
        place(1 - Math.pow(1 - t, 2));
        updateTrail(t);

        for (Entity e : Combat.enemies()) {
            if (!alreadyHit.contains(e) && e.isColliding(entity)) {
                alreadyHit.add(e);
                Combat.hit(e, hit);
            }
        }

        if (t >= 1.0) entity.removeFromWorld();
    }

    private void place(double eased) {
        int dir = -player.getComponent(PlayerMovementComponent.class).getCurrentWatchDirection();
        double start = dir > 0 ? START_ANGLE : -START_ANGLE;
        double end = dir > 0 ? END_ANGLE : -END_ANGLE;
        if (reverse) {
            double tmp = start;
            start = end;
            end = tmp;
        }
        entity.setRotation(start + (end - start) * eased);
        entity.setPosition(player.getCenter().getX() - entity.getWidth() / 2, player.getY() + player.getHeight() / 4);
    }
}
