package at.htl.flowstate.Menu;

import at.htl.flowstate.Assets;
import at.htl.flowstate.Components.Player.LoadoutComponent;
import at.htl.flowstate.Skills.SkillManager;
import at.htl.flowstate.Skills.SkillType;
import at.htl.flowstate.Skills.SlotKind;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.EnumMap;
import java.util.Map;

/**
 * The hotbar (tools/art/hotbar.py): four iron sockets on a forged beam, each showing only its skill's icon.
 * The active attack gets a gold bezel and a toggle that is on an arcane one. A skill on cooldown goes dark and its
 * icon comes back from the bottom up as the cooldown runs out; a long cooldown ends with a white-hot flash.
 * The Fisticuffs combo meter, a thin trough of fire, appears above the sockets while a combo is running.
 */
public final class Hotbar extends Pane {
    public static final int SCALE = 2;
    // keep in sync with hotbar.py
    private static final int SLOTS = SkillManager.HOTBAR_SIZE;
    private static final int PIT = 24;
    private static final int RIM = 4;
    private static final int SOCK = PIT + 2 * RIM;
    private static final int GAP = 6;
    private static final int END = 12;
    private static final int MARGIN = 1;
    private static final int RIM_GOLD = 0, RIM_ARCANE = 1, RIM_FLASH = 2;
    private static final int COMBO_TX = 9, COMBO_TY = 4, COMBO_GAP = 1;

    private static final double FLASH_MIN_COOLDOWN = 1.0;  // shorter cooldowns (weapon swings) don't flash
    private static final double FLASH_TIME = 0.3;
    private static final Color EDGE = Color.web("#fff6d8", 0.6);   // where the icon is coming back
    private static final double COMBO_FADE_IN = 0.12;   // seconds
    private static final double COMBO_FADE_OUT = 0.6;   // long enough to see the lost combo wash out

    private static final Map<SkillType, Image[]> ICONS = new EnumMap<>(SkillType.class);  // {bright, dark}

    private final ImageView back = Assets.sprite("Hotbar.png", SCALE);
    private final Slot[] slots = new Slot[SLOTS];
    private final HudBar combo = new HudBar("COMBO", COMBO_TX, COMBO_TY, 12, true);

    private final class Slot {
        final ImageView dark = crisp();
        final ImageView icon = crisp();
        final Rectangle edge = new Rectangle(PIT * SCALE, SCALE, EDGE);
        final ImageView rim = crisp();
        final ImageView flash = crisp(Assets.frameImage("HotbarRim.png", 3, SCALE, RIM_FLASH));
        SkillType type;
        int rimFrame = -1;
        int shownRows = -1;
        double lastCooldown;
        double flashLeft;

        Slot(int i) {
            Pane pit = new Pane(dark, icon, edge);
            pit.setLayoutX((sockX(i) + RIM) * SCALE);
            pit.setLayoutY((MARGIN + RIM) * SCALE);
            Pane frame = new Pane(rim, flash);
            frame.setLayoutX(sockX(i) * SCALE);
            frame.setLayoutY(MARGIN * SCALE);
            flash.setOpacity(0);
            getChildren().addAll(pit, frame);
        }
    }

    public Hotbar() {
        getChildren().add(back);
        for (int i = 0; i < SLOTS; i++) slots[i] = new Slot(i);
        combo.setLayoutX(END * SCALE);
        combo.setLayoutY(-(combo.barHeight() + COMBO_GAP * SCALE));   // above the bar, outside its size
        combo.setOpacity(0);
        getChildren().add(combo);
        setMouseTransparent(true);
    }

    private static int sockX(int i) {
        return END + i * (SOCK + GAP);
    }

    /** The size of the bar itself, without the combo meter above it. */
    public double barWidth() {
        return back.getImage().getWidth();
    }

    public double barHeight() {
        return back.getImage().getHeight();
    }

