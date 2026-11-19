package at.htl.flowstate;

import at.htl.flowstate.Combat.Combat;
import at.htl.flowstate.Components.Player.LoadoutComponent;
import at.htl.flowstate.Components.Player.PlayerMovementComponent;
import at.htl.flowstate.Components.Player.PlayerStatsComponent;
import at.htl.flowstate.Components.SpriteComponents.SpriteComponent;
import at.htl.flowstate.Visuals.Impact;
import at.htl.flowstate.Visuals.Particles;
import javafx.geometry.Point2D;
import javafx.util.Duration;
import at.htl.flowstate.Factories.EnemyFactory;
import at.htl.flowstate.Factories.LevelFactory;
import at.htl.flowstate.Generation.LevelGeneration;
import at.htl.flowstate.Menu.GameMenu;
import at.htl.flowstate.Menu.GoldCursor;
import at.htl.flowstate.Menu.Hud;
import at.htl.flowstate.Menu.SkillTree.SkillTreeView;
import at.htl.flowstate.Skills.SkillManager;
import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.app.scene.MenuType;
import com.almasb.fxgl.app.scene.SceneFactory;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.input.UserAction;
import com.almasb.fxgl.physics.BoundingShape;
import com.almasb.fxgl.physics.HitBox;
import com.almasb.fxgl.physics.PhysicsComponent;
import com.almasb.fxgl.physics.box2d.dynamics.BodyType;
import com.almasb.fxgl.physics.box2d.dynamics.FixtureDef;
import javafx.scene.Group;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.almasb.fxgl.dsl.FXGL.*;

public class Game extends GameApplication {

    private static Entity player;
    private LevelGeneration levelGeneration;

    private final double WINDOW_WIDTH = 1920;
    private final double WINDOW_HEIGHT = 1080;

    private SkillTreeView skillTree;
    private Hud hud;

    private static final short CATEGORY_TERRAIN = 0x0001;
    private static final short CATEGORY_PLAYER = 0x0002;

    // parallax layers (tools/art/terrain.py): 480x216 art shown at x5 = 2400x1080, seamless horizontally
    private static final int BG_SCALE = 5;
    // foreground layers move faster than the world (factor > 1): the bigger, the closer they look
    private static final double FG_MID_FACTOR = 1.3;
    private static final double FG_NEAR_FACTOR = 1.7;
    private static final double DEATH_EXIT_DELAY = 0.8; // puddle stays on screen before returning to the main menu
    private static final double EMBER_INTERVAL = 0.35; // faint embers rising out of the abyss
    private final List<ParallaxLayer> layers = new ArrayList<>();
    // foreground cave silhouettes (tools/art/foreground.py), in front of the world, scrolling faster than the camera
    private final List<ParallaxLayer> fgLayers = new ArrayList<>();
    private final Group foreground = new Group();
    private double emberTimer = 0;
    private double cleanupTimer = 0;
    private static final double CLEANUP_INTERVAL = 0.5;
    private PlayerStatsComponent playerStats;
    private boolean dying = false;
    private double cameraX = 0;
    // the viewport may move this far vertically for camera shake
    private static final int SHAKE_MARGIN = 40;

    /** Two copies of one image side by side, scrolled at a fraction of the camera speed. */
    private record ParallaxLayer(ImageView a, ImageView b, double factor) {
        void scroll(double cameraX) {
            double width = a.getImage().getWidth();
            double offset = -(cameraX * factor) % width;
            if (offset > 0) offset -= width;
            a.setLayoutX(Math.round(offset));
            b.setLayoutX(Math.round(offset + width));
        }

        /** Vertical camera movement (shake), also scaled by the factor. */
        void shiftY(double cameraY) {
            double y = Math.round(-cameraY * factor);
            a.setLayoutY(y);
            b.setLayoutY(y);
        }

        void setImage(Image img) {
            a.setImage(img);
            b.setImage(img);
        }
    }

