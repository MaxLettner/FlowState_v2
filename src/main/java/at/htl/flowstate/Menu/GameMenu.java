package at.htl.flowstate.Menu;

import at.htl.flowstate.Assets;
import com.almasb.fxgl.app.scene.FXGLMenu;
import com.almasb.fxgl.app.scene.MenuType;
import com.almasb.fxgl.dsl.FXGL;
import javafx.scene.CacheHint;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The Esc menu: a full-screen rune wall (tools/art/menu.py) with the buttons inside a carved, glowing seal.
 * It is FXGL's game menu sub-scene, so the game is paused while it is open; Esc resumes.
 * Start New Game and Exit Game ask first, Controls shows the key tablet, Save / Load are not implemented yet
 * and only answer "coming soon". Esc or Back leaves a dialog; arrows / W S + Enter work as well as the mouse.
 */
public class GameMenu extends FXGLMenu {
    private enum View { MAIN, CONTROLS, CONFIRM_NEW, CONFIRM_EXIT }

    private static final int SCALE = 4;
    // screen positions = layout() of tools/art/menu.py (art pixels) x4; keep in sync
    private static final double CENTER_X = 960, CENTER_Y = 584;
    private static final double RING_RADIUS = 91 * SCALE;
    private static final double MAIN_X = 752, MAIN_Y = 404, MAIN_STEP = 76;
    private static final double TABLET_X = 520, TABLET_Y = 160;
    private static final double BACK_X = 856, BACK_Y = 916;
    private static final double PROMPT_Y = 464;
    private static final double CONFIRM_Y = 592, CONFIRM_LEFT_X = 736, CONFIRM_RIGHT_X = 976;
    private static final double[][] FLAMES = {{224, 440}, {1648, 440}};   // top-left of the flame frames
    private static final int FLAME_FRAMES = 6;
    private static final double FLAME_W = 12 * SCALE, FLAME_H = 20 * SCALE;
    private static final double FLAME_FPS = 11;
    private static final double SOON_TIME = 1.6;      // how long Save / Load show "coming soon"
    private static final double EMBER_INTERVAL = 0.16;
    private static final Color[] EMBER_COLORS = {Color.web("#fff1b0"), Color.web("#ffc23d"), Color.web("#ff7a1f")};
    private static final Color PARCHMENT = Color.web("#e8d9b0");

    private final Pane root = new Pane();
    private final ImageView glow = new ImageView(Assets.pixels("MenuGlow.png", SCALE));
    private final Circle[] glints = new Circle[2];
    private final List<ImageView> flames = new ArrayList<>();
    private final List<Circle> torchLights = new ArrayList<>();
    private final Group emberLayer = new Group();
    private final List<Ember> embers = new ArrayList<>();

    private final Group mainGroup = new Group();
    private final Group controlsGroup = new Group();
    private final Group confirmGroup = new Group();
    private final List<MenuButton> mainButtons = new ArrayList<>();
    private final ImageView prompt = new ImageView();
    private final MenuButton startButton, quitButton, cancelButton, backButton;
    private final Text leftHint = hint(), rightHint = hint();

    private View view = View.MAIN;
    private MenuButton focused = null;
    private boolean keyboard = false;   // the last focus change came from the keyboard
    private int lastMain = 0;           // main button that opened the current dialog
    private double time = 0;
    private double emberTimer = 0;
    private final Random rng = new Random();

