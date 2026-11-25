package at.htl.flowstate.Factories;

import at.htl.flowstate.Assets;
import com.almasb.fxgl.core.math.FXGLMath;
import javafx.scene.Group;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.ImagePattern;
import at.htl.flowstate.Components.Chests.ChestComponent;
import at.htl.flowstate.Components.Identifier.PlatformIdentifierComponent;
import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.EntityFactory;
import com.almasb.fxgl.entity.SpawnData;
import com.almasb.fxgl.entity.Spawns;
import com.almasb.fxgl.physics.BoundingShape;
import com.almasb.fxgl.physics.PhysicsComponent;
import com.almasb.fxgl.physics.box2d.dynamics.BodyType;
import com.almasb.fxgl.physics.box2d.dynamics.FixtureDef;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

public class LevelFactory implements EntityFactory {
    private static final int TILE_SCALE = 2;

    @Spawns("platform")
    public Entity newPlatform(SpawnData data) {
        Color color = data.get("color");
        double w = data.get("width");
        double h = data.get("height");

        FixtureDef platFd = new FixtureDef();
        platFd.getFilter().categoryBits = 0x0001;
        platFd.getFilter().maskBits = (short) 0xFFFF;

        PhysicsComponent platPhysics = new PhysicsComponent();
        platPhysics.setBodyType(BodyType.STATIC);
        platPhysics.setFixtureDef(platFd);

        return FXGL.entityBuilder(data)
                .bbox(BoundingShape.box(w, h))
                .view(terrainView(data.getX(), data.getY(), w, h, Color.SADDLEBROWN.equals(color)))
                .with(platPhysics)
                .with(new PlatformIdentifierComponent())
                .collidable()
                .zIndex(10)
                .build();
    }

    // sprite strips from tools/art/terrain.py
    private static final int PLATFORM_VARIANTS = 3; // black iron, obsidian + bone, rune stone
    private static final int EMBED_FRAMES = 6;      // bones, skull, crystals, magma ... buried in the ground
    private static final int SURFACE_FRAMES = 6;    // decorations standing on the ground

    /**
     * Textured terrain: the body tile is anchored to world coordinates so neighbouring blocks line up,
     * plus a crust / cap-stone strip along the top edge. Floating platforms pick a random variant and get
     * something hanging below; ground gets random things buried in it.
     */
    private static Group terrainView(double x, double y, double w, double h, boolean structure) {
        int variant = FXGLMath.random(0, PLATFORM_VARIANTS - 1);
        Image bodyImg = structure ? Assets.frameImage("Platform.png", PLATFORM_VARIANTS, TILE_SCALE, variant)
                : Assets.pixels("Ground.png", TILE_SCALE);
        Image topImg = structure ? Assets.frameImage("PlatformTop.png", PLATFORM_VARIANTS, TILE_SCALE, variant)
                : Assets.pixels("GroundTop.png", TILE_SCALE);
        double bw = bodyImg.getWidth(), bh = bodyImg.getHeight();

        Rectangle fill = new Rectangle(w, h);
        fill.setFill(new ImagePattern(bodyImg, -mod(x, bw), -mod(y, bh), bw, bh, false));
        Group view = new Group(fill);

        if (structure) {
            Image under = Assets.frameImage("PlatformUnder.png", PLATFORM_VARIANTS, TILE_SCALE, variant);
            Rectangle below = new Rectangle(w, under.getHeight());
            below.setTranslateY(h);
            below.setFill(new ImagePattern(under, 0, 0, under.getWidth(), under.getHeight(), false));
            view.getChildren().add(below);
        } else {
            addEmbeds(view, w);
        }

        double topY = structure ? 0 : -2;
        double topH = Math.min(topImg.getHeight(), h - topY);
        Rectangle edge = new Rectangle(w, topH);
        edge.setTranslateY(topY);
        edge.setFill(new ImagePattern(topImg, -mod(x, topImg.getWidth()), 0, topImg.getWidth(), topImg.getHeight(), false));
        view.getChildren().add(edge);
        return view;
    }

    /** A few random bones / skulls / crystals buried in the visible upper part of a ground segment. */
    private static void addEmbeds(Group view, double w) {
        Image probe = Assets.frameImage("GroundEmbed.png", EMBED_FRAMES, TILE_SCALE, 0);
        double fw = probe.getWidth();
        if (w < fw) return;
        int count = (int) (w / 260) + (FXGLMath.randomDouble() < 0.6 ? 1 : 0);
        for (int i = 0; i < count; i++) {
            ImageView v = new ImageView(Assets.frameImage("GroundEmbed.png", EMBED_FRAMES, TILE_SCALE,
                    FXGLMath.random(0, EMBED_FRAMES - 1)));
            v.setSmooth(false);
            v.setTranslateX(FXGLMath.randomDouble() * (w - fw));
            v.setTranslateY(28 + FXGLMath.randomDouble() * 110);
            v.setScaleX(FXGLMath.randomDouble() < 0.5 ? -1 : 1);
            view.getChildren().add(v);
        }
    }

    /** Purely visual decoration standing on the ground, drawn behind the player and enemies. */
    @Spawns("decor")
    public Entity newDecor(SpawnData data) {
        int frame = data.get("frame");
        ImageView v = new ImageView(Assets.frameImage("GroundSurface.png", SURFACE_FRAMES, TILE_SCALE, frame));
        v.setSmooth(false);
        v.setScaleX(FXGLMath.randomDouble() < 0.5 ? -1 : 1);
        return FXGL.entityBuilder(data)
                .view(v)
                .zIndex(-2)
                .build();
    }

    /** Ambient ruin (destroyed house, colonnade, tower stump, archway) behind the player, purely visual. */
    @Spawns("ruin")
    public Entity newRuin(SpawnData data) {
        int frame = data.get("frame");
        ImageView v = new ImageView(Assets.frameImage("Ruins.png", RUIN_FRAMES, TILE_SCALE, frame));
        v.setSmooth(false);
        if (data.hasKey("flip") && (boolean) data.get("flip")) v.setScaleX(-1);
        return FXGL.entityBuilder(data)
                .view(v)
                .zIndex(-3)
                .build();
    }

    public static final int RUIN_FRAMES = 4;
    public static final double RUIN_WIDTH = 96 * TILE_SCALE;
    public static final double RUIN_HEIGHT = 72 * TILE_SCALE;

    private static double mod(double v, double m) {
        return ((v % m) + m) % m;
    }

    @Spawns("chest")
    public Entity newChest(SpawnData data) {
        double w = data.get("width");
        double h = data.get("height");

        return FXGL.entityBuilder(data)
                .bbox(BoundingShape.box(w, h))
                .with(new ChestComponent())
                .collidable()
                .zIndex(-1)
                .build();
    }

}