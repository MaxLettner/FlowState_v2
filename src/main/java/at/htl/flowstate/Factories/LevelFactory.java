package at.htl.flowstate.Factories;

import at.htl.flowstate.Assets;
import javafx.scene.Group;
import javafx.scene.image.Image;
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

    /**
     * Textured terrain: the body tile is anchored to world coordinates so neighbouring blocks line up,
     * plus a crust / cap-stone strip along the top edge.
     */
    private static Group terrainView(double x, double y, double w, double h, boolean structure) {
        String body = structure ? "Platform.png" : "Ground.png";
        String top = structure ? "PlatformTop.png" : "GroundTop.png";
        Image bodyImg = Assets.pixels(body, TILE_SCALE);
        Image topImg = Assets.pixels(top, TILE_SCALE);
        double bw = bodyImg.getWidth(), bh = bodyImg.getHeight();

        Rectangle fill = new Rectangle(w, h);
        fill.setFill(new ImagePattern(bodyImg, -mod(x, bw), -mod(y, bh), bw, bh, false));

        double topY = structure ? 0 : -2;
        double topH = Math.min(topImg.getHeight(), h - topY);
        Rectangle edge = new Rectangle(w, topH);
        edge.setTranslateY(topY);
        edge.setFill(new ImagePattern(topImg, -mod(x, topImg.getWidth()), 0, topImg.getWidth(), topImg.getHeight(), false));
        return new Group(fill, edge);
    }

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