package at.htl.flowstate.Menu;

import at.htl.flowstate.Components.Player.LoadoutComponent;
import at.htl.flowstate.Components.Player.PlayerStatsComponent;
import at.htl.flowstate.Game;
import at.htl.flowstate.Skills.SkillManager;
import com.almasb.fxgl.app.scene.Viewport;
import com.almasb.fxgl.entity.Entity;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import static com.almasb.fxgl.dsl.FXGL.*;

/** HP / mana / XP bars in the top right, the hotbar (with the combo meter) at the bottom and the charge bar. */
public class Hud {
    private static final double CHARGE_W = 60;
    private static final double HOTBAR_BOTTOM = 24;   // screen px between the hotbar and the bottom edge
    private static final double BARS_RIGHT = 36;   // screen px between the bars and the right edge
    private static final double BARS_TOP = 30;
    private static final double BARS_GAP = 2 * HudBar.SCALE;

    private final Entity player;
    private final PlayerStatsComponent stats;
    private final LoadoutComponent loadout;

    private final HudBar hpBar = new HudBar("HP", 5, true);
    private final HudBar mpBar = new HudBar("MP", 8, true);
    private final HudBar xpBar = new HudBar("XP", 3, false);

    private final Hotbar hotbar = new Hotbar();

    private final Rectangle chargeBg = new Rectangle(CHARGE_W, 6, Color.rgb(0, 0, 0, 0.6));
    private final Rectangle chargeFill = new Rectangle(0, 6, Color.GOLD);

    public Hud() {
        double width = getAppWidth();
        double height = getAppHeight();
        player = Game.getPlayer();
        stats = player.getComponent(PlayerStatsComponent.class);
        loadout = player.getComponent(LoadoutComponent.class);

        double barY = BARS_TOP;
        for (HudBar bar : new HudBar[]{hpBar, mpBar, xpBar}) {
            bar.setLayoutX(width - BARS_RIGHT - bar.barWidth());
            bar.setLayoutY(barY);
            barY += bar.barHeight() + BARS_GAP;
            addUINode(bar);
        }

        double hotbarX = Math.round((width - hotbar.barWidth()) / 2);
        double hotbarY = height - HOTBAR_BOTTOM - hotbar.barHeight();
        hotbar.setLayoutX(hotbarX);
        hotbar.setLayoutY(hotbarY);
        addUINode(hotbar);

        addUINode(chargeBg);
        addUINode(chargeFill);
    }

    public void update() {
        hpBar.update(stats.getHealth(), stats.getMaxHealth(), tpf());
        mpBar.update(stats.getMana(), stats.getMaxMana(), tpf());
        xpBar.update(stats.getExperience(), stats.getMaxExperience(), tpf());

        hotbar.update(SkillManager.get(), loadout, tpf());

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
