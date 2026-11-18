package at.htl.flowstate;

import at.htl.flowstate.Combat.Combat;
import at.htl.flowstate.Components.Player.LoadoutComponent;
import at.htl.flowstate.Components.Player.PlayerMovementComponent;
import at.htl.flowstate.Components.Player.PlayerStatsComponent;
import at.htl.flowstate.Components.SpriteComponents.SpriteComponent;
import at.htl.flowstate.Visuals.Impact;
import at.htl.flowstate.Visuals.Particles;
import javafx.geometry.Point2D;
import at.htl.flowstate.Factories.EnemyFactory;
import at.htl.flowstate.Factories.LevelFactory;
import at.htl.flowstate.Generation.LevelGeneration;
import at.htl.flowstate.Menu.GameMenu;
import at.htl.flowstate.Menu.Hud;
import at.htl.flowstate.Menu.SkillTree.SkillTreeView;
import at.htl.flowstate.Skills.SkillManager;
import at.htl.flowstate.Skills.SkillType;
import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.app.scene.SceneFactory;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.input.UserAction;
import com.almasb.fxgl.physics.BoundingShape;
import com.almasb.fxgl.physics.HitBox;
import com.almasb.fxgl.physics.PhysicsComponent;
import com.almasb.fxgl.physics.box2d.dynamics.BodyType;
import com.almasb.fxgl.physics.box2d.dynamics.FixtureDef;
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
    private static final double EMBER_INTERVAL = 0.35; // faint embers rising out of the abyss
    private final List<ParallaxLayer> layers = new ArrayList<>();
    private double emberTimer = 0;
    private double cleanupTimer = 0;
    private static final double CLEANUP_INTERVAL = 0.5;
    private PlayerStatsComponent playerStats;
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
        settings.setSceneFactory(new SceneFactory() {
            @NotNull
            @Override
            public com.almasb.fxgl.app.scene.FXGLMenu newGameMenu() {
                return new GameMenu();
            }
        });
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

        getInput().addAction(new UserAction("Toggle Fullscreen") {
            @Override protected void onActionBegin() {
                getSettings().getFullScreen().set(!getSettings().getFullScreen().get());
            }
        }, KeyCode.F11);

        getInput().addAction(new UserAction("Toggle Skill Tree") {
            @Override protected void onActionBegin() {
                if (skillTree.isOpen()) skillTree.close();
                else skillTree.open();
            }
        }, KeyCode.I);

        getInput().addAction(new UserAction("Attack") {
            @Override protected void onActionBegin() { if (!skillTree.isOpen()) loadout().primaryPress(); }
            @Override protected void onActionEnd() { loadout().primaryRelease(); }
        }, MouseButton.PRIMARY);

        getInput().addAction(new UserAction("Secondary") {
            @Override protected void onActionBegin() { if (!skillTree.isOpen()) loadout().secondaryPress(); }
            @Override protected void onActionEnd() { loadout().secondaryRelease(); }
        }, MouseButton.SECONDARY);

        for (int i = 0; i < SLOT_KEYS.length; i++) {
            final int slot = i;
            getInput().addAction(new UserAction("Hotbar " + (i + 1)) {
                @Override protected void onActionBegin() {
                    SkillType hovered = skillTree.isOpen() ? skillTree.getHoveredSkill() : null;
                    if (hovered != null) SkillManager.get().assign(hovered, slot);
                    else loadout().pressSlot(slot);
                }
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
        setupPlayer();
        playerStats = player.getComponent(PlayerStatsComponent.class);
        getGameScene().getViewport().setBounds(0, -SHAKE_MARGIN, Integer.MAX_VALUE, (int) WINDOW_HEIGHT + SHAKE_MARGIN);
        skillTree = new SkillTreeView();
        hud = new Hud();
        setupBg();
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
                .with(new SpriteComponent("Player", false, true))
                .collidable()
                .buildAndAttach();
    }

    private void setupBg() {
        layers.add(addLayer(Assets.pixels("bg_far.png", BG_SCALE), 0.03, 0));
        layers.add(addLayer(Assets.pixels("bg_mid.png", BG_SCALE), 0.08, 0));
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
        if (player.getY() > WINDOW_HEIGHT + 100) {
            getGameController().exit();
        }
        Impact.update(tpf);
        updateCamera();
        levelGeneration.generateLevel(player.getX());
        cleanupPlatforms(tpf);
        scrollBg(tpf);
        if (playerStats.isDead()) {
            getGameController().exit();
        }
        hud.update();
    }


    //-----BACKGROUND-----
    private void scrollBg(double tpf) {
        double cameraX = getGameScene().getViewport().getX();
        for (ParallaxLayer layer : layers) layer.scroll(cameraX);

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
        getGameScene().getViewport().setX(cameraX + Impact.getShakeX());
        getGameScene().getViewport().setY(Impact.getShakeY());
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
        launch(args);
    }

    public static Entity getPlayer() {
        return player;
    }
}
