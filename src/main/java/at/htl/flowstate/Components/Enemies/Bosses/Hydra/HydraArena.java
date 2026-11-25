package at.htl.flowstate.Components.Enemies.Bosses.Hydra;

import at.htl.flowstate.Assets;
import at.htl.flowstate.Components.Enemies.Bosses.BossArena;
import at.htl.flowstate.Components.Enemies.Bosses.BossGateComponent;
import at.htl.flowstate.Components.Enemies.Bosses.Surface;
import at.htl.flowstate.Components.Identifier.PlatformIdentifierComponent;
import at.htl.flowstate.Components.Player.PlayerMovementComponent;
import at.htl.flowstate.Components.Player.PlayerStatsComponent;
import at.htl.flowstate.Generation.LevelGeneration;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Visuals.Impact;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.core.math.FXGLMath;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import com.almasb.fxgl.physics.BoundingShape;
import com.almasb.fxgl.physics.HitBox;
import com.almasb.fxgl.physics.PhysicsComponent;
import com.almasb.fxgl.physics.box2d.dynamics.BodyType;
import com.almasb.fxgl.physics.box2d.dynamics.FixtureDef;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.ArrayList;
import java.util.List;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;
import static com.almasb.fxgl.dsl.FXGL.getGameWorld;

/**
 * The Tar Hydra's arena (BossArena runs the gates, camera and cutscenes; this is what is special about hers).
 * <pre>
 *   0 ...... 300 ........................................ 1340 ......... 1920   (x from the arena's left edge)
 *   left ledge |  tar lake, five basalt pillars sticking out  | right ledge (where the player arrives)
 *              |   ▮420   ▮590    ▮820    ▮1050   ▮1220     |
 * </pre>
 * Until the fight the lake is covered by one plain floor slab. In the intro the slab sinks into the tar and the
 * pillars push up out of it; in the last phase ({@link #rise}) the tar rises over both ledges, so only the pillars
 * are left to stand on. Falling into the tar costs {@link #FALL_DAMAGE}, slows the player for a moment and puts them
 * back on the nearest pillar or ledge. When the Hydra dies the tar drains away and the floor comes back.
 */
public final class HydraArena {
    public static final double LAKE_FROM = 300, LAKE_TO = 1340;
    /** The pillars: arena x of the centre, height of the top above the floor. */
    private static final double[][] PILLARS = {{420, 120}, {590, 240}, {820, 90}, {1050, 240}, {1220, 120}};
    private static final double PILLAR_W = 112, PILLAR_DEPTH = 420;
    private static final double SURFACE_LOW = 20, SURFACE_HIGH = -60;    // the tar's surface relative to the floor
    private static final double SURFACE_START = 260;                     // where it comes up from in the intro
    private static final double OPEN_SPEED = 240, RISE_SPEED = 30, DRAIN_SPEED = 220;
    private static final double PILLAR_RISE = 0.9;                       // s
    private static final double FALL_DAMAGE = 15, TARRED = 2.0;
    private static final double CATCH_BELOW = 30;                        // the player's feet this far under: caught
    private static final int LAKE_FRAMES = 4;
    private static final double LAKE_FPS = 5;
    static final Color[] TAR = {Color.web("#46404f"), Color.web("#28222d"), Color.web("#17131a"), Color.web("#8a8698")};
    private static final Color TAR_DEEP = Color.web("#17131a");

    private final BossArena arena;
    private final double lockX, groundY;
    private Entity floor, lake;
    private final List<Entity> pillars = new ArrayList<>();
    private boolean open, draining;
    private double surface, surfaceTarget;

    /** {@code lakeFloor}: the ground LevelGeneration laid over the lake (sinks in the intro). */
    public HydraArena(BossArena arena, double lockX, double groundY, Entity lakeFloor) {
        this.arena = arena;
        this.lockX = lockX;
        this.groundY = groundY;
        floor = lakeFloor;
    }

    double lockX() { return lockX; }
    double groundY() { return groundY; }

    /** The tar's surface (world y). */
    double surface() {
        return open ? surface : groundY + SURFACE_LOW;
    }

    /** The ledges are under the tar (last phase). */
    boolean flooded() {
        return open && surface < groundY - 1;
    }

