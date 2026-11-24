package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Assets;
import at.htl.flowstate.Components.SpriteComponents.DemonSpriteComponent;
import at.htl.flowstate.Components.SpriteComponents.SpriteComponent;
import at.htl.flowstate.Game;
import at.htl.flowstate.Visuals.FrameSprite;
import at.htl.flowstate.Visuals.Frames;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.ImagePattern;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;

/**
 * The Hollow Ember's arena (BossArena runs the gates, camera and cutscenes; this is what is special about it): the
 * floor across the arena turns to black glass, with a faint reflection of the player and of the boss under the
 * floor line. In the intro the player's reflection lags behind them, stops and is where the Hollow climbs out.
 * It is one overlay on top of the ordinary ground, so {@link #finish} just removes it.
 */
public final class HollowArena {
    private static final double DEPTH = 130;                    // how far the glass shows under the floor line
    private static final double REFLECTION_OPACITY = 0.3;
    private static final double LAG = 5;                         // the lagging reflection follows at this rate
    private static final double OFFSET_X = 20 - 64, OFFSET_Y = 80 - 126;   // the player's 128 px frame on the 40x80 body
    private static final Color GLASS = Color.web("#07040e");
    private static final Color[] SHARDS = {Color.web("#d8b4ff"), Color.web("#a86cff"), Color.web("#4a1a9a")};

    private static double lockX, groundY;
    private static Entity panel;
    private static boolean intro, hidePlayer;
    private static double riseX;

    private HollowArena() {}

    public static void reset() {
        panel = null;
        intro = hidePlayer = false;
    }

    /** The glass floor appears (the intro); the player's reflection starts to lag. */
    static void open(double lockX, double groundY, double riseX) {
        HollowArena.lockX = lockX;
        HollowArena.groundY = groundY;
        HollowArena.riseX = riseX;
        intro = true;
        hidePlayer = false;
        double w = BossArena.WIDTH;
        Group view = new Group();

        Rectangle base = new Rectangle(w, DEPTH);
        base.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, GLASS.deriveColor(0, 1, 1, 0.8)), new Stop(1, GLASS.deriveColor(0, 1, 1, 0.97))));
        Group reflections = new Group();
        Rectangle clip = new Rectangle(w, DEPTH);
        reflections.setClip(clip);
        reflections.setOpacity(REFLECTION_OPACITY);
        Rectangle fade = new Rectangle(w, DEPTH);
        fade.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.TRANSPARENT), new Stop(1, GLASS)));
        Image top = Assets.pixels("HollowGlassTop.png", 2);
        Rectangle edge = new Rectangle(w, top.getHeight());
        edge.setTranslateY(-2);
        edge.setFill(new ImagePattern(top, 0, 0, top.getWidth(), top.getHeight(), false));
        view.getChildren().addAll(base, reflections, fade, edge);

        panel = entityBuilder().at(lockX, groundY).view(view)
                .with(new Reflection(reflections))
                .zIndex(11).buildAndAttach();
        for (double x = lockX; x < lockX + w; x += 90) {
            Particles.burst(new Point2D(x + 45, groundY), SHARDS, 3, -90, 60, 60, 220, 0.8, 5, 500);
        }
    }

    /** The boss is out of the glass: the player's reflection is just theirs again. */
    static void introDone() {
        intro = false;
        hidePlayer = false;
    }

    /** The boss climbs out of the player's reflection: it is gone. */
    static void reflectionLeft() {
        hidePlayer = true;
    }

    /** The fight is over: the glass goes and the ordinary ground is back. */
    static void finish() {
        if (panel != null && panel.isActive()) {
            for (double x = lockX; x < lockX + BossArena.WIDTH; x += 90) {
                Particles.burst(new Point2D(x + 45, groundY), SHARDS, 2, -90, 60, 60, 200, 0.7, 5, 500);
            }
            panel.removeFromWorld();
        }
        panel = null;
        intro = hidePlayer = false;
    }

    /** Draws the flipped copies of the player and the boss under the floor line. */
    private static final class Reflection extends Component {
        private final Group group;
        private FrameSprite playerRef, bossRef;
        private double lagX = Double.NaN;

        Reflection(Group group) {
            this.group = group;
        }

        @Override
        public void onUpdate(double tpf) {
            Entity player = Game.getPlayer();
            SpriteComponent sc = player.getComponent(SpriteComponent.class);
            FrameSprite src = sc.getSprite();
            if (playerRef == null) {
                playerRef = new FrameSprite(src.getFrames());
                group.getChildren().add(playerRef);
            }
            double x = player.getX();
            if (intro) {
                // lags behind the player, then slides off to where the Hollow will come out and stops there
                double target = BossArena.time() > 1.0 ? riseX - 20 : x;
                if (Double.isNaN(lagX)) lagX = x;
                lagX += (target - lagX) * Math.min(1, tpf * (target == x ? LAG : 2.2));
                x = lagX;
            } else {
                lagX = Double.NaN;
            }
            playerRef.setVisible(!hidePlayer);
            playerRef.show(src.getFrames(), src.getFrameIndex());
            playerRef.setScaleX(intro && BossArena.time() > 1.0 ? (player.getX() >= lagX ? 1 : -1) : sc.getFacing());
            place(playerRef, x + OFFSET_X, player.getY() + OFFSET_Y, src.getFrames());

            Entity boss = HollowBehaviour.current();
            if (boss == null) {
                if (bossRef != null) bossRef.setVisible(false);
                return;
            }
            FrameSprite node = (FrameSprite) boss.getComponent(DemonSpriteComponent.class).getNode();
            if (bossRef == null) {
                bossRef = new FrameSprite(node.getFrames());
                group.getChildren().add(bossRef);
            }
            bossRef.setVisible(true);
            bossRef.show(node.getFrames(), node.getFrameIndex());
            bossRef.setScaleX(node.getScaleX());
            place(bossRef, boss.getX() + node.getTranslateX(), boss.getY() + node.getTranslateY(), node.getFrames());
        }

        /** (left, top) is the frame's top left in the world: its mirror image under the floor line. */
        private void place(FrameSprite ref, double left, double top, Frames frames) {
            ref.setScaleY(-1);
            ref.setTranslateX(left - lockX);
            ref.setTranslateY(groundY - top - frames.height());
        }
    }
}
