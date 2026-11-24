package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Assets;
import com.almasb.fxgl.entity.Entity;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.util.List;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;

/**
 * One of the Tar Hydra's necks, strung every frame from pieces (tools/art/hydra.py) along a curve from its base in
 * the tar to its head or stump: the fin spikes along its back first, then every outline disc, then the fill discs
 * (no outline of their own, so they merge into one smooth neck), then the bone belly plates. The curve leaves the
 * tar straight up and arrives at the head along the way the head points.
 */
final class HydraNeck {
    private static final int DISC_FRAMES = 21, SCUTE_FRAMES = 11, FIN_FRAMES = 2;
    private static final double DISC_SIZE = 128, SCUTE_SIZE = 64, FIN_W = 40, FIN_H = 60, FIN_BASE = 28;
    private static final int SAMPLES = 64;
    private static final int SCALE = 2;                        // shown at x2 like every demon; radii below are world px
    private static Image[] edgeImg, fillImg, scuteImg, finImg;

    private final Entity holder;
    private final Group fins = new Group(), edges = new Group(), fills = new Group(), scutes = new Group();
    private final double baseRadius, endRadius;
    private final double[] xs = new double[SAMPLES + 1], ys = new double[SAMPLES + 1], len = new double[SAMPLES + 1];

    HydraNeck(double baseRadius, double endRadius, int zIndex) {
        this.baseRadius = baseRadius;
        this.endRadius = endRadius;
        if (edgeImg == null) {
            edgeImg = images("HydraNeckEdge.png", DISC_FRAMES);
            fillImg = images("HydraNeck.png", DISC_FRAMES);
            scuteImg = images("HydraScute.png", SCUTE_FRAMES);
            finImg = images("HydraFin.png", FIN_FRAMES);
        }
        // placed at the arena, not at 0: Game's cleanup removes whatever is far behind the camera
        holder = entityBuilder().at(HydraArena.lockX(), 0).zIndex(zIndex).buildAndAttach();
        holder.getViewComponent().addChild(new Group(fins, edges, fills, scutes));
    }

    private static Image[] images(String file, int count) {
        Image[] out = new Image[count];
        for (int i = 0; i < count; i++) out[i] = Assets.frameImage(file, count, SCALE, i);
        return out;
    }

    /**
     * Strings the neck from {@code base} to {@code end}, arriving along {@code dir} (a unit vector, the way the head
     * points). {@code facing}: the side the head looks to, so the belly plates go on the underside.
     */
    void update(Point2D base, Point2D end, Point2D dir, int facing) {
        holder.getViewComponent().setVisible(true);
        double ox = holder.getX();
        double l = base.distance(end);
        Point2D p1 = base.add(0, -l * 0.45);
        Point2D p2 = end.subtract(dir.multiply(l * 0.45));
        len[0] = 0;
        for (int i = 0; i <= SAMPLES; i++) {
            double t = i / (double) SAMPLES, u = 1 - t;
            xs[i] = u * u * u * base.getX() + 3 * u * u * t * p1.getX() + 3 * u * t * t * p2.getX() + t * t * t * end.getX() - ox;
            ys[i] = u * u * u * base.getY() + 3 * u * u * t * p1.getY() + 3 * u * t * t * p2.getY() + t * t * t * end.getY();
            if (i > 0) len[i] = len[i - 1] + Math.hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1]);
        }
        double total = len[SAMPLES];
        int nf = 0, nd = 0, ns = 0;
        // fins along the back, every 0.8 r
        for (double s = step(0, 0.8); s < total - 4; s += step(s / total, 0.8)) {
            double t = s / total, r = radius(t);
            double[] p = at(s), d = dirAt(s);
            double bx = -d[1] * facing, by = d[0] * facing;            // the belly side; the back is the other way
            double fx = p[0] - bx * r * 0.55, fy = p[1] - by * r * 0.55;
            double deg = Math.toDegrees(Math.atan2(-bx, by));
            ImageView v = view(fins, nf++);
            v.setImage(finImg[nf % FIN_FRAMES]);
            double rad = Math.toRadians(deg);
            double cx = fx + FIN_BASE * Math.sin(rad), cy = fy - FIN_BASE * Math.cos(rad);
            v.setLayoutX(cx - FIN_W / 2);
            v.setLayoutY(cy - FIN_H / 2);
            v.setRotate(deg);
        }
        // the discs, every 0.4 r: outlines first, then the fills
        for (double s = 0; s <= total; s += Math.max(4, radius(s / total) * 0.4)) {
            double r = radius(s / total);
            double[] p = at(s);
            int i = (int) Math.max(0, Math.min(DISC_FRAMES - 1, Math.round(r / SCALE - 10)));      // frame i: art radius 10 + i
            place(view(edges, nd), edgeImg[i], p, DISC_SIZE);
            place(view(fills, nd), fillImg[i], p, DISC_SIZE);
            nd++;
        }
        // belly plates, every 0.5 r
        for (double s = step(0, 0.5); s < total - 4; s += step(s / total, 0.5)) {
            double r = radius(s / total);
            double[] p = at(s), d = dirAt(s);
            double bx = -d[1] * facing, by = d[0] * facing;
            int i = (int) Math.max(0, Math.min(SCUTE_FRAMES - 1, Math.round(r * 0.34 / SCALE - 4)));   // art radius 4 + i
            place(view(scutes, ns++), scuteImg[i], new double[]{p[0] + bx * r * 0.55, p[1] + by * r * 0.55}, SCUTE_SIZE);
        }
        hideFrom(fins, nf);
        hideFrom(edges, nd);
        hideFrom(fills, nd);
        hideFrom(scutes, ns);
    }

    void hide() {
        holder.getViewComponent().setVisible(false);
    }

    void remove() {
        if (holder.isActive()) holder.removeFromWorld();
    }

    private double radius(double t) {
        return baseRadius + (endRadius - baseRadius) * t;
    }

    private double step(double t, double k) {
        return Math.max(6, radius(t) * k);
    }

    private double[] at(double s) {
        int i = index(s);
        double seg = len[i + 1] - len[i];
        double k = seg <= 0 ? 0 : (s - len[i]) / seg;
        return new double[]{xs[i] + (xs[i + 1] - xs[i]) * k, ys[i] + (ys[i + 1] - ys[i]) * k};
    }

    private double[] dirAt(double s) {
        int i = index(s);
        int a = Math.max(0, i - 2), b = Math.min(SAMPLES, i + 3);
        double dx = xs[b] - xs[a], dy = ys[b] - ys[a], l = Math.hypot(dx, dy);
        return l == 0 ? new double[]{0, -1} : new double[]{dx / l, dy / l};
    }

    private int index(double s) {
        int i = 0;
        while (i < SAMPLES - 1 && len[i + 1] < s) i++;
        return i;
    }

    private static ImageView view(Group g, int i) {
        List<Node> kids = g.getChildren();
        while (kids.size() <= i) {
            ImageView v = new ImageView();
            v.setSmooth(false);
            kids.add(v);
        }
        ImageView v = (ImageView) kids.get(i);
        v.setVisible(true);
        return v;
    }

    private static void place(ImageView v, Image img, double[] p, double size) {
        v.setImage(img);
        v.setLayoutX(Math.round(p[0] - size / 2));
        v.setLayoutY(Math.round(p[1] - size / 2));
    }

    private static void hideFrom(Group g, int n) {
        List<Node> kids = g.getChildren();
        for (int i = n; i < kids.size(); i++) kids.get(i).setVisible(false);
    }
}
