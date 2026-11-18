package at.htl.flowstate.Menu;

import at.htl.flowstate.Components.Player.LoadoutComponent;
import at.htl.flowstate.Components.Player.PlayerStatsComponent;
import at.htl.flowstate.Game;
import at.htl.flowstate.Skills.SkillManager;
import at.htl.flowstate.Skills.SkillType;
import at.htl.flowstate.Skills.SlotKind;
import com.almasb.fxgl.app.scene.Viewport;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.ui.ProgressBar;
import javafx.geometry.Pos;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

import static com.almasb.fxgl.dsl.FXGL.*;

/** HP / mana / XP bars, skill points, the 4-slot hotbar, combo meter and charge bar. */
public class Hud {
    private static final double SLOT_W = 130;
    private static final double SLOT_H = 58;
    private static final double SLOT_GAP = 8;
    private static final double CHARGE_W = 60;

    private final ProgressBar hpBar = new ProgressBar();
    private final ProgressBar mpBar = new ProgressBar();
    private final ProgressBar xpBar = new ProgressBar();
    private final Text spText = new Text();

    private final Rectangle[] slotBg = new Rectangle[SkillManager.HOTBAR_SIZE];
    private final Text[] slotText = new Text[SkillManager.HOTBAR_SIZE];

    private final Rectangle comboBg;
    private final Rectangle comboFill;
    private final Rectangle chargeBg = new Rectangle(CHARGE_W, 6, Color.rgb(0, 0, 0, 0.6));
    private final Rectangle chargeFill = new Rectangle(0, 6, Color.GOLD);

    public Hud() {
        double width = getAppWidth();
        double height = getAppHeight();
        PlayerStatsComponent stats = Game.getPlayer().getComponent(PlayerStatsComponent.class);

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
            slotBg[i] = new Rectangle(SLOT_W, SLOT_H);
            slotBg[i].setArcWidth(8);
            slotBg[i].setArcHeight(8);
            slotText[i] = new Text();
            slotText[i].setFill(Color.WHITE);
            slotText[i].setStyle("-fx-font-size: 13px;");
            slotText[i].setTextAlignment(TextAlignment.CENTER);
            slotText[i].setWrappingWidth(SLOT_W - 8);
            StackPane slot = new StackPane(slotBg[i], slotText[i]);
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
        Entity player = Game.getPlayer();
        PlayerStatsComponent stats = player.getComponent(PlayerStatsComponent.class);
        LoadoutComponent loadout = player.getComponent(LoadoutComponent.class);
        SkillManager skills = SkillManager.get();

        hpBar.setCurrentValue(stats.getHealth());
        mpBar.setMaxValue(stats.getMaxMana());
        mpBar.setCurrentValue(stats.getMana());
        xpBar.setMaxValue(stats.getMaxExperience());
        xpBar.setCurrentValue(stats.getExperience());
        spText.setText("Skill points: " + (int) stats.getSkillPoints() + "   [I] Skill tree");

        for (int i = 0; i < SkillManager.HOTBAR_SIZE; i++) {
            SkillType type = skills.getSlot(i);
            boolean active = type != null && type.getSlotKind() == SlotKind.ATTACK && i == loadout.getActiveSlot();
            boolean on = type != null && loadout.isToggleOn(type);

            String name = type == null ? "—" : type.getSlotName();
            String status = type == null ? "" : loadout.getSlotStatus(type);
            slotText[i].setText((i + 1) + "  " + name + (status.isEmpty() ? "" : "\n" + status));

            slotBg[i].setFill(on ? Color.web("#1565C0") : Color.rgb(20, 20, 20, 0.8));
            slotBg[i].setStroke(active ? Color.GOLD : Color.web("#666"));
            slotBg[i].setStrokeWidth(active ? 3 : 1);
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
}
