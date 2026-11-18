package at.htl.flowstate.Menu;

import at.htl.flowstate.Assets;
import at.htl.flowstate.Components.Player.LoadoutComponent;
import at.htl.flowstate.Components.Player.PlayerStatsComponent;
import at.htl.flowstate.Game;
import at.htl.flowstate.Skills.SkillManager;
import at.htl.flowstate.Skills.SkillType;
import at.htl.flowstate.Skills.SlotKind;
import com.almasb.fxgl.app.scene.Viewport;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.ui.ProgressBar;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

import static com.almasb.fxgl.dsl.FXGL.*;

/**
 * HP / mana / XP bars, skill points, the 4-slot hotbar, combo meter and charge bar.
 * A hotbar slot on cooldown goes dark and fills back up from the bottom; it is full again when the skill is ready.
 */
public class Hud {
    private static final double SLOT_W = 165;
    private static final double SLOT_H = 58;
    private static final double SLOT_GAP = 8;
    private static final double CHARGE_W = 60;
    private static final Color SLOT_ON = Color.web("#1565C0");
    private static final Color SLOT_OFF = Color.rgb(48, 48, 56, 0.85);   // ready ("full")
    private static final Color SLOT_EMPTY = Color.rgb(8, 8, 10, 0.85);   // just used
    private static final double FLASH_MIN_COOLDOWN = 1.0;  // shorter cooldowns (weapon swings) don't flash
    private static final double FLASH_TIME = 0.3;
    private static final double FLASH_OPACITY = 0.55;
    private static final Color SLOT_BORDER = Color.web("#666");
    private static final int ICON_SCALE = 2;   // 24px icons shown at 48px
    private static final double ICON_SIZE = 24 * ICON_SCALE;
    private static final double SLOT_PAD = 5;

    private final Entity player;
    private final PlayerStatsComponent stats;
    private final LoadoutComponent loadout;
    private int lastSkillPoints = -1;

    private final ProgressBar hpBar = new ProgressBar();
    private final ProgressBar mpBar = new ProgressBar();
    private final ProgressBar xpBar = new ProgressBar();
    private final Text spText = new Text();

    private final Rectangle[] slotBg = new Rectangle[SkillManager.HOTBAR_SIZE];
    private final Text[] slotText = new Text[SkillManager.HOTBAR_SIZE];
    private final ImageView[] slotIcon = new ImageView[SkillManager.HOTBAR_SIZE];
    private final SkillType[] slotIconType = new SkillType[SkillManager.HOTBAR_SIZE];
    private final Rectangle[] slotFill = new Rectangle[SkillManager.HOTBAR_SIZE];
    private final Rectangle[] slotFlash = new Rectangle[SkillManager.HOTBAR_SIZE];
    private final Rectangle[] slotBorder = new Rectangle[SkillManager.HOTBAR_SIZE];
    private final double[] lastCooldown = new double[SkillManager.HOTBAR_SIZE];
    private final double[] flashLeft = new double[SkillManager.HOTBAR_SIZE];

    private final Rectangle comboBg;
    private final Rectangle comboFill;
    private final Rectangle chargeBg = new Rectangle(CHARGE_W, 6, Color.rgb(0, 0, 0, 0.6));
    private final Rectangle chargeFill = new Rectangle(0, 6, Color.GOLD);

    public Hud() {
        double width = getAppWidth();
        double height = getAppHeight();
        player = Game.getPlayer();
        stats = player.getComponent(PlayerStatsComponent.class);
        loadout = player.getComponent(LoadoutComponent.class);

        setupBar(hpBar, Color.web("#921616"), stats.getMaxHealth(), width * 0.82, 50, true);
        setupBar(mpBar, Color.web("#2300d5"), stats.getMaxMana(), width * 0.82, 80, false);
        setupBar(xpBar, Color.DARKGOLDENROD, stats.getMaxExperience(), width * 0.82, 110, true);

        spText.setFill(Color.WHITE);
        spText.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        spText.setLayoutX(width * 0.82);
        spText.setLayoutY(155);
        addUINode(spText);

        double totalWidth = SkillManager.HOTBAR_SIZE * SLOT_W + (SkillManager.HOTBAR_SIZE - 1) * SLOT_GAP;
        double startX = (width - totalWidth) / 2;
        double slotY = height - SLOT_H - 24;
        for (int i = 0; i < SkillManager.HOTBAR_SIZE; i++) {
            slotBg[i] = roundedSlot();
            slotFill[i] = new Rectangle(SLOT_W, 0, SLOT_OFF);
            slotFlash[i] = new Rectangle(SLOT_W, SLOT_H, Color.WHITE);
            slotFlash[i].setOpacity(0);
            Pane cooldownLayer = new Pane(slotFill[i], slotFlash[i]);
            cooldownLayer.setClip(roundedSlot());
            cooldownLayer.setMaxSize(SLOT_W, SLOT_H);
            slotBorder[i] = roundedSlot();
            slotBorder[i].setFill(Color.TRANSPARENT);
            slotText[i] = new Text();
            slotText[i].setFill(Color.WHITE);
            slotText[i].setStyle("-fx-font-size: 13px;");
            slotText[i].setTextAlignment(TextAlignment.LEFT);
            slotText[i].setWrappingWidth(SLOT_W - ICON_SIZE - 3 * SLOT_PAD);
            slotIcon[i] = new ImageView();
            slotIcon[i].setSmooth(false);
            slotIcon[i].setFitWidth(ICON_SIZE);
            slotIcon[i].setFitHeight(ICON_SIZE);
            HBox content = new HBox(SLOT_PAD, slotIcon[i], slotText[i]);
            content.setAlignment(Pos.CENTER_LEFT);
            content.setPadding(new Insets(0, SLOT_PAD, 0, SLOT_PAD));
            content.setMaxSize(SLOT_W, SLOT_H);
            StackPane slot = new StackPane(slotBg[i], cooldownLayer, slotBorder[i], content);
            slot.setAlignment(Pos.CENTER);
            slot.setLayoutX(startX + i * (SLOT_W + SLOT_GAP));
            slot.setLayoutY(slotY);
            addUINode(slot);
        }

        comboBg = new Rectangle(totalWidth, 5, Color.rgb(0, 0, 0, 0.5));
        comboBg.setLayoutX(startX);
        comboBg.setLayoutY(slotY - 10);
        comboFill = new Rectangle(0, 5, Color.ORANGE);
        comboFill.setLayoutX(startX);
        comboFill.setLayoutY(slotY - 10);
        addUINode(comboBg);
        addUINode(comboFill);

        addUINode(chargeBg);
        addUINode(chargeFill);
    }

