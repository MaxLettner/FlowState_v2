package at.htl.flowstate.Menu;

import at.htl.flowstate.Assets;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Text in the 5x7 pixel font of the rune menus (tools/art/pixfont.py, PixelFont.png), with the same looks the
 * menu art uses (tools/art/menu.py): molten letters for titles, pale letters inlaid into the stone for body text,
 * and stone keycaps. Text is upper-cased, drawn in art pixels and upscaled by a whole factor, so it stays crisp.
 * Every image has one art pixel of room around the letters for the rim and shadow.
 */
public final class PixelText {
    public static final int GLYPH_H = 7;
    private static final int LINE_GAP = 3;   // art px between two lines
    private static final int PAD = 1;
    private static final int CELL_W = 6, CELL_H = 8, COLUMNS = 16;

    // tools/art palettes (skilltree.WALL, MORTAR, EMBER, menu.PARCHMENT)
    private static final int[] WALL = argb("#8c8794", "#6f6a78", "#57535f", "#44404b", "#34313a", "#26242c",
            "#1b1a20", "#121116");
    private static final int MORTAR = argb("#0b0a0e")[0];
    private static final int[] EMBER = argb("#fff1b0", "#ffc23d", "#ff7a1f", "#d1361a", "#7a1a12", "#3e0d0b");

    /** Glowing molten rock with a dark carved rim, like the titles and hovered buttons. */
    public static final Look MOLTEN = new Look(
            new int[]{EMBER[1], EMBER[1], EMBER[2], EMBER[2], EMBER[2], EMBER[3], EMBER[3]}, WALL[7], EMBER[5]);
    /** The pale body text of the menus. */
    public static final Look PARCHMENT = inlaid(Color.web("#eadcb6"), Color.web("#cbb88d"));

    /**
     * How letters are painted: a colour per glyph row, an optional rim around the letters and an optional
     * shadow one pixel down and right (0 = none).
     */
    public record Look(int[] rows, int rim, int shadow) {}

    /** Letters set into the stone: {@code top} on the upper three rows, {@code bottom} below, dark shadow. */
    public static Look inlaid(Color top, Color bottom) {
        int t = argb(top), b = argb(bottom);
        return new Look(new int[]{t, t, t, b, b, b, b}, 0, WALL[7]);
    }

    /** Inlaid letters in one colour, a little darker on the lower rows. */
    public static Look inlaid(Color color) {
        return inlaid(color, color.deriveColor(0, 1.1, 0.84, 1));
    }

    private static boolean[][][] glyphs;   // [char - 32][x][y], null where the font has no glyph

    private PixelText() {}

    public static ImageView view(String text, Look look, int scale) {
        return view(render(text, look, scale, 0, false));
    }

    public static ImageView view(Image img) {
        ImageView v = new ImageView(img);
        v.setSmooth(false);
        return v;
    }

    public static Image render(String text, Look look, int scale) {
        return render(text, look, scale, 0, false);
    }