    private static final KeyCode[] SLOT_KEYS = {KeyCode.DIGIT1, KeyCode.DIGIT2, KeyCode.DIGIT3, KeyCode.DIGIT4};

    @Override
    protected void initSettings(GameSettings settings) {
        settings.setTitle("FlowState");
        settings.setVersion("0.7.0");
        settings.setWidth((int) WINDOW_WIDTH);
        settings.setHeight((int) WINDOW_HEIGHT);
        // the 1920x1080 game is scaled (letterboxed) to the screen; F11 toggles windowed mode
        settings.setFullScreenAllowed(true);
        settings.setFullScreenFromStart(true);
        // the game starts in the rune menu; the world is only built once "Start New Game" is pressed
        settings.setMainMenuEnabled(!DevShots.AUTOSTART);
        settings.setSceneFactory(new SceneFactory() {
            @NotNull
            @Override
            public com.almasb.fxgl.app.scene.FXGLMenu newMainMenu() {
                return new GameMenu(MenuType.MAIN_MENU);
            }

            @NotNull
            @Override
            public com.almasb.fxgl.app.scene.FXGLMenu newGameMenu() {
                return new GameMenu(MenuType.GAME_MENU);
            }
        });
    }

    @Override
    protected void onPreInit() {
        // the golden arrow replaces FXGL's default cursor on every scene
        GoldCursor.install(getPrimaryStage());
        Sfx.preload();
        FrameRateLog.install();
    }

    @Override
    protected void initPhysics() {
        getPhysicsWorld().setGravity(0, 800);
    }

    @Override
    protected void initInput() {
        getInput().addAction(new UserAction("Left") {
            @Override protected void onAction() { movement().startMoveLeft(); }
            @Override protected void onActionEnd() { movement().stopHorizontalMovement(); }
        }, KeyCode.A);

        getInput().addAction(new UserAction("Right") {
            @Override protected void onAction() { movement().startMoveRight(); }
            @Override protected void onActionEnd() { movement().stopHorizontalMovement(); }
        }, KeyCode.D);

        getInput().addAction(new UserAction("Jump") {
            @Override protected void onActionBegin() { movement().startJump(); }
            @Override protected void onActionEnd() { movement().stopJump(); }
        }, KeyCode.W);

        getInput().addAction(new UserAction("Down") {
            @Override protected void onActionBegin() { movement().startDown(); }
            @Override protected void onActionEnd() { movement().stopDown(); }
        }, KeyCode.S);

        getInput().addAction(new UserAction("Toggle Mute") {
            @Override protected void onActionBegin() { Sfx.toggleMute(); }
        }, KeyCode.M);

        getInput().addAction(new UserAction("Toggle Fullscreen") {
            @Override protected void onActionBegin() {
                getSettings().getFullScreen().set(!getSettings().getFullScreen().get());
            }
        }, KeyCode.F11);

        // closing (I / Esc) is bound inside the skill tree, which pauses the game while open
        getInput().addAction(new UserAction("Open Skill Tree") {
            @Override protected void onActionBegin() { skillTree.open(); }
        }, KeyCode.I);

        getInput().addAction(new UserAction("Attack") {
            @Override protected void onActionBegin() { loadout().primaryPress(); }
            @Override protected void onActionEnd() { loadout().primaryRelease(); }
        }, MouseButton.PRIMARY);

        getInput().addAction(new UserAction("Secondary") {
            @Override protected void onActionBegin() { loadout().secondaryPress(); }
            @Override protected void onActionEnd() { loadout().secondaryRelease(); }
        }, MouseButton.SECONDARY);

        for (int i = 0; i < SLOT_KEYS.length; i++) {
            final int slot = i;
            getInput().addAction(new UserAction("Hotbar " + (i + 1)) {
                @Override protected void onActionBegin() { loadout().pressSlot(slot); }
            }, SLOT_KEYS[i]);
        }
    }

    private static PlayerMovementComponent movement() {
        return player.getComponent(PlayerMovementComponent.class);
    }

