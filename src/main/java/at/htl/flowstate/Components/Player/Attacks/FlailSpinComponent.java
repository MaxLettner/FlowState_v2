package at.htl.flowstate.Components.Player.Attacks;

import at.htl.flowstate.Game;
import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import javafx.geometry.Point2D;

/**
 * Morningstar hold: the chained ball whirls around the player's centre, the chain trailing in a slight curve.
 * Visual only, the ability does the damage and sets the speed.
 */
public class FlailSpinComponent extends Component {
    private static final double DEGREES_PER_SECOND = 720;
    private static final double LINK_LENGTH = 10;
    private static final double CURL = 30;            // degrees the ball trails behind the grip

    private final Entity player = Game.getPlayer();
    private final FlailView view;
    private final int linkCount;
    private double angle = -90;
    private double speed = 1;

    private FlailSpinComponent(FlailView view, int linkCount) {
        this.view = view;
        this.linkCount = linkCount;
    }

    /** Starts the spin; {@code reach} is the distance from the player's centre to the ball's centre. */
    public static Entity spawn(double reach) {
        int links = FlailView.linkCount(reach, LINK_LENGTH);
        FlailView view = new FlailView(links);
        return FXGL.entityBuilder()
                .view(view)
                .with(new FlailSpinComponent(view, links))
                .zIndex(13)
                .buildAndAttach();
    }

    /** Spin speed as a multiple of the starting speed. */
    public void setSpeed(double speed) {
        this.speed = speed;
    }

    @Override
    public void onAdded() {
        draw();
    }

    @Override
    public void onUpdate(double tpf) {
        angle += DEGREES_PER_SECOND * speed * tpf;
        draw();
    }

    private void draw() {
        Point2D hand = player.getCenter();
        Point2D[] joints = new Point2D[linkCount + 1];
        joints[0] = hand.add(direction(angle).multiply(FlailView.handleLength()));
        for (int i = 1; i <= linkCount; i++) {
            joints[i] = joints[i - 1].add(direction(angle - CURL * i / linkCount).multiply(LINK_LENGTH));
        }
        view.draw(hand, joints);
    }

    private static Point2D direction(double degrees) {
        double r = Math.toRadians(degrees);
        return new Point2D(Math.cos(r), Math.sin(r));
    }
}