    public void update(SkillManager skills, LoadoutComponent loadout, double tpf) {
        for (int i = 0; i < SLOTS; i++) {
            Slot s = slots[i];
            SkillType type = skills.getSlot(i);
            if (type != s.type) {
                s.type = type;
                Image[] img = type == null ? null : ICONS.computeIfAbsent(type, Hotbar::icons);
                s.icon.setImage(img == null ? null : img[0]);
                s.dark.setImage(img == null ? null : img[1]);
                s.shownRows = -1;
                s.lastCooldown = 0;
                s.flashLeft = 0;
            }

            boolean active = type != null && type.getSlotKind() == SlotKind.ATTACK && i == loadout.getActiveSlot();
            boolean on = type != null && loadout.isToggleOn(type);
            int frame = active ? RIM_GOLD : on ? RIM_ARCANE : -1;
            if (frame != s.rimFrame) {
                s.rimFrame = frame;
                s.rim.setImage(frame < 0 ? null : Assets.frameImage("HotbarRim.png", 3, SCALE, frame));
            }

            double cooldown = type == null ? 0 : loadout.getCooldownProgress(type);
            if (cooldown <= 0 && s.lastCooldown > 0 && loadout.getCooldownTotal(type) >= FLASH_MIN_COOLDOWN) {
                s.flashLeft = FLASH_TIME;
            }
            s.lastCooldown = cooldown;
            if (s.flashLeft > 0) s.flashLeft = Math.max(0, s.flashLeft - tpf);
            s.flash.setOpacity(s.flashLeft / FLASH_TIME);
            showCooldown(s, cooldown);
        }

        double comboProgress = loadout.getComboProgress();
        combo.update(comboProgress, 1, tpf);
        double fade = comboProgress > 0 ? tpf / COMBO_FADE_IN : -tpf / COMBO_FADE_OUT;
        combo.setOpacity(Math.max(0, Math.min(1, combo.getOpacity() + fade)));
    }

    /** The icon's lower part is lit up to a whole art pixel, the rest stays dark. */
    private static void showCooldown(Slot s, double cooldown) {
        int rows = cooldown <= 0 ? PIT : (int) Math.round(PIT * (1 - Math.min(1, cooldown)));
        if (rows == s.shownRows) return;
        s.shownRows = rows;
        boolean cooling = rows < PIT;
        s.dark.setVisible(cooling);
        s.icon.setVisible(rows > 0);
        if (rows > 0) s.icon.setViewport(new Rectangle2D(0, (PIT - rows) * SCALE, PIT * SCALE, rows * SCALE));
        s.icon.setLayoutY((PIT - rows) * SCALE);
        s.edge.setVisible(cooling && rows > 0);
        s.edge.setLayoutY((PIT - rows) * SCALE);
    }

    /** The bright icon and a dark, mostly grey copy of it for the part still on cooldown. */
    private static Image[] icons(SkillType type) {
        Image bright = Assets.pixels("HotbarIcon_" + type.name() + ".png", SCALE);
        int w = (int) bright.getWidth(), h = (int) bright.getHeight();
        WritableImage dark = new WritableImage(w, h);
        PixelReader r = bright.getPixelReader();
        PixelWriter pw = dark.getPixelWriter();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = r.getArgb(x, y);
                int red = argb >> 16 & 0xff, green = argb >> 8 & 0xff, blue = argb & 0xff;
                double lum = red * 0.3 + green * 0.59 + blue * 0.11;
                pw.setArgb(x, y, argb & 0xff000000 | darken(red, lum) << 16 | darken(green, lum) << 8 | darken(blue, lum));
            }
        }
        return new Image[]{bright, dark};
    }

    private static int darken(int channel, double lum) {
        return (int) ((channel * 0.3 + lum * 0.7) * 0.4);
    }

    private static ImageView crisp() {
        return crisp(null);
    }

    private static ImageView crisp(Image img) {
        ImageView v = new ImageView(img);
        v.setSmooth(false);
        return v;
    }
}
