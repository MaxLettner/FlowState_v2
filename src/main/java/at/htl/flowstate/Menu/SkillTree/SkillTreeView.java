package at.htl.flowstate.Menu.SkillTree;

import at.htl.flowstate.Assets;
import at.htl.flowstate.Components.Player.PlayerStatsComponent;
import at.htl.flowstate.Game;
import at.htl.flowstate.Menu.PixelText;
import at.htl.flowstate.Menu.PixelText.Look;
import at.htl.flowstate.Skills.NodeKind;
import at.htl.flowstate.Skills.SkillManager;
import at.htl.flowstate.Skills.SkillType;
import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.input.UserAction;
import com.almasb.fxgl.scene.SubScene;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.CacheHint;
import javafx.scene.Group;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Full-screen skill tree (I): the tree is carved into an ancient rune wall, the Rusty Sword at its root.
 * Owned nodes and the grooves leading to them glow with molten rock, learnable ones have gold rings.
 * Click a node to buy it (or a stat rank). Click an owned skill to put it in the first free hotbar slot,
 * or hover it and press 1-4 to put it in that slot. Hovering shows a tooltip next to the node.
 * Shown as an FXGL sub-scene, so the game world is paused while it is open.
 */
public class SkillTreeView {
    private static final KeyCode[] SLOT_KEYS = {KeyCode.DIGIT1, KeyCode.DIGIT2, KeyCode.DIGIT3, KeyCode.DIGIT4};
    private static final int BG_SCALE = 4;            // SkillTreeBg.png is 480x270
    private static final double TIP_W = 420;
    private static final double TIP_PAD = 14;
    private static final double TIP_TEXT_W = TIP_W - 2 * TIP_PAD;
    private static final int SP_GLYPH_TOP = 26;       // art row of the skill point count on the lintel (skilltree.py)
    private static final int TEXT_SCALE = 2;          // body text, like the node rings and icons
    private static final int TITLE_SCALE = 4;         // like the rune menu's buttons
    private static final double SPARK_SPEED = 240;    // px/s along a groove
    private static final double PANEL_SIDE = 56;      // the hotbar and controls panels sit in the lower corners
    private static final double PANEL_BOTTOM = 1046;
    private static final Look GOLD = PixelText.inlaid(Color.web("#f5cd52"), Color.web("#cf9c2f"));
    private static final Look BAD = PixelText.inlaid(Color.web("#ff6b5e"));
    private static final Look GOOD = PixelText.inlaid(Color.web("#9be06f"));
    private static final Look GREY = PixelText.inlaid(Color.web("#a39fab"));
    private static final String PANEL = "-fx-background-color: rgba(14,13,18,0.88); -fx-background-radius: 6;"
            + "-fx-border-color: #8a774f; -fx-border-width: 2; -fx-border-radius: 6;";

    private final SkillManager skills = SkillManager.get();
    private final PlayerStatsComponent stats;

    // only the top scene is updated, so pushing this pauses physics, entities and Game.onUpdate
    private final SubScene scene = new SubScene() {
        @Override
        protected void onUpdate(double tpf) {
            animate(tpf);
        }
    };
    private final Pane root = new Pane();
    private final GrooveRenderer grooves = new GrooveRenderer();
    private final ImageView glowView = new ImageView(grooves.glow);
    private final Group sparkLayer = new Group();
    private final Map<SkillType, SkillNode> nodes = new EnumMap<>(SkillType.class);
    private final List<Spark> sparks = new ArrayList<>();

    private final ImageView spView = new ImageView();
    private final VBox tip = new VBox(8);
    private final ImageView tipName = new ImageView();
    private final ImageView tipKind = new ImageView();
    private final ImageView tipDesc = new ImageView();
    private final ImageView tipStatus = new ImageView();
    private final ImageView tipHint = new ImageView();
    private final HBox hotbarSlots = new HBox(8);