    private static Rectangle roundedSlot() {
        Rectangle r = new Rectangle(SLOT_W, SLOT_H);
        r.setArcWidth(8);
        r.setArcHeight(8);
        return r;
    }

    private void setupBar(ProgressBar bar, Color color, double max, double x, double y, boolean label) {
        bar.setMinValue(0);
        bar.setMaxValue(max);
        bar.setFill(color);
        bar.setHeight(15);
        bar.setWidth(300);
        bar.setLabelVisible(label);
        bar.setLayoutX(x);
        bar.setLayoutY(y);
        addUINode(bar);
    }

    public void update() {
        SkillManager skills = SkillManager.get();

        hpBar.setCurrentValue(stats.getHealth());
        mpBar.setMaxValue(stats.getMaxMana());
        mpBar.setCurrentValue(stats.getMana());
        xpBar.setMaxValue(stats.getMaxExperience());
        xpBar.setCurrentValue(stats.getExperience());
        int sp = (int) stats.getSkillPoints();
        if (sp != lastSkillPoints) {
            lastSkillPoints = sp;
            spText.setText("Skill points: " + sp + "   [I] Skill tree");
        }

        for (int i = 0; i < SkillManager.HOTBAR_SIZE; i++) {
            SkillType type = skills.getSlot(i);
            boolean active = type != null && type.getSlotKind() == SlotKind.ATTACK && i == loadout.getActiveSlot();
            boolean on = type != null && loadout.isToggleOn(type);

            String name = type == null ? "—" : type.getSlotName();
            String status = type == null ? "" : loadout.getSlotStatus(type);
            slotText[i].setText((i + 1) + "  " + name + (status.isEmpty() ? "" : "\n" + status));
            if (type != slotIconType[i]) {
                slotIconType[i] = type;
                slotIcon[i].setImage(type == null ? null : Assets.pixels(type.getIconName(), ICON_SCALE));
            }

            double cooldown = type == null ? 0 : loadout.getCooldownProgress(type);
            if (cooldown <= 0 && lastCooldown[i] > 0 && loadout.getCooldownTotal(type) >= FLASH_MIN_COOLDOWN) {
                flashLeft[i] = FLASH_TIME;
            }
            lastCooldown[i] = cooldown;
            updateCooldown(i, on, cooldown);

            slotBorder[i].setStroke(active ? Color.GOLD : SLOT_BORDER);
            slotBorder[i].setStrokeWidth(active ? 3 : 1);
        }

        double combo = loadout.getComboProgress();
        comboBg.setVisible(combo > 0);
        comboFill.setVisible(combo > 0);
        comboFill.setWidth(comboBg.getWidth() * combo);

        double charge = loadout.getChargeProgress();
        boolean charging = charge >= 0;
        chargeBg.setVisible(charging);
        chargeFill.setVisible(charging);
        if (charging) {
            Viewport vp = getGameScene().getViewport();
            double x = player.getCenter().getX() - vp.getX() - CHARGE_W / 2;
            double y = player.getY() - vp.getY() - 16;
            chargeBg.setLayoutX(x);
            chargeBg.setLayoutY(y);
            chargeFill.setLayoutX(x);
            chargeFill.setLayoutY(y);
            chargeFill.setWidth(CHARGE_W * charge);
            chargeFill.setFill(charge >= 1 ? Color.ORANGERED : Color.GOLD);
        }
    }

    /** Dark background with the ready color rising from the bottom as the cooldown runs out. */
    private void updateCooldown(int i, boolean on, double cooldown) {
        Color ready = on ? SLOT_ON : SLOT_OFF;
        boolean cooling = cooldown > 0;
        slotBg[i].setFill(cooling ? SLOT_EMPTY : ready);
        double h = cooling ? SLOT_H * (1 - cooldown) : 0;
        slotFill[i].setFill(ready);
        slotFill[i].setHeight(h);
        slotFill[i].setY(SLOT_H - h);

        if (flashLeft[i] > 0) flashLeft[i] = Math.max(0, flashLeft[i] - tpf());
        slotFlash[i].setOpacity(FLASH_OPACITY * flashLeft[i] / FLASH_TIME);
    }
}