    private static LoadoutComponent loadout() {
        return player.getComponent(LoadoutComponent.class);
    }

    @Override
    protected void initGame() {
        setupFactories();
        levelGeneration = new LevelGeneration();
        Combat.init();
        Impact.reset();
        cameraX = 0;
        // a new game keeps the viewport: left where the last run ended, keepOnScreen would drag the new player there
        getGameScene().getViewport().setX(0);
        getGameScene().getViewport().setY(0);
        dying = false;
        getInput().setProcessInput(true);   // a death turned it off
        SkillManager.reset();   // initGame runs again for "Start New Game"; before the player caches it
        setupPlayer();
        playerStats = player.getComponent(PlayerStatsComponent.class);
        getGameScene().getViewport().setBounds(0, -SHAKE_MARGIN, Integer.MAX_VALUE, (int) WINDOW_HEIGHT + SHAKE_MARGIN);
        skillTree = new SkillTreeView();
        hud = new Hud();
        setupBg();
        Sfx.stopStarted();
        Sfx.ambient(Sfx.AMB_ABYSS);
    }

    //------SETUP-----
    private void setupFactories() {
        getGameWorld().addEntityFactory(new LevelFactory());
        getGameWorld().addEntityFactory(new EnemyFactory());
    }

    private void setupPlayer() {
        FixtureDef playerFd = new FixtureDef().friction(0).density(1.0f);
        playerFd.getFilter().categoryBits = CATEGORY_PLAYER;
        playerFd.getFilter().maskBits = CATEGORY_TERRAIN;

        PhysicsComponent physics = new PhysicsComponent();
        physics.setBodyType(BodyType.DYNAMIC);
        physics.setFixtureDef(playerFd);
        physics.setOnPhysicsInitialized(() -> physics.getBody().setFixedRotation(true));

        player = entityBuilder()
                .at(300, levelGeneration.getBaseY() - 150)
                .bbox(new HitBox(BoundingShape.box(40, 80)))
                .with(physics)
                .with(new PlayerMovementComponent())
                .with(new PlayerStatsComponent())
                .with(new LoadoutComponent())
                .with(new SpriteComponent("Player", true, true, true))
                .collidable()
                .buildAndAttach();
    }

    private void setupBg() {
        // a new game keeps the scene root (only the game world and UI are cleared)
        for (ParallaxLayer l : layers) getGameScene().getRoot().getChildren().removeAll(l.a(), l.b());
        layers.clear();
        layers.add(addLayer(Assets.pixels("bg_far.png", BG_SCALE), 0.03, 0));
        layers.add(addLayer(Assets.pixels("bg_mid.png", BG_SCALE), 0.08, 0));
        setupFg();
    }

    private void setupFg() {
        // between the game world (contentRoot child 0) and the UI root, so the HUD stays on top
        var content = getGameScene().getContentRoot().getChildren();
        if (!content.contains(foreground)) content.add(1, foreground);
        foreground.setMouseTransparent(true);
        foreground.getChildren().clear();
        fgLayers.clear();
        fgLayers.add(addFgLayer(Assets.pixels("fg_mid.png", 4), FG_MID_FACTOR));
        fgLayers.add(addFgLayer(Assets.pixels("fg_near.png", 6), FG_NEAR_FACTOR));
    }

    private ParallaxLayer addFgLayer(Image img, double factor) {
        ImageView a = new ImageView(img), b = new ImageView(img);
        a.setSmooth(false);
        b.setSmooth(false);
        foreground.getChildren().addAll(a, b);
        return new ParallaxLayer(a, b, factor);
    }

    private ParallaxLayer addLayer(Image img, double factor, double y) {
        ImageView a = new ImageView(img), b = new ImageView(img);
        for (ImageView v : new ImageView[]{a, b}) {
            v.setSmooth(false);
            v.setLayoutY(y);
            // behind the game world, in front of the previously added layers
            getGameScene().getRoot().getChildren().add(layers.size() * 2 + (v == a ? 0 : 1), v);
        }
        return new ParallaxLayer(a, b, factor);
    }