    private SkillNode hovered = null;
    private String message = null;   // feedback for the hovered node, e.g. "Not enough skill points"
    private boolean open = false;
    private boolean dirty = true;
    private double time = 0;

    public SkillTreeView() {
        stats = Game.getPlayer().getComponent(PlayerStatsComponent.class);

        root.setPrefSize(SkillTreeLayout.WIDTH, SkillTreeLayout.HEIGHT);
        ImageView bg = new ImageView(Assets.pixels("SkillTreeBg.png", BG_SCALE));
        bg.setSmooth(false);

        ImageView lines = new ImageView(grooves.lines);
        lines.setSmooth(false);
        glowView.setSmooth(false);
        glowView.setEffect(new GaussianBlur(22));
        glowView.setBlendMode(BlendMode.ADD);
        glowView.setMouseTransparent(true);
        glowView.setCache(true);
        glowView.setCacheHint(CacheHint.SPEED);
        sparkLayer.setBlendMode(BlendMode.ADD);
        sparkLayer.setMouseTransparent(true);

        Group nodeLayer = new Group();
        for (SkillType t : SkillType.values()) {
            SkillNode node = new SkillNode(t);
            node.hitArea().setOnMouseEntered(e -> hover(node));
            node.hitArea().setOnMouseExited(e -> unhover(node));
            node.hitArea().setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY) onClick(node);
            });
            nodes.put(t, node);
            nodeLayer.getChildren().add(node);
        }

        root.getChildren().addAll(bg, lines, glowView, sparkLayer, nodeLayer, header(), hotbar(), controls(), tooltip());
        scene.getContentRoot().getChildren().add(root);
        initInput();

        skills.addListener(this::refresh);
        stats.addSkillPointsListener(this::refresh);
    }

    /** The game scene's input is inactive while this is open, so the overlay needs its own bindings. */
    private void initInput() {
        for (KeyCode key : new KeyCode[]{KeyCode.I, KeyCode.ESCAPE}) {
            scene.getInput().addAction(new UserAction("Close Skill Tree " + key) {
                @Override protected void onActionBegin() { close(); }
            }, key);
        }
        for (int i = 0; i < SLOT_KEYS.length; i++) {
            final int slot = i;
            scene.getInput().addAction(new UserAction("Assign Slot " + (i + 1)) {
                @Override protected void onActionBegin() { assignHovered(slot); }
            }, SLOT_KEYS[i]);
        }
    }

    //-----Open / close-----
    public void open() {
        if (open) return;
        open = true;
        refresh();
        FXGL.getSceneService().pushSubScene(scene);
    }

    public void close() {
        if (!open) return;
        open = false;
        if (hovered != null) unhover(hovered);
        FXGL.getSceneService().popSubScene();
    }

    public boolean isOpen() {
        return open;
    }

    //-----Static parts-----
    private ImageView header() {
        // molten, under the carved title on the stone lintel; the image has one art pixel of rim above the letters
        spView.setSmooth(false);
        spView.setLayoutY((SP_GLYPH_TOP - 1) * BG_SCALE);
        spView.setMouseTransparent(true);
        return spView;
    }

    private VBox hotbar() {
        VBox box = new VBox(8, PixelText.view("HOTBAR", PixelText.MOLTEN, TEXT_SCALE), hotbarSlots);
        box.setStyle(PANEL + "-fx-padding: 10 12 12 12;");
        anchor(box, false);
        box.setMouseTransparent(true);
        return box;
    }

    /** The keys as stone keycaps and the mouse, like the controls tablet of the rune menu. */
    private VBox controls() {
        VBox box = new VBox(6,
                controlLine("LEARN / EQUIP", PixelText.mouse(false, TEXT_SCALE)),
                controlLine("PUT HOVERED SKILL IN A SLOT", key("1"), symbol("-"), key("4")),
                controlLine("CLOSE", key("I"), symbol("/"), key("ESC")));
        box.setStyle(PANEL + "-fx-padding: 10 14 10 14;");
        anchor(box, true);
        box.setMouseTransparent(true);
        return box;
    }

    /** Keeps a panel in a lower corner, on whole pixels, whatever size its text gives it. */
    private static void anchor(Region box, boolean right) {
        box.heightProperty().addListener((o, a, h) -> box.setLayoutY(Math.round(PANEL_BOTTOM - h.doubleValue())));
        if (right) {
            box.widthProperty().addListener((o, a, w) ->
                    box.setLayoutX(Math.round(SkillTreeLayout.WIDTH - PANEL_SIDE - w.doubleValue())));
        } else {
            box.setLayoutX(PANEL_SIDE);
        }
    }

    private static Image key(String name) {
        return PixelText.keycap(name, TEXT_SCALE);
    }

    private static Image symbol(String s) {
        return PixelText.render(s, PixelText.PARCHMENT, TEXT_SCALE);
    }

    private HBox controlLine(String what, Image... keys) {
        HBox keyBox = new HBox(4);
        for (Image k : keys) keyBox.getChildren().add(PixelText.view(k));
        keyBox.setAlignment(Pos.CENTER_RIGHT);
        keyBox.setPrefWidth(92);
        HBox line = new HBox(12, keyBox, PixelText.view(what, PixelText.PARCHMENT, TEXT_SCALE));
        line.setAlignment(Pos.CENTER_LEFT);
        return line;
    }

    private VBox tooltip() {
        for (ImageView v : new ImageView[]{tipName, tipKind, tipDesc, tipStatus, tipHint}) v.setSmooth(false);
        tip.getChildren().addAll(tipName, tipKind, tipDesc, tipStatus, tipHint);
        tip.setStyle(PANEL + "-fx-background-color: rgba(14,13,18,0.95); -fx-padding: 12 14 14 14;");
        tip.setPrefWidth(TIP_W);
        tip.setMouseTransparent(true);
        tip.setVisible(false);
        return tip;
    }

    //-----Hover / click-----
    private void hover(SkillNode node) {
        if (hovered != null) hovered.setHovered(false);
        hovered = node;
        message = null;
        node.setHovered(true);
        showTip();
    }

    private void unhover(SkillNode node) {
        if (hovered != node) return;
        node.setHovered(false);
        hovered = null;
        message = null;
        tip.setVisible(false);
    }

    private void onClick(SkillNode node) {
        SkillType type = node.type;
        if (type.getKind() == NodeKind.STAT || !skills.owns(type)) {
            if (skills.lockReason(type) != null) {
                message = null;
            } else if (skills.buy(type)) {
                node.flash();
                message = null;
            } else {
                message = "Not enough skill points";
            }
            showTip();
            return;
        }
        if (!type.isUsable() || skills.slotOf(type) >= 0) return;
        int empty = skills.firstEmptySlot();
        if (empty >= 0) {
            skills.assign(type, empty);
        } else {
            message = "Hotbar is full: press 1-4 to replace a slot";
            showTip();
        }
    }

    private void assignHovered(int slot) {
        if (hovered == null) return;
        if (!skills.owns(hovered.type) || !hovered.type.isUsable()) {
            message = skills.owns(hovered.type) ? "This can't go on the hotbar" : "Learn it first";
            showTip();
            return;
        }
        skills.assign(hovered.type, slot);
    }

    //-----Refresh-----
    private void refresh() {
        if (!open) {
            dirty = true;
            return;
        }
        dirty = false;
        int sp = (int) stats.getSkillPoints();
        Image spText = PixelText.render(sp + (sp == 1 ? " SKILL POINT" : " SKILL POINTS"), PixelText.MOLTEN, BG_SCALE);
        spView.setImage(spText);
        // centred like the carved title (skilltree.lintel), on the wall's pixel grid
        int textW = (int) (spText.getWidth() / BG_SCALE) - 2;
        spView.setLayoutX(((int) SkillTreeLayout.WIDTH / BG_SCALE / 2 - textW / 2 - 1) * BG_SCALE);

        Map<SkillType, GrooveRenderer.Fill> fills = new EnumMap<>(SkillType.class);
        for (SkillNode node : nodes.values()) {
            SkillType t = node.type;
            boolean owned = skills.owns(t);
            boolean learnable = skills.lockReason(t) == null;
            SkillNode.State state = owned ? SkillNode.State.OWNED
                    : learnable ? SkillNode.State.AVAILABLE : SkillNode.State.LOCKED;
            String plate = t.getKind() == NodeKind.STAT
                    ? t.getDisplayName() + "  " + skills.getRank(t.getBranch()) + "/" + SkillManager.MAX_RANK : null;
            node.setState(state, skills.canBuy(t, stats.getSkillPoints()), skills.slotOf(t), plate);
            fills.put(t, owned ? GrooveRenderer.Fill.MOLTEN
                    : learnable ? GrooveRenderer.Fill.DORMANT : GrooveRenderer.Fill.NONE);
        }
        grooves.render(fills);
        rebuildSparks(fills);
        refreshHotbar();
        if (hovered != null) showTip();
    }

    private void refreshHotbar() {
        hotbarSlots.getChildren().clear();
        for (int i = 0; i < SkillManager.HOTBAR_SIZE; i++) {
            SkillType t = skills.getSlot(i);
            Pane slot = new Pane();
            slot.setPrefSize(60, 60);
            slot.setStyle("-fx-background-color: #1b1a20; -fx-border-color: #57535f; -fx-border-width: 2;");
            if (t != null) {
                ImageView icon = Assets.sprite(t.getIconName(), 2);
                icon.setLayoutX(6);
                icon.setLayoutY(6);
                slot.getChildren().add(icon);
            }
            // the key under the slot, so it never hides part of the icon
            VBox column = new VBox(4, slot, PixelText.view(key(String.valueOf(i + 1))));
            column.setAlignment(Pos.CENTER);
            hotbarSlots.getChildren().add(column);
        }
    }

    private void showTip() {
        if (hovered == null) return;
        SkillType t = hovered.type;
        tipName.setImage(PixelText.render(t.getDisplayName(), PixelText.inlaid(SkillNode.branchColor(t.getBranch())),
                TITLE_SCALE, TIP_TEXT_W, false));
        tipKind.setImage(text(kindLine(t), GREY));
        tipDesc.setImage(text(t.getDescription(), PixelText.PARCHMENT));

        Status s = status(t);
        tipStatus.setImage(text(message != null ? message : s.text, message != null ? BAD : s.look));
        tipHint.setImage(s.hint == null ? null : text(s.hint, GREY));
        tipHint.setManaged(s.hint != null);
        tipHint.setVisible(s.hint != null);

        tip.applyCss();
        tip.layout();
        double h = tip.prefHeight(TIP_W);
        Point2D c = hovered.center;
        double gap = hovered.radius + 14;
        double x = c.getX() + gap;
        if (x + TIP_W > SkillTreeLayout.WIDTH - 40) x = c.getX() - gap - TIP_W;
        double y = Math.max(40, Math.min(SkillTreeLayout.HEIGHT - 40 - h, c.getY() - h / 2));
        tip.setLayoutX(x);
        tip.setLayoutY(y);
        tip.setVisible(true);
    }

    private static Image text(String s, Look look) {
        return PixelText.render(s, look, TEXT_SCALE, TIP_TEXT_W, false);
    }

    private record Status(String text, Look look, String hint) {}

    private Status status(SkillType t) {
        int slot = skills.slotOf(t);
        String slotHint = t.isUsable() ? "Hover and press 1-4 to put it in that hotbar slot." : null;
        if (t.getKind() == NodeKind.START) {
            return new Status(slot >= 0 ? "Equipped in slot " + (slot + 1) : "Owned - click to equip", GOOD, slotHint);
        }
        if (t.getKind() == NodeKind.STAT) {
            int rank = skills.getRank(t.getBranch());
            if (rank >= SkillManager.MAX_RANK) return new Status("Rank " + rank + "/" + SkillManager.MAX_RANK + " - max", GOOD, null);
            boolean afford = skills.canBuy(t, stats.getSkillPoints());
            return new Status("Rank " + rank + "/" + SkillManager.MAX_RANK + " - next rank costs " + skills.cost(t) + " SP",
                    afford ? GOLD : BAD, afford ? "Click to learn the next rank." : "Not enough skill points.");
        }
        if (skills.owns(t)) {
            if (!t.isUsable()) return new Status("Owned", GOOD, null);
            return new Status(slot >= 0 ? "Equipped in slot " + (slot + 1) : "Owned - click to equip", GOOD, slotHint);
        }
        String reason = skills.lockReason(t);
        if (reason != null) return new Status("Locked - " + reason, BAD, "Costs " + skills.cost(t) + " SP once unlocked.");
        boolean afford = skills.canBuy(t, stats.getSkillPoints());
        return new Status("Costs " + skills.cost(t) + " SP", afford ? GOLD : BAD,
                afford ? "Click to learn." : "Not enough skill points.");
    }

    private static String kindLine(SkillType t) {
        String branch = t.getBranch().getDisplayName();
        return switch (t.getKind()) {
            case START -> "Starting weapon";
            case STAT -> "Stat - " + branch;
            case SUBTREE -> "Sub-tree - " + branch + (t.getSlotName() != null ? " - grants " + t.getSlotName() : "");
            case SKILL -> "Skill - " + branch + " / " + t.getParent().getDisplayName();
        };
    }

    //-----Animation-----
    /** A bright speck of molten rock flowing along an owned groove, from the parent out to the child. */
    private record Spark(List<Point2D> path, double length, double phase, Rectangle core, Rectangle halo) {}

    private void rebuildSparks(Map<SkillType, GrooveRenderer.Fill> fills) {
        sparks.clear();
        sparkLayer.getChildren().clear();
        int i = 0;
        for (Map.Entry<SkillType, GrooveRenderer.Fill> e : fills.entrySet()) {
            if (e.getValue() != GrooveRenderer.Fill.MOLTEN || SkillTreeLayout.parent(e.getKey()) == null) continue;
            List<Point2D> path = grooves.screenPath(e.getKey());
            double length = 0;
            for (int k = 1; k < path.size(); k++) length += path.get(k).distance(path.get(k - 1));
            Rectangle core = new Rectangle(4, 4, Color.web("#fff3a0"));
            Rectangle halo = new Rectangle(10, 10, Color.rgb(255, 150, 40, 0.45));
            sparkLayer.getChildren().addAll(halo, core);
            sparks.add(new Spark(path, length, (i++ * 0.37) % 1.0, core, halo));
        }
    }

    private void animate(double tpf) {
        time += tpf;
        if (dirty) refresh();
        glowView.setOpacity(0.7 + 0.3 * Math.sin(time * 2.1));
        for (SkillNode node : nodes.values()) node.animate(time, tpf);
        for (Spark s : sparks) {
            double t = (time * SPARK_SPEED / Math.max(1, s.length) + s.phase) % 1.0;
            Point2D p = s.path.get((int) (t * (s.path.size() - 1)));
            // snap to the 2px groove grid so the speck stays a crisp pixel
            double x = Math.floor(p.getX() / 2) * 2, y = Math.floor(p.getY() / 2) * 2;
            s.core.setX(x - 2);
            s.core.setY(y - 2);
            s.halo.setX(x - 5);
            s.halo.setY(y - 5);
            double fade = Math.sin(Math.PI * t);
            s.core.setOpacity(fade);
            s.halo.setOpacity(fade);
        }
    }
}
