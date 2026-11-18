package at.htl.flowstate.Components.Player.Attacks;

import at.htl.flowstate.Combat.Combat;
import at.htl.flowstate.Combat.Hit;
import at.htl.flowstate.Game;
import com.almasb.fxgl.app.scene.Viewport;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import javafx.geometry.Point2D;

import java.util.HashSet;
import java.util.Set;

import static com.almasb.fxgl.dsl.FXGL.getGameScene;

/**
 * A thrown trident: flies straight, drops when its pierce is used up, sticks in
 * terrain, and is picked up by walking over it. Recall Trident can fly back.
 */
public class ThrownTridentComponent extends Component {
    /** Callbacks to the owning ability. */
    public interface Owner {
        void onLanded(Point2D position);
        void onPickedUp();
    }

    private enum State { FLYING, DROPPING, STUCK, RETURNING }

    private static final double DROP_GRAVITY = 800;
    private static final double RETURN_SPEED = 1200;
    private static final double LOST_RETURN_TIME = 3.0;

    private final Entity player = Game.getPlayer();
    private final Owner owner;
    private final Hit throwHit;
    private final Hit returnHit;
    private Point2D velocity;
    private int pierce;
    private State state = State.FLYING;
    private double lostTime = 0;
    private boolean pickedUp = false;
    private final Set<Entity> alreadyHit = new HashSet<>();

    public ThrownTridentComponent(Owner owner, Point2D velocity, int pierce, Hit throwHit, Hit returnHit) {
        this.owner = owner;
        this.velocity = velocity;
        this.pierce = pierce;
        this.throwHit = throwHit;
        this.returnHit = returnHit;
    }

    @Override
    public void onAdded() {
        entity.setRotationOrigin(new Point2D(entity.getWidth() / 2, entity.getHeight() / 2));
        rotateTo(velocity);
    }

    @Override
    public void onUpdate(double tpf) {
        if (pickedUp) return;
        switch (state) {
            case FLYING -> fly(tpf);
            case DROPPING -> drop(tpf);
            case STUCK -> { if (entity.isColliding(player)) pickUp(); }
            case RETURNING -> returnToPlayer(tpf);
        }
        if (state != State.RETURNING && !pickedUp) checkLost(tpf);
    }

    private void fly(double tpf) {
        entity.translate(velocity.multiply(tpf));
        for (Entity e : Combat.enemies()) {
            if (alreadyHit.contains(e) || !entity.isColliding(e)) continue;
            alreadyHit.add(e);
            Combat.hit(e, throwHit);
            if (pierce == 0) {
                land();
                state = State.DROPPING;
                velocity = Point2D.ZERO;
                return;
            }
            if (pierce > 0) pierce--;
        }
        if (Combat.touchesTerrain(entity)) {
            land();
            state = State.STUCK;
        }
    }

    private void drop(double tpf) {
        velocity = velocity.add(0, DROP_GRAVITY * tpf);
        entity.translate(velocity.multiply(tpf));
        if (Combat.touchesTerrain(entity)) state = State.STUCK;
        else if (entity.isColliding(player)) pickUp();
    }

    private void returnToPlayer(double tpf) {
        Point2D toPlayer = player.getCenter().subtract(entity.getCenter());
        if (toPlayer.magnitude() < 30 || entity.isColliding(player)) {
            pickUp();
            return;
        }
        velocity = toPlayer.normalize().multiply(RETURN_SPEED);
        rotateTo(velocity);
        entity.translate(velocity.multiply(tpf));
        for (Entity e : Combat.enemies()) {
            if (!alreadyHit.contains(e) && entity.isColliding(e)) {
                alreadyHit.add(e);
                Combat.hit(e, returnHit);
            }
        }
    }

    /** Recall Trident: fly back to the player from wherever it is. */
    public void recall() {
        if (state == State.RETURNING) return;
        state = State.RETURNING;
        alreadyHit.clear();
    }

    private void land() {
        owner.onLanded(entity.getCenter());
    }

    private void checkLost(double tpf) {
        Viewport vp = getGameScene().getViewport();
        double x = entity.getX(), y = entity.getY();
        boolean lost = x < vp.getX() - 100 || x > vp.getX() + vp.getWidth() + 300 || y > 1200 || y < vp.getY() - 600;
        lostTime = lost ? lostTime + tpf : 0;
        if (lostTime >= LOST_RETURN_TIME) pickUp();
    }

    private void pickUp() {
        if (pickedUp) return;
        pickedUp = true;
        owner.onPickedUp();
        entity.removeFromWorld();
    }

    private void rotateTo(Point2D v) {
        if (v.magnitude() > 0.01) entity.setRotation(Math.toDegrees(Math.atan2(v.getY(), v.getX())));
    }

    public boolean isFlying() {
        return state == State.FLYING;
    }
}