    /**
     * @param wrapWidth wrap words to lines at most this wide on screen (0 = one line per '\n')
     * @param center    centre every line in the widest one
     */
    public static Image render(String text, Look look, int scale, double wrapWidth, boolean center) {
        load();
        int maxArt = wrapWidth > 0 ? (int) (wrapWidth / scale) - 2 * PAD : Integer.MAX_VALUE;
        List<String> lines = wrap(clean(text), maxArt);
        int textW = 0;
        for (String l : lines) textW = Math.max(textW, width(l));
        int w = textW + 2 * PAD;
        int h = lines.size() * GLYPH_H + (lines.size() - 1) * LINE_GAP + 2 * PAD;
        Set<Integer> cells = new HashSet<>();
        int[] row = new int[w * h];
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            int x = PAD + (center ? (textW - width(l)) / 2 : 0);
            int y = PAD + i * (GLYPH_H + LINE_GAP);
            for (char c : l.toCharArray()) {
                boolean[][] g = glyphs[c - 32];
                for (int gx = 0; gx < g.length; gx++) {
                    for (int gy = 0; gy < GLYPH_H; gy++) {
                        if (g[gx][gy]) {
                            cells.add((y + gy) * w + x + gx);
                            row[(y + gy) * w + x + gx] = gy;
                        }
                    }
                }
                x += g.length + 1;
            }
        }
        int[] art = new int[w * h];
        for (int cell : cells) {
            int x = cell % w, y = cell / w;
            if (look.rim != 0) {
                for (int[] d : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
                    paintIfFree(art, cells, w, h, x + d[0], y + d[1], look.rim);
                }
            }
            if (look.shadow != 0) paintIfFree(art, cells, w, h, x + 1, y + 1, look.shadow);
        }
        for (int cell : cells) art[cell] = look.rows[row[cell]];
        return upscale(art, w, h, scale);
    }

    /** A stone keycap with the key's name carved into it (menu.py keycap). */
    public static Image keycap(String key, int scale) {
        load();
        String k = clean(key);
        int w = width(k) + 6, h = 11;
        int[] art = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int dl = x, dr = w - 1 - x, dt = y, db = h - 1 - y;
                int d = Math.min(Math.min(dl, dr), Math.min(dt, db));
                if (Math.min(dl, dr) + Math.min(dt, db) == 0) continue;   // cut corners
                int c;
                if (d == 0) c = MORTAR;
                else if (db == 1) c = WALL[4];                              // the key's front side
                else if (dl == 1 || dt == 1) c = WALL[0];
                else if (dr == 1) c = WALL[3];
                else c = (x * 7 + y * 3) % 11 != 0 ? WALL[1] : WALL[2];
                art[y * w + x] = c;
            }
        }
        int x = 3;
        for (char c : k.toCharArray()) {
            boolean[][] g = glyphs[c - 32];
            for (int gx = 0; gx < g.length; gx++) {
                for (int gy = 0; gy < GLYPH_H; gy++) {
                    if (g[gx][gy]) art[(2 + gy) * w + x + gx] = WALL[7];
                }
            }
            x += g.length + 1;
        }
        return upscale(art, w, h, scale);
    }

    /** The mouse with its left or right button lit, as on the controls tablet. */
    public static Image mouse(boolean right, int scale) {
        return Assets.frameImage("MouseIcon.png", 2, scale, right ? 1 : 0);
    }

    private static void paintIfFree(int[] art, Set<Integer> cells, int w, int h, int x, int y, int c) {
        if (x < 0 || y < 0 || x >= w || y >= h || cells.contains(y * w + x)) return;
        art[y * w + x] = c;
    }

    /** Upper case, and characters the font lacks become '?'. */
    private static String clean(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toUpperCase().toCharArray()) {
            if (c == '\n') sb.append(c);
            else if (c >= 32 && c < 128 && glyphs[c - 32] != null) sb.append(c);
            else sb.append('?');
        }
        return sb.toString();
    }

    private static int width(String line) {
        int w = 0;
        for (char c : line.toCharArray()) w += glyphs[c - 32].length + 1;
        return Math.max(0, w - 1);
    }

    private static List<String> wrap(String text, int maxArt) {
        List<String> lines = new ArrayList<>();
        for (String para : text.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                if (word.isEmpty()) continue;
                String next = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && width(next) > maxArt) {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(next);
                }
            }
            lines.add(line.toString());
        }
        return lines;
    }

    private static Image upscale(int[] art, int w, int h, int scale) {
        WritableImage img = new WritableImage(w * scale, h * scale);
        PixelWriter pw = img.getPixelWriter();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = art[y * w + x];
                if (c == 0) continue;
                for (int sy = 0; sy < scale; sy++) {
                    for (int sx = 0; sx < scale; sx++) pw.setArgb(x * scale + sx, y * scale + sy, c);
                }
            }
        }
        return img;
    }

    private static void load() {
        if (glyphs != null) return;
        PixelReader r = Assets.raw("PixelFont.png").getPixelReader();
        glyphs = new boolean[96][][];
        for (int i = 0; i < 96; i++) {
            int ox = (i % COLUMNS) * CELL_W, oy = (i / COLUMNS) * CELL_H;
            int gw = 0;
            while (gw < CELL_W && (r.getArgb(ox + gw, oy + CELL_H - 1) >>> 24) != 0) gw++;
            if (gw == 0) continue;
            boolean[][] g = new boolean[gw][GLYPH_H];
            for (int x = 0; x < gw; x++) {
                for (int y = 0; y < GLYPH_H; y++) g[x][y] = (r.getArgb(ox + x, oy + y) >>> 24) != 0;
            }
            glyphs[i] = g;
        }
    }

    private static int argb(Color c) {
        return ((int) Math.round(c.getOpacity() * 255) << 24) | ((int) Math.round(c.getRed() * 255) << 16)
                | ((int) Math.round(c.getGreen() * 255) << 8) | (int) Math.round(c.getBlue() * 255);
    }

    private static int[] argb(String... hexes) {
        int[] out = new int[hexes.length];
        for (int i = 0; i < hexes.length; i++) out[i] = argb(Color.web(hexes[i]));
        return out;
    }
}