    @Override
    protected void onUpdate(double tpf) {
        FrameRateLog.gameUpdate(tpf);
        if (!dying && player.getY() > WINDOW_HEIGHT + 100) {
            // fell into the abyss: nothing left to animate
            dying = true;
            Sfx.FALL.play();
            getGameController().gotoMainMenu();
            return;
        }
        Impact.update(tpf);
        updateCamera();
        levelGeneration.generateLevel(player.getX());
        cleanupPlatforms(tpf);
        scrollBg(tpf);
        if (playerStats.isDead() && !dying) {
            // melt into a puddle first, then back to the main menu
            dying = true;
            getInput().setProcessInput(false);
            movement().die();
            Sfx.PLAYER_DEATH.play();
            player.getComponent(SpriteComponent.class).playDeath(
                    () -> runOnce(() -> getGameController().gotoMainMenu(), Duration.seconds(DEATH_EXIT_DELAY)));
        }
        hud.update();
        DevShots.update(tpf, getPrimaryStage().getScene());
    }


    //-----BACKGROUND-----
    private void scrollBg(double tpf) {
        double cameraX = getGameScene().getViewport().getX();
        for (ParallaxLayer layer : layers) layer.scroll(cameraX);
        double cameraY = getGameScene().getViewport().getY();
        for (ParallaxLayer layer : fgLayers) {
            layer.scroll(cameraX);
            layer.shiftY(cameraY);
        }

        // sparse embers drifting up from the abyss
        emberTimer -= tpf;
        if (emberTimer <= 0) {
            emberTimer = EMBER_INTERVAL;
            Point2D at = new Point2D(cameraX + Math.random() * WINDOW_WIDTH, WINDOW_HEIGHT + 10);
            Point2D vel = new Point2D(-15 + Math.random() * 30, -35 - Math.random() * 45);
            Particles.spawn(at, vel, Particles.EMBER[1 + (int) (Math.random() * (Particles.EMBER.length - 1))],
                    Math.random() < 0.15 ? 5 : 3, 8 + Math.random() * 5, -2, -50);
        }
    }

    //-----CAMERA-----
    private void updateCamera() {
        double threshold = cameraX + WINDOW_WIDTH * 0.75;
        if (player.getX() > threshold) cameraX = player.getX() - WINDOW_WIDTH * 0.75;
        // whole pixels: a fractional camera offset makes JavaFX resample (blur) every sprite
        getGameScene().getViewport().setX(Math.round(cameraX + Impact.getShakeX()));
        getGameScene().getViewport().setY(Math.round(Impact.getShakeY()));
    }

    //-----HELPERS-----
    private void cleanupPlatforms(double tpf) {
        // scanning every entity is not needed each frame: things this far behind can wait half a second
        cleanupTimer -= tpf;
        if (cleanupTimer > 0) return;
        cleanupTimer = CLEANUP_INTERVAL;
        double viewX = getGameScene().getViewport().getX();
        List<Entity> toRemove = getGameWorld().getEntitiesFiltered(e -> e != player && e.getX() < viewX - 3500);
        toRemove.forEach(Entity::removeFromWorld);
    }

    public static void main(String[] args) {
        Thread.currentThread().setContextClassLoader(Game.class.getClassLoader());
        // JavaFX drives its pulse off vsync, but on some drivers (e.g. Mesa under XWayland) the buffer swap
        // doesn't wait for vsync and the game loop runs uncapped (~230 fps on a 60 Hz screen). FXGL only
        // re-measures fps every 2 s, so the game visibly speeds up and slows down. Without vsync JavaFX falls
        // back to its steady 60 Hz pulse timer. Can still be overridden with -Dprism.vsync=true.
        if (System.getProperty("prism.vsync") == null) System.setProperty("prism.vsync", "false");
        launch(args);
    }

    public static Entity getPlayer() {
        return player;
    }
}
