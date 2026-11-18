package at.htl.flowstate.Menu.SkillTree;

import at.htl.flowstate.Assets;
import at.htl.flowstate.Components.Player.PlayerStatsComponent;
import at.htl.flowstate.Game;
import at.htl.flowstate.Skills.Branch;
import at.htl.flowstate.Skills.NodeKind;
import at.htl.flowstate.Skills.SkillManager;
import at.htl.flowstate.Skills.SkillType;
import com.almasb.fxgl.dsl.FXGL;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

/**
 * Skill tree overlay (I). One tab per branch: stat node, three sub-trees with three skills each.
 * Click to buy. Click an owned skill to put it in the first free hotbar slot,
 * or hover it and press 1-4 to put it in a specific slot.
 */
public class SkillTreeView {
    private static final double WIDTH = 940;
    private static final int ICON_SCALE = 2;   // 24px icons shown at 48px
    private static final ColorAdjust LOCKED = new ColorAdjust(0, -0.85, -0.35, 0);
    private static final String HINT = "Click to buy. Click an owned skill to equip it, or hover it and press 1–4 to put it in that hotbar slot.";

    private final SkillManager skills = SkillManager.get();
    private final PlayerStatsComponent stats;

    private final VBox root = new VBox(14);
    private final Text spText = new Text();
    private final HBox tabs = new HBox(12);
    private final VBox content = new VBox(14);
    private final HBox hotbarRow = new HBox(10);
    private final Text infoText = new Text(HINT);

    private Branch branch = Branch.MELEE;
    private SkillType hovered = null;
    private boolean open = false;

    public SkillTreeView() {
        stats = Game.getPlayer().getComponent(PlayerStatsComponent.class);

        root.setAlignment(Pos.TOP_CENTER);
        root.setPrefWidth(WIDTH);
        root.setStyle("-fx-background-color: rgba(0,0,0,0.88); -fx-padding: 20;");
        root.setLayoutX(FXGL.getAppWidth() / 2.0 - WIDTH / 2);
        root.setLayoutY(30);

        Text title = new Text("Skill Tree");
        title.setFill(Color.WHITE);
        title.setStyle("-fx-font-size: 32px; -fx-font-weight: bold;");

        spText.setFill(Color.GOLD);
        spText.setStyle("-fx-font-size: 18px;");

        tabs.setAlignment(Pos.CENTER);
        content.setAlignment(Pos.TOP_CENTER);
        hotbarRow.setAlignment(Pos.CENTER);

        infoText.setFill(Color.LIGHTGRAY);
        infoText.setStyle("-fx-font-size: 14px;");
        infoText.setWrappingWidth(WIDTH - 60);
        infoText.setTextAlignment(TextAlignment.CENTER);

        root.getChildren().addAll(title, spText, tabs, content, hotbarRow, infoText);

        skills.addListener(this::refresh);
        stats.addSkillPointsListener(this::refresh);
        refresh();
    }

    //-----Open / close-----
    public void open() {
        open = true;
        refresh();
        FXGL.getGameScene().addUINode(root);
    }

    public void close() {
        open = false;
        hovered = null;
        FXGL.getGameScene().removeUINode(root);
    }

    public boolean isOpen() {
        return open;
    }

    public SkillType getHoveredSkill() {
        return hovered;
    }

    //-----Building-----
    private void refresh() {
        spText.setText("Skill points: " + (int) stats.getSkillPoints());

        tabs.getChildren().clear();
        for (Branch b : Branch.values()) {
            Button tab = new Button(b.getDisplayName());
            tab.setPrefWidth(160);
            tab.setStyle("-fx-font-size: 16px; -fx-text-fill: white; -fx-background-color: "
                    + (b == branch ? "#1565C0;" : "#444;"));
            tab.setOnAction(e -> {
                branch = b;
                refresh();
            });
            tabs.getChildren().add(tab);
        }

        content.getChildren().clear();
        content.getChildren().add(nodeButton(SkillManager.statOf(branch), 300));

        HBox subtrees = new HBox(14);
        subtrees.setAlignment(Pos.TOP_CENTER);
        for (SkillType subtree : SkillType.subtreesOf(branch)) {
            VBox box = new VBox(8);
            box.setAlignment(Pos.TOP_CENTER);
            box.setStyle("-fx-border-color: #666; -fx-border-width: 1; -fx-padding: 10;");
            box.getChildren().add(nodeButton(subtree, 270));
            for (SkillType skill : SkillType.skillsOf(subtree)) box.getChildren().add(nodeButton(skill, 270));
            subtrees.getChildren().add(box);
        }
        content.getChildren().add(subtrees);

        hotbarRow.getChildren().clear();
        Text label = new Text("Hotbar:");
        label.setFill(Color.WHITE);
        label.setStyle("-fx-font-size: 14px;");
        hotbarRow.getChildren().addAll(label, nodeButton(SkillType.RUSTY_SWORD, 150));
        for (int i = 0; i < SkillManager.HOTBAR_SIZE; i++) {
            SkillType t = skills.getSlot(i);
            Text slot = new Text("[" + (i + 1) + "] " + (t == null ? "—" : t.getSlotName()));
            slot.setFill(Color.LIGHTBLUE);
            slot.setStyle("-fx-font-size: 14px;");
            HBox entry = new HBox(4);
            entry.setAlignment(Pos.CENTER);
            if (t != null) entry.getChildren().add(Assets.sprite(t.getIconName(), 1));
            entry.getChildren().add(slot);
            hotbarRow.getChildren().add(entry);
        }
    }

