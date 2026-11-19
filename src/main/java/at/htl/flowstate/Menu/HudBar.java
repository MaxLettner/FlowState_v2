package at.htl.flowstate.Menu;

import at.htl.flowstate.Assets;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;

/**
 * One of the HUD bars (tools/art/hudbars.py): a forged iron trough coming out of a gem socket, filled with a
 * slowly flowing liquid. What was just lost stays behind washed out and drains away after a moment; gains fill
 * up quickly instead of jumping. The fill always ends on a whole art pixel.
 */
public final class HudBar extends Pane {
    public static final int SCALE = 2;
    // keep in sync with hudbars.py
    private static final int TROUGH_X = 18;   // where the HP / mana / XP bars have their trough
    private static final int TROUGH_Y = 5;
    private static final int TROUGH_LEN = 128;
    private static final int TILE = 32;

    private static final double GAIN_RATE = 8;       // fraction of the gap closed per second
    private static final double DRAIN_DELAY = 0.45;  // seconds the lost part stays before it drains
    private static final double DRAIN_SPEED = 0.6;   // bar lengths per second

    private final ImageView back;
    private final ImageView drain;
    private final ImageView fill;
    private final ImageView edge;
    private final int troughH;
    private final double flowSpeed;
    private final boolean drains;

    private double shown = -1;
    private double lag;
    private double lagDelay;
    private double flow;
    private int lastScroll = -1, lastFill = -1, lastLag = -1;

    /**
     * @param flowSpeed how fast the liquid flows, in art pixels per second
     * @param drains    false snaps losses away at once (the XP bar empties on a level up)
     */
    public HudBar(String id, double flowSpeed, boolean drains) {
        this(id, TROUGH_X, TROUGH_Y, flowSpeed, drains);
    }

    /** A bar whose art has its (TROUGH_LEN long) trough somewhere else, like the combo meter (hotbar.py). */
    public HudBar(String id, int troughX, int troughY, double flowSpeed, boolean drains) {
        this.flowSpeed = flowSpeed;
        this.drains = drains;
        String strip = "HudFill_" + id + ".png";
        fill = crisp(Assets.frameImage(strip, 3, SCALE, 0));
        drain = crisp(Assets.frameImage(strip, 3, SCALE, 1));
        edge = crisp(Assets.frameImage(strip, 3, SCALE, 2));
        troughH = (int) fill.getImage().getHeight();
        edge.setViewport(new Rectangle2D(0, 0, SCALE, troughH));

        Pane trough = new Pane(drain, fill, edge);
        trough.setLayoutX(troughX * SCALE);
        trough.setLayoutY(troughY * SCALE);
        back = Assets.sprite("HudBar_" + id + ".png", SCALE);
        getChildren().addAll(back, trough);
        setMouseTransparent(true);
    }

    /** The size of the whole bar on screen (the fill never reaches past it). */
    public double barWidth() {
        return back.getImage().getWidth();
    }

    public double barHeight() {
        return back.getImage().getHeight();
    }

    private static ImageView crisp(Image img) {
        ImageView v = new ImageView(img);
        v.setSmooth(false);
        return v;
    }

    public void update(double value, double max, double tpf) {
        double target = max <= 0 ? 0 : Math.max(0, Math.min(1, value / max));
        if (shown < 0) {
            shown = lag = target;
        } else if (target < shown) {
            lag = drains ? Math.max(lag, shown) : target;
            lagDelay = DRAIN_DELAY;
            shown = target;
        } else {
            shown += (target - shown) * Math.min(1, tpf * GAIN_RATE);
            if (target - shown < 0.5 / TROUGH_LEN) shown = target;
        }
        if (lag > shown) {
            if (lagDelay > 0) lagDelay -= tpf;
            else lag = Math.max(shown, lag - DRAIN_SPEED * tpf);
        } else {
            lag = shown;
        }

        flow = (flow + flowSpeed * tpf) % TILE;
        int scroll = (TILE - (int) flow) % TILE;   // the liquid flows to the right
        int n = artPixels(shown);
        int m = artPixels(lag);
        if (scroll == lastScroll && n == lastFill && m == lastLag) return;
        lastScroll = scroll;
        lastFill = n;
        lastLag = m;
        show(fill, scroll, n);
        show(drain, scroll, m);
        edge.setVisible(n > 0 && n < TROUGH_LEN);
        edge.setLayoutX((n - 1) * SCALE);
    }

    private static int artPixels(double fraction) {
        return fraction <= 0 ? 0 : Math.max(1, (int) Math.round(fraction * TROUGH_LEN));
    }

    private void show(ImageView view, int scroll, int artWidth) {
        view.setVisible(artWidth > 0);
        if (artWidth > 0) view.setViewport(new Rectangle2D(scroll * SCALE, 0, artWidth * SCALE, troughH));
    }
}