    //-----the intro, the last phase, the end-----
    /** The floor over the lake sinks into the tar, the tar comes up and the basalt pillars push out of it. */
    public void open() {
        if (floor != null && floor.isActive()) {
            double y = floor.getY();
            for (double x = floor.getX(); x < floor.getRightX(); x += 70) {
                Particles.of(Particles.DUST).count(5).angle(90, 50).speed(60, 260)
                        .life(1.0).size(9).gravity(900).at(new Point2D(x + 35, y + 10));
                Particles.of(TAR).count(4).angle(-90, 60).speed(80, 260)
                        .life(0.9).size(6).gravity(700).at(new Point2D(x + 35, y + 6));
            }
            floor.removeFromWorld();
        }
        floor = null;
        open = true;
        draining = false;
        surface = groundY + SURFACE_START;
        surfaceTarget = groundY + SURFACE_LOW;
        lake = entityBuilder().at(lockX + LAKE_FROM, surface).zIndex(11).with(new LakeComponent()).buildAndAttach();
        for (double[] p : PILLARS) pillars.add(pillar(lockX + p[0], groundY - p[1]));
        Impact.quake(22);
        Sfx.STONE_CRACK.at(new Point2D(lockX + (LAKE_FROM + LAKE_TO) / 2, groundY), 1, 0.5);
        Sfx.TAR_SPLASH.at(new Point2D(lockX + (LAKE_FROM + LAKE_TO) / 2, groundY), 1, 0.5);
    }

    /** The last phase: the tar rises over both ledges. */
    void rise() {
        surfaceTarget = groundY + SURFACE_HIGH;
        Sfx.TAR_WAVE.at(new Point2D(lockX + (LAKE_FROM + LAKE_TO) / 2, groundY), 1, 0.6);
        Impact.quake(16);
    }

    /** The fight is over (or gone): the tar drains, the pillars sink, the floor comes back. */
    public void finish() {
        getGameWorld().getEntitiesByComponent(TarGlobComponent.class).forEach(Entity::removeFromWorld);
        getGameWorld().getEntitiesByComponent(TarPoolComponent.class).forEach(Entity::removeFromWorld);
        getGameWorld().getEntitiesByComponent(TarWaveComponent.class).forEach(Entity::removeFromWorld);
        getGameWorld().getEntitiesByComponent(LureSpotComponent.class).forEach(Entity::removeFromWorld);
        if (!open) return;
        open = false;
        draining = true;
        for (Entity p : pillars) {
            if (!p.isActive()) continue;
            Particles.of(Particles.ROCK).count(8).angle(-90, 60).speed(60, 240)
                    .life(0.8).size(6).gravity(900).at(new Point2D(p.getCenter().getX(), p.getY() + 6));
            p.removeFromWorld();
        }
        pillars.clear();
        double x = lockX + LAKE_FROM, w = LAKE_TO - LAKE_FROM;
        floor = LevelGeneration.spawnGroundSegment(x, groundY, w);
        floor.setOpacity(0);
        Impact.shake(14);
        Sfx.TAR_WAVE.at(new Point2D(x + w / 2, groundY), 0.8, 1.3);
    }

    /** A basalt pillar: solid from its top down into the tar; it rises out of the tar in the intro, solid as it goes. */
    private Entity pillar(double cx, double top) {
        Image topImg = Assets.pixels("BasaltPillar_top.png", 2);
        Image shaft = Assets.pixels("BasaltPillar_shaft.png", 2);
        Group view = new Group();
        ImageView head = new ImageView(topImg);
        head.setSmooth(false);
        view.getChildren().add(head);
        for (double y = topImg.getHeight(); y < PILLAR_DEPTH; y += shaft.getHeight()) {
            ImageView v = new ImageView(shaft);
            v.setSmooth(false);
            v.setTranslateY(y);
            view.getChildren().add(v);
        }
        FixtureDef fd = new FixtureDef();
        fd.getFilter().categoryBits = 0x0001;
        fd.getFilter().maskBits = (short) 0xFFFF;
        PhysicsComponent physics = new PhysicsComponent();
        physics.setBodyType(BodyType.STATIC);
        physics.setFixtureDef(fd);
        double x = cx - PILLAR_W / 2;
        return entityBuilder()
                .at(x, top + PILLAR_DEPTH)                   // sunk; PillarComponent pushes it up, body and all
                .bbox(new HitBox(BoundingShape.box(PILLAR_W, PILLAR_DEPTH)))
                .view(view)
                .with(physics)
                .with(new PlatformIdentifierComponent())
                .with(new PillarComponent(top))
                .collidable()
                .zIndex(-30)                   // behind the whole Hydra and the player
                .buildAndAttach();
    }

