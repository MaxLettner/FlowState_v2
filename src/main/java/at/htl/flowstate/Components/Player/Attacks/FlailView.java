package at.htl.flowstate.Components.Player.Attacks;

import at.htl.flowstate.Assets;
import at.htl.flowstate.Visuals.WeaponArt;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.image.ImageView;
import javafx.scene.transform.Rotate;

/**
 * The chained Morningstar, drawn relative to the hand (put its entity at the hand): the grip in the hand, a chain of links
 * through the given joints, the spiked ball on the last joint. Links alternate face-on rings and edge-on links.
 */
public class FlailView extends Group {
    private static final String HANDLE = "FlailHandle.png";
    private static final double GRIP = 6;   // how far the grip sticks out behind the hand
    private static final int SCALE = 2;

    private final Rotate handleTurn = new Rotate();
    private final ImageView[] links;
    private final ImageView head;

    /** @param linkCount number of chain segments (joints - 1) */
    public FlailView(int linkCount) {
        Group handle = new Group(WeaponArt.tipAt(HANDLE, handleLength(), 0, 1));
        handle.getTransforms().add(handleTurn);

        links = new ImageView[linkCount];
        for (int i = 0; i < linkCount; i++) {
            links[i] = new ImageView(Assets.frameImage("ChainLinks.png", 2, SCALE, i % 2));
            links[i].setSmooth(false);
        }
        head = Assets.sprite("FlailHead.png", SCALE);
        getChildren().add(handle);
        getChildren().addAll(links);
        getChildren().add(head);
    }

    /** Distance from the hand to where the chain starts. */
    public static double handleLength() {
        return WeaponArt.length(HANDLE, 1) - GRIP;
    }

    /** How many links of {@code linkLength} a flail reaching {@code reach} from the hand to the ball needs. */
    public static int linkCount(double reach, double linkLength) {
        return Math.max(2, (int) Math.round((reach - handleLength()) / linkLength));
    }

    /**
     * @param hand   where the player holds the grip
     * @param joints chain joints: [0] at the grip's tip, the last one is the ball's centre
     */
    public void draw(Point2D hand, Point2D[] joints) {
        handleTurn.setAngle(degrees(hand, joints[0]));

        for (int i = 0; i < links.length; i++) {
            Point2D a = joints[i].subtract(hand), b = joints[i + 1].subtract(hand);
            ImageView link = links[i];
            link.setTranslateX((a.getX() + b.getX()) / 2 - link.getImage().getWidth() / 2);
            link.setTranslateY((a.getY() + b.getY()) / 2 - link.getImage().getHeight() / 2);
            link.setRotate(degrees(a, b));
        }

        Point2D last = joints[joints.length - 1].subtract(hand);
        head.setTranslateX(last.getX() - head.getImage().getWidth() / 2);
        head.setTranslateY(last.getY() - head.getImage().getHeight() / 2);
        head.setRotate(degrees(joints[joints.length - 2], last));
    }

    private static double degrees(Point2D from, Point2D to) {
        return Math.toDegrees(Math.atan2(to.getY() - from.getY(), to.getX() - from.getX()));
    }
}
