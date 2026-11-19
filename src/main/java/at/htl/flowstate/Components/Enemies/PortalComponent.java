package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Assets;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Visuals.FrameSprite;
import at.htl.flowstate.Visuals.Frames;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.entity.component.Component;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;

/**
 * A rift on the ground (Gatekeeper, Pit Lord): tears open, lets the demons through after a moment, then closes.
 * {@code onOpen} runs once, when the demons come out.
 */
public class PortalComponent extends Component {
    private static final int SCALE = 2;
    private static final double OPEN_TIME = 0.25;     // frames 0-1
    private static final double SPAWN_AT = 0.85;
    private static final double LIFE = 1.4;
    private static final Color[] SPARKS = {Color.web("#ffd6e4"), Color.web("#ff7aa8"), Color.web("#e0306e")};

    private final Runnable onOpen;
    private FrameSprite view;
    private Frames frames;
    private double age;
    private boolean opened;

    private PortalComponent(Runnable onOpen) {
        this.onOpen = onOpen;
    }

    /** A portal standing on groundY, centred on x. */
    public static void spawn(double x, double groundY, Runnable onOpen) {
        Frames frames = Assets.frames("Portal.png", 6, SCALE);
        FrameSprite view = new FrameSprite(frames);
        PortalComponent portal = new PortalComponent(onOpen);
        portal.view = view;
        portal.frames = frames;
        entityBuilder()
                .at(x - frames.width() / 2, groundY - frames.height() + 4)
                .view(view)
                .with(portal)
                .zIndex(9)
                .buildAndAttach();
        Sfx.PORTAL_OPEN.at(new Point2D(x, groundY));
    }

    @Override
    public void onUpdate(double tpf) {
        age += tpf;
        if (age < OPEN_TIME) {
            view.show(frames, age < OPEN_TIME / 2 ? 0 : 1);
        } else {
            view.show(frames, 2 + (int) ((age - OPEN_TIME) * 10) % 4);
        }
        if (!opened && age >= SPAWN_AT) {
            opened = true;
            Particles.burst(entity.getCenter().add(0, 20), SPARKS, 16, -90, 120, 60, 240, 0.5, 5, 200);
            onOpen.run();
        }
        if (age > LIFE - 0.3) entity.setOpacity(Math.max(0, (LIFE - age) / 0.3));
        if (age >= LIFE) entity.removeFromWorld();
    }
}
