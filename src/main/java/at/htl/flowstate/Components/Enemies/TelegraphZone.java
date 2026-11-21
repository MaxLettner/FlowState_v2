package at.htl.flowstate.Components.Enemies;

import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;

/**
 * A boss's warning for the blow it is winding up: the stretch of ground, and the height above it, that the blow will
 * cover. It shows from the start of the wind-up until the blow lands. A red field fills in from the attacker's side as
 * the moment comes, the bright rim on top marks how high the blow reaches (above it is safe: jump), the ground line
 * pulses faster and faster, and the whole thing flashes white-hot just before the hit.
 * Purely visual: the attack does its own hit test with the same numbers.
 */
public class TelegraphZone extends Component {
    private static final double FADE_IN = 0.1, FLASH = 0.14, LINGER = 0.1;
    private static final Color HOT = Color.rgb(255, 196, 61);

    private final double w, lead;
    private final int fromDir;
    private Rectangle field, fill, rim, ground;
    private double age;

    private TelegraphZone(double w, double lead, int fromDir) {
        this.w = w;
        this.lead = Math.max(0.2, lead);
        this.fromDir = fromDir;
    }

    /**
     * Marks x0..x1 on the ground at groundY, up to height above it, for lead seconds (when the blow lands).
     * fromDir: the side the fill grows from (1 = from the left edge, -1 = from the right).
     */
    public static TelegraphZone spawn(double x0, double x1, double groundY, double height, double lead, int fromDir) {
        double w = Math.max(20, x1 - x0);
        TelegraphZone z = new TelegraphZone(w, lead, fromDir);
        z.field = new Rectangle(w, height, gradient(0.2, 0.03));
        z.fill = new Rectangle(0, height, gradient(0.38, 0.06));
        z.rim = new Rectangle(w, 3, Color.rgb(255, 120, 40, 0.8));
        z.ground = new Rectangle(w, 6, HOT);
        z.ground.setTranslateY(height - 6);
        z.ground.setEffect(new DropShadow(14, Color.rgb(255, 90, 20, 0.9)));
        entityBuilder()
                .at(x0, groundY - height)
                .view(z.field)
                .view(z.fill)
                .view(z.rim)
                .view(z.ground)
                .with(z)
                .zIndex(9)
                .buildAndAttach();
        z.onUpdate(0);
        return z;
    }

    private static LinearGradient gradient(double bottom, double top) {
        return new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(255, 60, 20, top)), new Stop(1, Color.rgb(255, 60, 20, bottom)));
    }

    /** The blow was called off (stunned, killed): the warning goes at once. */
    public void dismiss() {
        Entity e = getEntity();
        if (e != null && e.isActive()) e.removeFromWorld();
    }

    @Override
    public void onUpdate(double tpf) {
        age += tpf;
        double k = Math.min(1, age / lead);
        double in = Math.min(1, age / FADE_IN);
        fill.setWidth(w * k);
        fill.setTranslateX(fromDir > 0 ? 0 : w * (1 - k));
        double pulse = 0.5 + 0.5 * Math.sin(age * (8 + 26 * k));
        ground.setOpacity(in * (0.45 + 0.55 * pulse));
        rim.setOpacity(in * (0.35 + 0.5 * k));
        field.setOpacity(in);
        fill.setOpacity(in);
        if (age >= lead - FLASH) {
            // white-hot: now
            field.setFill(Color.rgb(255, 230, 180, 0.35));
            ground.setFill(Color.WHITE);
            rim.setFill(Color.WHITE);
            rim.setOpacity(1);
            ground.setOpacity(1);
        }
        if (age >= lead + LINGER) dismiss();
    }
}