    public GameMenu() {
        super(MenuType.GAME_MENU);
        root.setPrefSize(1920, 1080);

        ImageView bg = Assets.sprite("MenuBg.png", SCALE);
        glow.setSmooth(false);
        glow.setEffect(new GaussianBlur(22));
        glow.setBlendMode(BlendMode.ADD);
        glow.setMouseTransparent(true);
        glow.setCache(true);
        glow.setCacheHint(CacheHint.SPEED);

        Group lights = new Group();
        lights.setBlendMode(BlendMode.ADD);
        lights.setMouseTransparent(true);
        for (int i = 0; i < glints.length; i++) {
            glints[i] = new Circle(46, new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.rgb(255, 236, 170, 0.85)), new Stop(0.35, Color.rgb(255, 140, 40, 0.35)),
                    new Stop(1, Color.TRANSPARENT)));
            lights.getChildren().add(glints[i]);
        }
        for (double[] f : FLAMES) {
            Circle light = new Circle(f[0] + FLAME_W / 2, f[1] + FLAME_H * 0.6, 250,
                    new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                            new Stop(0, Color.rgb(255, 150, 60, 0.26)), new Stop(1, Color.TRANSPARENT)));
            torchLights.add(light);
            lights.getChildren().add(light);
            ImageView flame = new ImageView();
            flame.setSmooth(false);
            flame.setLayoutX(f[0]);
            flame.setLayoutY(f[1]);
            flame.setMouseTransparent(true);
            flames.add(flame);
        }
        emberLayer.setBlendMode(BlendMode.ADD);
        emberLayer.setMouseTransparent(true);

        String[] ids = {"NEW_GAME", "SAVE", "LOAD", "CONTROLS", "EXIT"};
        Runnable[] actions = {() -> show(View.CONFIRM_NEW), () -> soon(0), () -> soon(1),
                () -> show(View.CONTROLS), () -> show(View.CONFIRM_EXIT)};
        for (int i = 0; i < ids.length; i++) {
            MenuButton b = new MenuButton(ids[i], MAIN_X, MAIN_Y + i * MAIN_STEP, actions[i]);
            mainButtons.add(b);
            mainGroup.getChildren().add(b);
        }
        // the actions are only stubs until saving exists: fireSave() / fireLoad() of FXGLMenu are the hooks

        ImageView tablet = Assets.sprite("MenuControls.png", SCALE);
        tablet.setLayoutX(TABLET_X);
        tablet.setLayoutY(TABLET_Y);
        backButton = new MenuButton("BACK", BACK_X, BACK_Y, () -> show(View.MAIN));
        controlsGroup.getChildren().addAll(tablet, backButton);

        prompt.setSmooth(false);
        prompt.setLayoutY(PROMPT_Y);
        startButton = new MenuButton("START", CONFIRM_LEFT_X, CONFIRM_Y, this::fireNewGame);
        quitButton = new MenuButton("QUIT", CONFIRM_LEFT_X, CONFIRM_Y, this::fireExit);
        cancelButton = new MenuButton("CANCEL", CONFIRM_RIGHT_X, CONFIRM_Y, () -> show(View.MAIN));
        confirmGroup.getChildren().addAll(prompt, startButton, quitButton, cancelButton);

        leftHint.setText("FLOWSTATE  v" + FXGL.getSettings().getVersion());
        leftHint.setLayoutX(56);
        leftHint.setLayoutY(1024);
        rightHint.setLayoutY(1024);

        root.getChildren().addAll(bg, glow, lights);
        root.getChildren().addAll(flames);
        root.getChildren().addAll(emberLayer, mainGroup, confirmGroup, controlsGroup, leftHint, rightHint);
        getContentRoot().getChildren().add(root);

        // filters run before FXGL's own menu key handler: Esc first closes an open dialog
        getInput().addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
        show(View.MAIN);
        animate(0);
    }

    @Override
    public void onCreate() {
        // every time the menu opens
        keyboard = false;
        lastMain = 0;
        for (MenuButton b : mainButtons) b.soonLeft = 0;
        show(View.MAIN);
    }

    @Override
    protected void onUpdate(double tpf) {
        animate(tpf);
    }

    //-----Views-----
    private void show(View v) {
        view = v;
        mainGroup.setVisible(v == View.MAIN);
        controlsGroup.setVisible(v == View.CONTROLS);
        boolean confirm = v == View.CONFIRM_NEW || v == View.CONFIRM_EXIT;
        confirmGroup.setVisible(confirm);
        if (confirm) {
            prompt.setImage(Assets.pixels(v == View.CONFIRM_NEW ? "MenuText_NEW_GAME.png" : "MenuText_EXIT.png", SCALE));
            prompt.setLayoutX(CENTER_X - prompt.getImage().getWidth() / 2);
            startButton.setVisible(v == View.CONFIRM_NEW);
            quitButton.setVisible(v == View.CONFIRM_EXIT);
        }
        rightHint.setText(v == View.MAIN ? "ESC  RESUME" : "ESC  BACK");
        rightHint.setLayoutX(1864 - rightHint.getLayoutBounds().getWidth());
        for (MenuButton b : allButtons()) b.setDown(false);
        // keyboard users land on a sensible button (Cancel in the prompts); the mouse re-focuses on hover
        if (!keyboard) setFocus(null);
        else setFocus(switch (v) {
            case MAIN -> mainButtons.get(lastMain);
            case CONTROLS -> backButton;
            case CONFIRM_NEW, CONFIRM_EXIT -> cancelButton;
        });
    }

    /** The buttons of the current view in keyboard order. */
    private List<MenuButton> focusOrder() {
        return switch (view) {
            case MAIN -> mainButtons;
            case CONTROLS -> List.of(backButton);
            case CONFIRM_NEW -> List.of(startButton, cancelButton);
            case CONFIRM_EXIT -> List.of(quitButton, cancelButton);
        };
    }

    private List<MenuButton> allButtons() {
        List<MenuButton> all = new ArrayList<>(mainButtons);
        all.addAll(List.of(backButton, startButton, quitButton, cancelButton));
        return all;
    }

    private void soon(int index) {
        MenuButton b = mainButtons.get(index + 1);
        b.soonLeft = SOON_TIME;
        b.refresh();
    }

    private void onKey(KeyEvent e) {
        KeyCode k = e.getCode();
        if (k == KeyCode.ESCAPE) {
            if (view != View.MAIN) {
                show(View.MAIN);
                e.consume();
            }
            return;
        }
        keyboard = true;
        List<MenuButton> order = focusOrder();
        int i = order.indexOf(focused);
        switch (k) {
            case UP, W, LEFT, A -> setFocus(order.get(i < 0 ? order.size() - 1 : (i - 1 + order.size()) % order.size()));
            case DOWN, S, RIGHT, D, TAB -> setFocus(order.get(i < 0 ? 0 : (i + 1) % order.size()));
            case ENTER, SPACE -> {
                if (focused != null) focused.activate();
            }
            default -> { return; }
        }
        e.consume();
    }

    private void setFocus(MenuButton b) {
        MenuButton old = focused;
        focused = b;
        if (old != null) old.refresh();
        if (b != null) b.refresh();
    }

    //-----Animation-----
    private void animate(double tpf) {
        time += tpf;
        glow.setOpacity(0.62 + 0.18 * Math.sin(time * 1.7) + 0.06 * Math.sin(time * 4.3));

        for (int i = 0; i < glints.length; i++) {
            double a = time * 0.45 + i * Math.PI;
            glints[i].setCenterX(CENTER_X + RING_RADIUS * Math.cos(a));
            glints[i].setCenterY(CENTER_Y + RING_RADIUS * Math.sin(a));
        }
        for (int i = 0; i < flames.size(); i++) {
            int frame = (int) Math.floor(time * FLAME_FPS + i * 3) % FLAME_FRAMES;
            flames.get(i).setImage(Assets.frameImage("MenuFlame.png", FLAME_FRAMES, SCALE, frame));
            double flicker = 0.8 + 0.12 * Math.sin(time * 13 + i * 2) + 0.08 * Math.sin(time * 31 + i);
            torchLights.get(i).setOpacity(flicker);
        }

        emberTimer -= tpf;
        if (emberTimer <= 0) {
            emberTimer = EMBER_INTERVAL;
            double[] f = FLAMES[rng.nextInt(FLAMES.length)];
            spawnEmber(f[0] + FLAME_W / 2 + rng.nextGaussian() * 8, f[1] + 12);
        }
        for (int i = embers.size() - 1; i >= 0; i--) {
            Ember e = embers.get(i);
            if (!e.update(tpf)) {
                emberLayer.getChildren().remove(e.view);
                embers.remove(i);
            }
        }

        for (MenuButton b : mainButtons) {
            if (b.soonLeft > 0) {
                b.soonLeft = Math.max(0, b.soonLeft - tpf);
                b.refresh();
            }
        }
    }


    private void spawnEmber(double x, double y) {
        Ember e = new Ember(x, y, rng.nextGaussian() * 12, -70 - rng.nextDouble() * 60, 1.2 + rng.nextDouble() * 1.2,
                EMBER_COLORS[rng.nextInt(EMBER_COLORS.length)]);
        embers.add(e);
        emberLayer.getChildren().add(e.view);
    }

    private static Text hint() {
        Text t = new Text();
        t.setFont(Font.font("Serif", FontWeight.BOLD, 18));
        t.setFill(PARCHMENT);
        t.setOpacity(0.65);
        t.setMouseTransparent(true);
        return t;
    }

    /** One rising pixel of heat, snapped to the art pixel grid. */
    private static final class Ember {
        final Rectangle view = new Rectangle(SCALE, SCALE);
        double x, y, vx, vy, life;
        final double maxLife, phase;

        Ember(double x, double y, double vx, double vy, double life, Color c) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.life = this.maxLife = life;
            this.phase = x * 0.1;
            view.setFill(c);
            update(0);
        }

        boolean update(double tpf) {
            life -= tpf;
            x += (vx + Math.sin(life * 5 + phase) * 18) * tpf;
            y += vy * tpf;
            view.setLayoutX(Math.round(x / SCALE) * SCALE);
            view.setLayoutY(Math.round(y / SCALE) * SCALE);
            view.setOpacity(Math.max(0, Math.min(1, life / maxLife * 1.5)));
            return life > 0;
        }
    }

    //-----Buttons-----
    /** A carved stone slab with a pixel-font label; lit and molten while hovered or focused. */
    private final class MenuButton extends Group {
        private static final DropShadow HOVER_GLOW =
                new DropShadow(BlurType.GAUSSIAN, Color.rgb(255, 122, 31, 0.7), 28, 0.2, 0, 0);

        final Runnable action;
        private final Image normal, hover;
        private final ImageView img = new ImageView();
        private boolean down;
        double soonLeft = 0;

        MenuButton(String id, double x, double y, Runnable action) {
            this.action = action;
            normal = Assets.frameImage("MenuButton_" + id + ".png", 2, SCALE, 0);
            hover = Assets.frameImage("MenuButton_" + id + ".png", 2, SCALE, 1);
            img.setSmooth(false);
            getChildren().add(img);
            setLayoutX(x);
            setLayoutY(y);
            setCursor(Cursor.HAND);
            setOnMouseEntered(e -> {
                keyboard = false;
                setFocus(this);
            });
            setOnMouseExited(e -> {
                setDown(false);
                if (focused == this) setFocus(null);
            });
            setOnMousePressed(e -> {
                if (e.getButton() == MouseButton.PRIMARY) setDown(true);
            });
            setOnMouseReleased(e -> setDown(false));
            setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY) activate();
            });
            refresh();
        }

        void activate() {
            int i = mainButtons.indexOf(this);
            if (i >= 0) lastMain = i;
            action.run();
        }

        void setDown(boolean p) {
            down = p;
            refresh();
        }

        void refresh() {
            boolean lit = focused == this;
            if (soonLeft > 0) {
                img.setImage(Assets.frameImage("MenuButton_SOON.png", 2, SCALE, lit ? 1 : 0));
                // a short shake in whole art pixels right after the click
                double since = SOON_TIME - soonLeft;
                img.setTranslateX(since < 0.3 ? ((int) (since * 30) % 2 == 0 ? SCALE : -SCALE) : 0);
            } else {
                img.setImage(lit ? hover : normal);
                img.setTranslateX(0);
            }
            img.setTranslateY(down ? SCALE : 0);
            setEffect(lit ? HOVER_GLOW : null);
        }
    }
}