    private Button nodeButton(SkillType type, double width) {
        Button btn = new Button(label(type));
        btn.setPrefWidth(width);
        btn.setMinHeight(58);
        ImageView icon = Assets.sprite(type.getIconName(), ICON_SCALE);
        if (!skills.owns(type) && !skills.canBuy(type, stats.getSkillPoints())) icon.setEffect(LOCKED);
        btn.setGraphic(icon);
        btn.setContentDisplay(ContentDisplay.LEFT);
        btn.setGraphicTextGap(10);
        btn.setWrapText(true);
        btn.setTextAlignment(TextAlignment.CENTER);
        btn.setStyle(style(type));
        btn.setOnMouseEntered(e -> {
            hovered = type;
            infoText.setText(type.getDisplayName() + ": " + type.getDescription());
        });
        btn.setOnMouseExited(e -> {
            if (hovered == type) hovered = null;
            infoText.setText(HINT);
        });
        btn.setOnAction(e -> onClick(type));
        return btn;
    }

    private void onClick(SkillType type) {
        if (type.getKind() == NodeKind.STAT || !skills.owns(type)) {
            skills.buy(type);
            return;
        }
        if (!type.isUsable() || skills.slotOf(type) >= 0) return;
        int empty = skills.firstEmptySlot();
        if (empty >= 0) {
            skills.assign(type, empty);
        } else {
            infoText.setText("Hotbar is full: hover " + type.getDisplayName() + " and press 1–4 to replace a slot.");
        }
    }

    private String label(SkillType type) {
        String name = type.getKind() == NodeKind.SUBTREE
                ? type.getDisplayName() + "  (" + type.getSlotName() + ")"
                : type.getDisplayName();

        if (type.getKind() == NodeKind.STAT) {
            int rank = skills.getRank(type.getBranch());
            String status = rank >= SkillManager.MAX_RANK ? "max" : "+1 rank: " + skills.cost(type) + " SP";
            return name + "  —  rank " + rank + "/" + SkillManager.MAX_RANK + "\n" + status;
        }
        if (skills.owns(type)) {
            int slot = skills.slotOf(type);
            return name + "\n" + (slot >= 0 ? "Owned · slot " + (slot + 1) : "Owned · click to equip");
        }
        String reason = skills.lockReason(type);
        return name + "\n" + (reason != null ? reason : "Cost " + skills.cost(type) + " SP");
    }

    private String style(SkillType type) {
        String base = "-fx-font-size: 13px; ";
        boolean owned = skills.owns(type);
        if (type.getKind() == NodeKind.STAT) {
            if (skills.canBuy(type, stats.getSkillPoints())) return base + "-fx-background-color: #FF8C00; -fx-text-fill: white;";
            return base + (owned ? "-fx-background-color: #008000; -fx-text-fill: white;" : "-fx-background-color: #444; -fx-text-fill: #aaa;");
        }
        if (owned && skills.slotOf(type) >= 0) return base + "-fx-background-color: #1565C0; -fx-text-fill: white;";
        if (owned) return base + "-fx-background-color: #008000; -fx-text-fill: white;";
        if (skills.canBuy(type, stats.getSkillPoints())) return base + "-fx-background-color: #FF8C00; -fx-text-fill: white;";
        return base + "-fx-background-color: #444; -fx-text-fill: #aaa;";
    }
}
