package at.htl.flowstate.Menu.SkillTree;

import at.htl.flowstate.Skills.SkillManager;
import at.htl.flowstate.Skills.SkillType;
import javafx.geometry.Point2D;

import java.util.ArrayList;
import java.util.List;

/**
 * Where every node sits on the 1920x1080 skill tree screen and how the grooves between them curve.
 * The Rusty Sword is the root at the bottom; each branch grows stat node, sub-trees, then a fan of three skills.
 * Keep the numbers in sync with tools/art/skilltree.py (the background's carved trunk sits below ROOT).
 */
final class SkillTreeLayout {
    static final double WIDTH = 1920;
    static final double HEIGHT = 1080;

    static final Point2D ROOT = new Point2D(960, 880);
    private static final double[] BRANCH_X = {345, 960, 1575};   // by Branch ordinal: melee, ranged, magic
    private static final double[] STAT_Y = {670, 640, 670};
    private static final double SUB_DX = 195;
    private static final double[] SUB_Y = {430, 400, 430};       // left, middle, right sub-tree
    private static final double[][] SKILL_OFF = {{-58, -125}, {0, -195}, {58, -125}};

    private SkillTreeLayout() {}

    static Point2D position(SkillType t) {
        int b = t.getBranch().ordinal();
        return switch (t.getKind()) {
            case START -> ROOT;
            case STAT -> new Point2D(BRANCH_X[b], STAT_Y[b]);
            case SUBTREE -> {
                int i = SkillType.subtreesOf(t.getBranch()).indexOf(t);
                yield new Point2D(BRANCH_X[b] + (i - 1) * SUB_DX, SUB_Y[i]);
            }
            case SKILL -> {
                int i = SkillType.skillsOf(t.getParent()).indexOf(t);
                yield position(t.getParent()).add(SKILL_OFF[i][0], SKILL_OFF[i][1]);
            }
        };
    }

    /** The node whose groove leads to {@code t} (the root for stat nodes), or null for the root itself. */
    static SkillType parent(SkillType t) {
        return switch (t.getKind()) {
            case START -> null;
            case STAT -> SkillType.RUSTY_SWORD;
            case SUBTREE -> SkillManager.statOf(t.getBranch());
            case SKILL -> t.getParent();
        };
    }

    /** Groove half-width in groove pixels (half resolution): thick boughs near the root, thin twigs at the top. */
    static double grooveRadius(SkillType child) {
        return switch (child.getKind()) {
            case STAT -> 3.5;
            case SUBTREE -> 3.0;
            default -> 2.5;
        };
    }

    /** Points along the groove from the parent to {@code child} in screen pixels, about {@code step} apart. */
    static List<Point2D> groove(SkillType child, double step) {
        Point2D p0 = position(parent(child));
        Point2D p3 = position(child);
        double dx = p3.getX() - p0.getX();
        Point2D p1, p2;
        switch (child.getKind()) {
            case STAT -> {
                if (Math.abs(dx) < 1) {
                    p1 = p0;
                    p2 = p3;
                } else {
                    // a bough: rises out of the trunk, sweeps sideways and meets the stat node from the inside
                    double s = Math.signum(dx);
                    p1 = p0.add(s * 40, -150);
                    p2 = p3.add(-s * 220, 30);
                }
            }
            case SUBTREE -> {
                p1 = p0.add(0, -90);
                p2 = p3.add(0, 90);
            }
            default -> {
                p1 = p0.add(dx * 0.15, -45);
                p2 = p3.add(0, 45);
            }
        }
        double length = p0.distance(p1) + p1.distance(p2) + p2.distance(p3);
        int n = Math.max(2, (int) (length / step));
        List<Point2D> pts = new ArrayList<>(n + 1);
        for (int i = 0; i <= n; i++) {
            double t = (double) i / n, u = 1 - t;
            double a = u * u * u, b = 3 * u * u * t, c = 3 * u * t * t, d = t * t * t;
            pts.add(new Point2D(a * p0.getX() + b * p1.getX() + c * p2.getX() + d * p3.getX(),
                    a * p0.getY() + b * p1.getY() + c * p2.getY() + d * p3.getY()));
        }
        return pts;
    }
}