    //-----queries-----
    /** Surfaces something can stand on, ledges first (gone while flooded). */
    List<Surface> surfaces() {
        List<Surface> out = new ArrayList<>();
        if (!flooded()) {
            out.add(new Surface(lockX + BossGateComponent.WIDTH, lockX + LAKE_FROM, groundY));
            out.add(new Surface(lockX + LAKE_TO, lockX + BossArena.WIDTH - BossGateComponent.WIDTH, groundY));
        }
        for (Entity p : pillars) {
            if (p.isActive()) out.add(new Surface(p.getX(), p.getRightX(), p.getY()));
        }
        return out;
    }

    /** The surface at x whose top is at or below y (the first one something dropping from y would meet), or null. */
    Surface surfaceUnder(double x, double y) {
        Surface best = null;
        for (Surface s : surfaces()) {
            if (!s.spans(x) || s.top() < y) continue;
            if (best == null || s.top() < best.top()) best = s;
        }
        return best;
    }

    /** What is under x: a surface's top, or the tar. */
    double floorAt(double x, double y) {
        Surface s = surfaceUnder(x, y);
        return s != null ? s.top() : surface();
    }

    /** Whether x is over the tar (between the ledges, or anywhere while flooded). */
    boolean overTar(double x) {
        return flooded() || (x > lockX + LAKE_FROM && x < lockX + LAKE_TO);
    }

    /** The player fell into the tar: hurt, slowed, and back on the nearest footing. @return true if caught */
    public boolean catchFall(Entity player) {
        // once the tar is over the ledges they are no footing at all: standing on one counts as falling in
        double below = flooded() ? Math.min(surface + CATCH_BELOW, groundY - 2) : surface + CATCH_BELOW;
        if (!open || player.getBottomY() < below || !overTar(player.getCenter().getX())) return false;
        PlayerStatsComponent stats = player.getComponent(PlayerStatsComponent.class);
        if (stats.isDead()) return false;
        double x = player.getCenter().getX();
        Point2D best = null;
        double bestD = Double.MAX_VALUE;
        for (Surface s : surfaces()) {
            if (s.top() > surface - 10) continue;
            double cx = Math.max(s.fromX() + 24, Math.min(s.toX() - 24, x));
            double d = Math.abs(cx - x);
            if (d < bestD) {
                bestD = d;
                best = new Point2D(cx, s.top());
            }
        }
        if (best == null) return false;
        Particles.of(TAR).count(22).angle(-90, 50).speed(120, 420).life(0.9).size(7).gravity(900).at(new Point2D(x, surface));
        player.getComponent(PlayerMovementComponent.class).teleport(
                new Point2D(best.getX() - player.getWidth() / 2, best.getY() - player.getHeight() - 4));
        if (!arena.inCutscene()) {            // the tar rising over the ledge in the cutscene just moves you
            stats.takeDamage(FALL_DAMAGE, null, false);
            stats.chill(TARRED);
        }
        Sfx.TAR_SPLASH.at(new Point2D(x, surface), 1, 0.8);
        Sfx.FALL.play(0.6, 1.2);
        Particles.of(TAR).count(16).angle(90, 70).speed(40, 160).life(0.9).size(5).gravity(600).at(player.getCenter());
        return true;
    }

    //-----the lake-----
    /** The tar: a dark body under an animated surface strip, following the level the arena sets. */
    private class LakeComponent extends Component {
        private final List<ImageView> tiles = new ArrayList<>();
        private Image[] frames;
        private Rectangle body;
        private double age, bubble;

