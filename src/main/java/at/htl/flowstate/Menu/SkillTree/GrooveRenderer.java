package at.htl.flowstate.Menu.SkillTree;

import at.htl.flowstate.Skills.SkillType;
import javafx.geometry.Point2D;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Draws the grooves between the skill nodes as pixel art at half resolution (shown x2, the same pixel size as
 * the icons): every connection is carved into the stone; owned ones are filled with molten rock, learnable
 * ones glow dimly. The same look as groove_stamp() in tools/art/skilltree.py.
 */
final class GrooveRenderer {
    enum Fill { NONE, DORMANT, MOLTEN }

    private static final int SCALE = 2;
    private static final int W = (int) SkillTreeLayout.WIDTH / SCALE;
    private static final int H = (int) SkillTreeLayout.HEIGHT / SCALE;
    private static final double STEP = 0.4;

    // stone ramp / ember ramp of the background
    private static final int LIP = 0xFF6F6A78;
    private static final int WALL = 0xFF121116;
    private static final int FLOOR = 0xFF26242C;
    private static final int[] MOLTEN = {0xFFD1361A, 0xFFFF7A1F, 0xFFFFC23D};
    private static final double[] MOLTEN_SHRINK = {0.9, 1.6, 2.3};
    private static final int[] DORMANT = {0xFF3E0D0B, 0xFF7A1A12};
    private static final double[] DORMANT_SHRINK = {1.0, 2.0};
    private static final int GLOW = 0xFFFF7A1F;

    private final Map<SkillType, List<Point2D>> paths = new EnumMap<>(SkillType.class);
    private final int[] carved = new int[W * H];
    private final int[] small = new int[W * H];
    private final int[] full = new int[W * H * SCALE * SCALE];

    final WritableImage lines = new WritableImage(W * SCALE, H * SCALE);
    /** Only the molten pixels, meant to be blurred and added on top as a glow. */
    final WritableImage glow = new WritableImage(W * SCALE, H * SCALE);

    GrooveRenderer() {
        for (SkillType t : SkillType.values()) {
            if (SkillTreeLayout.parent(t) == null) continue;
            List<Point2D> pts = new ArrayList<>();
            for (Point2D p : SkillTreeLayout.groove(t, STEP * SCALE)) pts.add(p.multiply(1.0 / SCALE));
            paths.put(t, pts);
        }
        // layer by layer over all grooves, so where they meet the channels merge instead of overlapping
        paths.forEach((t, pts) -> stamp(carved, pts, SkillTreeLayout.grooveRadius(t) + 0.5, 1, 1, LIP));
        paths.forEach((t, pts) -> stamp(carved, pts, SkillTreeLayout.grooveRadius(t), 0, 0, WALL));
        paths.forEach((t, pts) -> stamp(carved, pts, SkillTreeLayout.grooveRadius(t) - 1, 0.5, 0.5, FLOOR));
    }

    void render(Map<SkillType, Fill> fills) {
        System.arraycopy(carved, 0, small, 0, small.length);
        for (Fill pass : new Fill[]{Fill.DORMANT, Fill.MOLTEN}) {  // molten wins where grooves meet
            int[] colors = pass == Fill.MOLTEN ? MOLTEN : DORMANT;
            double[] shrink = pass == Fill.MOLTEN ? MOLTEN_SHRINK : DORMANT_SHRINK;
            for (int layer = 0; layer < colors.length; layer++) {
                for (Map.Entry<SkillType, List<Point2D>> e : paths.entrySet()) {
                    if (fills.getOrDefault(e.getKey(), Fill.NONE) != pass) continue;
                    double r = SkillTreeLayout.grooveRadius(e.getKey()) - shrink[layer];
                    stamp(small, e.getValue(), r, 0, 0, colors[layer]);
                }
            }
        }
        write(lines, small);

        java.util.Arrays.fill(small, 0);
        paths.forEach((t, pts) -> {
            if (fills.getOrDefault(t, Fill.NONE) == Fill.MOLTEN) stamp(small, pts, SkillTreeLayout.grooveRadius(t) - 0.5, 0, 0, GLOW);
        });
        write(glow, small);
    }

    /** Filled discs along the path (pixel centres inside radius r). */
    private static void stamp(int[] buf, List<Point2D> pts, double r, double ox, double oy, int argb) {
        if (r <= 0) return;
        double r2 = r * r;
        for (Point2D p : pts) {
            double cx = p.getX() + ox, cy = p.getY() + oy;
            int x0 = Math.max(0, (int) Math.floor(cx - r) - 1), x1 = Math.min(W - 1, (int) Math.ceil(cx + r) + 1);
            int y0 = Math.max(0, (int) Math.floor(cy - r) - 1), y1 = Math.min(H - 1, (int) Math.ceil(cy + r) + 1);
            for (int y = y0; y <= y1; y++) {
                double dy = y + 0.5 - cy;
                for (int x = x0; x <= x1; x++) {
                    double dx = x + 0.5 - cx;
                    if (dx * dx + dy * dy <= r2) buf[y * W + x] = argb;
                }
            }
        }
    }

    private void write(WritableImage img, int[] src) {
        int fw = W * SCALE;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int c = src[y * W + x];
                int o = y * SCALE * fw + x * SCALE;
                full[o] = c;
                full[o + 1] = c;
                full[o + fw] = c;
                full[o + fw + 1] = c;
            }
        }
        img.getPixelWriter().setPixels(0, 0, fw, H * SCALE, PixelFormat.getIntArgbInstance(), full, 0, fw);
    }

    /** The groove leading to {@code child} in screen pixels (for sparks travelling along it). */
    List<Point2D> screenPath(SkillType child) {
        List<Point2D> out = new ArrayList<>();
        for (Point2D p : paths.get(child)) out.add(p.multiply(SCALE));
        return out;
    }
}
