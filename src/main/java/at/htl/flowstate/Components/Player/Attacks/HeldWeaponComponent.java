package at.htl.flowstate.Components.Player.Attacks;

import at.htl.flowstate.Components.Player.PlayerMovementComponent;
import at.htl.flowstate.Game;
import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import javafx.geometry.Point2D;
import javafx.scene.Node;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;

/**
 * A purely visual item held by the player (bows, crossbows, block plate).
 * Fixed mode: placed in front of the player and mirrored with the facing direction.
 * Aimed mode: the grip (pivot) sits {@code distance} px from the player's centre towards the mouse,
 * the item is rotated to the aim and flipped vertically when aiming left so it never hangs upside down.
 */
public class HeldWeaponComponent extends Component {
    private final Entity player = Game.getPlayer();
    private final double width, height, offsetX, offsetY;
    private final boolean aimed;
    private final double pivotX, pivotY, distance;
    private double lifetime;

    private HeldWeaponComponent(double width, double height, double offsetX, double offsetY, double lifetime,
                                boolean aimed, double pivotX, double pivotY, double distance) {
        this.width = width;
        this.height = height;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.lifetime = lifetime;
        this.aimed = aimed;
        this.pivotX = pivotX;
        this.pivotY = pivotY;
        this.distance = distance;
    }

    /** @param lifetime seconds until removal, or &lt;= 0 to keep it until removed manually */
    public static Entity spawn(Node view, double width, double height, double offsetX, double offsetY, double lifetime) {
        return entityBuilder()
                .view(view)
                .with(new HeldWeaponComponent(width, height, offsetX, offsetY, lifetime, false, 0, 0, 0))
                .zIndex(5)
                .buildAndAttach();
    }

    /** Aimed item; (pivotX, pivotY) is the grip inside the view. */
    public static Entity spawnAimed(Node view, double pivotX, double pivotY, double distance, double lifetime) {
        return entityBuilder()
                .view(view)
                .with(new HeldWeaponComponent(0, 0, 0, 0, lifetime, true, pivotX, pivotY, distance))
                .zIndex(5)
                .buildAndAttach();
    }

    public void setLifetime(double seconds) {
        lifetime = seconds;
    }

    @Override
    public void onAdded() {
        if (aimed) {
            entity.setRotationOrigin(new Point2D(pivotX, pivotY));
            entity.setScaleOrigin(new Point2D(pivotX, pivotY));
        } else {
            entity.setScaleOrigin(new Point2D(width / 2, height / 2));
        }
        place();
    }

    @Override
    public void onUpdate(double tpf) {
        place();
        if (lifetime > 0) {
            lifetime -= tpf;
            if (lifetime <= 0) entity.removeFromWorld();
        }
    }

    private void place() {
        if (aimed) {
            Point2D center = player.getCenter();
            Point2D aim = FXGL.getInput().getMousePositionWorld().subtract(center);
            if (aim.magnitude() < 1e-3) aim = new Point2D(facing(), 0);
            aim = aim.normalize();
            entity.setRotation(Math.toDegrees(Math.atan2(aim.getY(), aim.getX())));
            entity.setScaleY(aim.getX() < 0 ? -1 : 1);
            Point2D grip = center.add(aim.multiply(distance));
            entity.setPosition(grip.getX() - pivotX, grip.getY() - pivotY);
            return;
        }
        int facing = facing();
        entity.setScaleX(facing);
        entity.setPosition(player.getCenter().getX() + facing * offsetX - width / 2,
                player.getCenter().getY() + offsetY - height / 2);
    }

    private int facing() {
        return player.getComponent(PlayerMovementComponent.class).getCurrentWatchDirection();
    }
}
