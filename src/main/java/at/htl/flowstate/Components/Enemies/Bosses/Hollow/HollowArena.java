package at.htl.flowstate.Components.Enemies.Bosses.Hollow;

import at.htl.flowstate.Assets;
import at.htl.flowstate.Components.Enemies.Bosses.BossArena;
import at.htl.flowstate.Components.SpriteComponents.DemonSpriteComponent;
import at.htl.flowstate.Components.SpriteComponents.SpriteComponent;
import at.htl.flowstate.Game;
import at.htl.flowstate.Generation.LevelGeneration;
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

    private final BossArena arena;
    private final double lockX, groundY;
    private Entity panel;
    private boolean intro, hidePlayer;
    private final double riseX;

    /** riseX: where the Hollow climbs out of the glass (world x). */
    public HollowArena(BossArena arena, double lockX, double groundY, double riseX) {
        this.arena = arena;
        this.lockX = lockX;
        this.groundY = groundY;
        this.riseX = riseX;
    }

    /** The glass floor appears (the intro); the player's reflection starts to lag. */
    public void open() {
        intro = true;
        hidePlayer = false;
        double w = BossArena.WIDTH;
        Group view = new Group();

        // the glass goes all the way down like the ground does; below the reflections it is solid black glass
        Rectangle base = new Rectangle(w, LevelGeneration.DEEP_FLOOR - groundY);
        base.setFill(new LinearGradient(0, 0, 0, DEPTH, false, CycleMethod.NO_CYCLE,
                new Stop(0, GLASS.deriveColor(0, 1, 1, 0.8)), new Stop(1, GLASS)));
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
            Particles.of(SHARDS).count(3).angle(-90, 60).speed(60, 220)
                    .life(0.8).size(5).gravity(500).at(new Point2D(x + 45, groundY));
        }
    }

    /** The boss is out of the glass: the player's reflection is just theirs again. */
    void introDone() {
        intro = false;
        hidePlayer = false;
    }

    /** The boss climbs out of the player's reflection: it is gone. */
    public void reflectionLeft() {
        hidePlayer = true;
    }

    /** The fight is over: the glass goes and the ordinary ground is back. */
    public void finish() {
        if (panel != null && panel.isActive()) {
            for (double x = lockX; x < lockX + BossArena.WIDTH; x += 90) {
                Particles.of(SHARDS).count(2).angle(-90, 60).speed(60, 200)
                        .life(0.7).size(5).gravity(500).at(new Point2D(x + 45, groundY));
            }
            panel.removeFromWorld();
        }
        panel = null;
        intro = hidePlayer = false;
    }

    /** Draws the flipped copies of the player and the boss under the floor line. */
    private final class Reflection extends Component {
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
                double target = arena.time() > 1.0 ? riseX - 20 : x;
                if (Double.isNaN(lagX)) lagX = x;
                lagX += (target - lagX) * Math.min(1, tpf * (target == x ? LAG : 2.2));
                x = lagX;
            } else {
                lagX = Double.NaN;
            }
            playerRef.setVisible(!hidePlayer);
            playerRef.show(src.getFrames(), src.getFrameIndex());
            playerRef.setScaleX(intro && arena.time() > 1.0 ? (player.getX() >= lagX ? 1 : -1) : sc.getFacing());
            place(playerRef, x + OFFSET_X, player.getY() + OFFSET_Y, src.getFrames());

            Entity boss = arena.fightingBoss();
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
