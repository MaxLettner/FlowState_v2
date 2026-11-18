package at.htl.flowstate.Menu.SkillTree;

import at.htl.flowstate.Assets;
import at.htl.flowstate.Menu.GoldCursor;
import at.htl.flowstate.Menu.PixelText;
import at.htl.flowstate.Skills.Branch;
import at.htl.flowstate.Skills.NodeKind;
import at.htl.flowstate.Skills.SkillType;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

/**
 * One dot of the skill tree: a ring (carved stone = locked, gold = learnable, molten = owned) around the round
 * skill icon, an optional name plate below and a badge with the hotbar slot. Centred on its layout position.
 */
final class SkillNode extends Group {
    enum State { LOCKED, AVAILABLE, OWNED }

    private static final int SCALE = 2;
    private static final ColorAdjust LOCKED_ICON = new ColorAdjust(0, -0.85, -0.35, 0);
    private static final Color OWNED_GLOW = Color.rgb(255, 122, 31, 0.8);
    private static final Color PULSE_GLOW = Color.rgb(255, 205, 82);
    private static final Color FLASH_GLOW = Color.rgb(255, 250, 220);
    private static final PixelText.Look BADGE = PixelText.inlaid(Color.web("#fff4b0"), Color.web("#f5cd52"));

    final SkillType type;
    final Point2D center;
    final double radius;

    private final ImageView ring = new ImageView();
    private final ImageView icon;
    private final DropShadow ringGlow = new DropShadow(BlurType.GAUSSIAN, Color.TRANSPARENT, 18, 0.35, 0, 0);
    private final DropShadow hoverGlow = new DropShadow(BlurType.GAUSSIAN, Color.rgb(255, 240, 200, 0.9), 26, 0.3, 0, 0);
    private final Circle hit;
    private final Group badge = new Group();
    private final ImageView badgeDigit = new ImageView();
    private final ImageView subLabel = new ImageView();

    private State state = State.LOCKED;
    private boolean pulse;
    private double flash;

    SkillNode(SkillType type) {
        this.type = type;
        this.center = SkillTreeLayout.position(type);
        setLayoutX(center.getX());
        setLayoutY(center.getY());

        radius = switch (type.getKind()) {
            case START -> 48;
            case STAT, SUBTREE -> 40;
            case SKILL -> 32;
        };
        ring.setSmooth(false);
        ring.setEffect(ringGlow);
        icon = Assets.sprite("IconRound_" + type.name() + ".png", SCALE);
        icon.setX(-icon.getImage().getWidth() / 2);
        icon.setY(-icon.getImage().getHeight() / 2);

        hit = new Circle(radius - 4, Color.TRANSPARENT);
        hit.setCursor(GoldCursor.hover());

        Circle badgeBg = new Circle(12, Color.web("#121116"));
        badgeBg.setStroke(Color.web("#f5cd52"));
        badgeBg.setStrokeWidth(2);
        badgeDigit.setSmooth(false);
        badge.getChildren().addAll(badgeBg, badgeDigit);
        badge.setLayoutX(Math.round(radius * 0.72));
        badge.setLayoutY(-Math.round(radius * 0.72));
        badge.setVisible(false);

        getChildren().addAll(ring, icon);
        if (type.getKind() == NodeKind.STAT || type.getKind() == NodeKind.SUBTREE) getChildren().add(plate());
        getChildren().addAll(badge, hit);
        setState(State.LOCKED, false, -1, null);
    }

    /** The node's mouse target (the images themselves are not picked). */
    Circle hitArea() {
        return hit;
    }

    private VBox plate() {
        VBox box = new VBox(4);
        box.setAlignment(Pos.CENTER);
        box.setLayoutY(radius - 2);
        box.setMouseTransparent(true);
        box.setStyle("-fx-background-color: rgba(12,11,15,0.82); -fx-background-radius: 4; -fx-padding: 4 8 4 8;"
                + "-fx-border-color: rgba(138,119,79,0.6); -fx-border-radius: 4; -fx-border-width: 1;");
        subLabel.setSmooth(false);
        if (type.getKind() == NodeKind.STAT) {
            ImageView branch = PixelText.view(type.getBranch().getDisplayName(),
                    PixelText.inlaid(branchColor(type.getBranch())), SCALE);
            box.getChildren().addAll(branch, subLabel);
        } else {
            subLabel.setImage(PixelText.render(type.getDisplayName(), PixelText.PARCHMENT, SCALE));
            box.getChildren().add(subLabel);
        }
        // centred under the dot whatever the text width, on whole pixels
        box.widthProperty().addListener((o, a, w) -> box.setLayoutX(-Math.round(w.doubleValue() / 2)));
        return box;
    }

    /**
     * @param affordable learnable right now (pulses)
     * @param slot hotbar slot or -1
     * @param plateText second line of the stat plate (rank), ignored for other nodes
     */
    void setState(State state, boolean affordable, int slot, String plateText) {
        this.state = state;
        this.pulse = affordable;
        ring.setImage(ringImage(state));
        ring.setX(-ring.getImage().getWidth() / 2);
        ring.setY(-ring.getImage().getHeight() / 2);
        icon.setEffect(state == State.LOCKED ? LOCKED_ICON : null);
        icon.setOpacity(state == State.LOCKED ? 0.85 : 1);
        badge.setVisible(slot >= 0);
        if (slot >= 0) {
            badgeDigit.setImage(PixelText.render(String.valueOf(slot + 1), BADGE, SCALE));
            badgeDigit.setX(-Math.round(badgeDigit.getImage().getWidth() / 2));
            badgeDigit.setY(-Math.round(badgeDigit.getImage().getHeight() / 2));
        }
        if (plateText != null && type.getKind() == NodeKind.STAT) {
            subLabel.setImage(PixelText.render(plateText, PixelText.PARCHMENT, SCALE));
        }
        animate(0, 0);
    }

    /** A short bright flare, e.g. right after the node was bought. */
    void flash() {
        flash = 1;
    }

    void setHovered(boolean hovered) {
        setEffect(hovered ? hoverGlow : null);
    }

    void animate(double time, double tpf) {
        flash = Math.max(0, flash - tpf * 1.6);
        Color base;
        double r;
        if (pulse) {
            double k = 0.5 + 0.5 * Math.sin(time * 4);
            base = PULSE_GLOW.deriveColor(0, 1, 1, 0.45 + 0.5 * k);
            r = 12 + 14 * k;
        } else if (state == State.OWNED) {
            base = OWNED_GLOW;
            r = 18;
        } else {
            base = Color.TRANSPARENT;
            r = 10;
        }
        if (flash > 0) {
            base = base.interpolate(FLASH_GLOW, flash);
            r += 30 * flash;
        }
        ringGlow.setColor(base);
        ringGlow.setRadius(r);
    }

    private Image ringImage(State state) {
        return switch (type.getKind()) {
            case START -> Assets.pixels("NodeRing_root.png", SCALE);
            case STAT, SUBTREE -> Assets.frameImage("NodeRing_major.png", 3, SCALE, state.ordinal());
            case SKILL -> Assets.frameImage("NodeRing_skill.png", 3, SCALE, state.ordinal());
        };
    }

    static Color branchColor(Branch b) {
        return switch (b) {
            case MELEE -> Color.web("#ff7b7b");
            case RANGED -> Color.web("#9be06f");
            case MAGIC -> Color.web("#c29bff");
        };
    }
}