        @Override
        public void onAdded() {
            frames = new Image[LAKE_FRAMES];
            for (int i = 0; i < LAKE_FRAMES; i++) frames[i] = Assets.frameImage("TarLake.png", LAKE_FRAMES, 2, i);
            // laid out wide enough to cover the ledges once it floods them; fit() shows the part that is tar
            double to = BossArena.WIDTH - LAKE_FROM;
            body = new Rectangle(0, 20, LAKE_TO - LAKE_FROM, 1600);
            body.setFill(TAR_DEEP);
            Group g = new Group(body);
            double tw = frames[0].getWidth();
            for (double x = -Math.ceil(LAKE_FROM / tw) * tw; x < to; x += tw) {      // one tile starts at the lake's edge
                ImageView v = new ImageView(frames[0]);
                v.setSmooth(false);
                v.setTranslateX(x);
                tiles.add(v);
                g.getChildren().add(v);
            }
            entity.getViewComponent().addChild(g);
            fit();
        }

        /** Only the stretch between the ledges until the tar floods them. */
        private void fit() {
            boolean wide = flooded();
            double from = wide ? -LAKE_FROM : 0, w = wide ? BossArena.WIDTH : LAKE_TO - LAKE_FROM;
            body.setX(from);
            body.setWidth(w);
            for (ImageView v : tiles) {
                double x = v.getTranslateX();
                v.setVisible(x + frames[0].getWidth() > from && x < from + w);
                v.setViewport(null);
                if (!wide && x < w && x + frames[0].getWidth() > w) {
                    v.setViewport(new javafx.geometry.Rectangle2D(0, 0, w - x, frames[0].getHeight()));
                }
            }
        }

        @Override
        public void onUpdate(double tpf) {
            age += tpf;
            double speed = draining ? DRAIN_SPEED : surface > surfaceTarget + 1 && surfaceTarget > groundY ? OPEN_SPEED : RISE_SPEED;
            if (draining) surfaceTarget = groundY + SURFACE_START + 200;
            if (surface > surfaceTarget) surface = Math.max(surfaceTarget, surface - speed * tpf);
            else surface = Math.min(surfaceTarget, surface + speed * tpf);
            entity.setY(surface);
            Image f = frames[(int) (age * LAKE_FPS) % LAKE_FRAMES];
            for (ImageView v : tiles) v.setImage(f);
            fit();
            if ((bubble -= tpf) <= 0) {
                bubble = 0.18;
                double x = lockX + LAKE_FROM + FXGLMath.randomDouble() * (LAKE_TO - LAKE_FROM);
                Particles.of(TAR).count(2).angle(-90, 30).speed(30, 90)
                        .life(0.6).size(5).gravity(300).at(new Point2D(x, surface + 4));
            }
            if (draining) {
                if (floor != null) floor.setOpacity(Math.min(1, floor.getOpacity() + tpf * 1.5));
                if (surface >= surfaceTarget - 1) {
                    draining = false;
                    if (floor != null) floor.setOpacity(1);
                    entity.removeFromWorld();
                    lake = null;
                }
            }
        }
    }

    /**
     * A pillar pushing up out of the tar in the intro. The body moves with the look, so there is never an invisible
     * pillar top to stand on.
     */
    private class PillarComponent extends Component {
        private final double top;
        private double age;

        PillarComponent(double top) {
            this.top = top;
        }

        @Override
        public void onUpdate(double tpf) {
            if (age >= PILLAR_RISE) return;
            age += tpf;
            double k = Math.min(1, age / PILLAR_RISE);
            double ease = 1 - (1 - k) * (1 - k) * (1 - k);
            Point2D at = new Point2D(entity.getX(), top + PILLAR_DEPTH * (1 - ease));
            PhysicsComponent physics = entity.getComponent(PhysicsComponent.class);
            if (physics.getBody() != null) physics.overwritePosition(at);
            else entity.setPosition(at);
            if (FXGLMath.randomDouble() < tpf * 14) {
                Particles.of(TAR).count(2).angle(-90, 40).speed(60, 200)
                        .life(0.6).size(5).gravity(700).at(new Point2D(entity.getCenter().getX() + (FXGLMath.randomDouble() - 0.5) * PILLAR_W, surface()));
            }
            if (k >= 1) Impact.shake(6);
        }
    }
}
