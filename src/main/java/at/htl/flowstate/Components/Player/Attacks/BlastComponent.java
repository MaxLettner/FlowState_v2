package at.htl.flowstate.Components.Player.Attacks;

import at.htl.flowstate.Assets;
import at.htl.flowstate.Combat.Combat;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Visuals.FrameSprite;
import at.htl.flowstate.Visuals.Frames;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import javafx.geometry.Point2D;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;

/** A growing area that hits every enemy once (explosions, freeze, shockwaves), drawn as an animated sprite. */
public class BlastComponent extends Component {
    private static final double ENEMY_HALF_SIZE = 20;

    /** Sprite strip, frame count, the art's radius in native px and whether it sits on the ground. */
    public enum Style {
        FIRE("Explosion_fire.png", 7, 22, false),
        ICE("Explosion_ice.png", 6, 22, false),
        DUST("Shockwave_dust.png", 6, 34, true);

        final String texture;
        final int frames;
        final double artRadius;
        final boolean grounded;

        Style(String texture, int frames, double artRadius, boolean grounded) {
            this.texture = texture;
            this.frames = frames;
            this.artRadius = artRadius;
            this.grounded = grounded;
        }
    }

    private final double maxRadius;
    private final double growTime;
    private final double totalTime;
    private final Consumer<Entity> onHit;
    private final Set<Entity> alreadyHit = new HashSet<>();
    private double elapsed = 0;

    private BlastComponent(double maxRadius, double growTime, double fadeTime, Consumer<Entity> onHit) {
        this.maxRadius = maxRadius;
        this.growTime = growTime;
        this.totalTime = growTime + fadeTime;
        this.onHit = onHit;
    }

    public static void spawn(Point2D center, double radius, double growTime, double fadeTime, Style style, Consumer<Entity> onHit) {
        // JavaFX filters scaled or sub-pixel ImageViews even with smooth=false, so the strip is upscaled
        // on the CPU by a whole factor and drawn 1:1 on whole pixels instead of being stretched to the radius.
        int scale = Math.max(1, (int) Math.round(radius / style.artRadius));
        Frames frames = Assets.frames(style.texture, style.frames, scale);
        double w = frames.width(), h = frames.height();
        FrameSprite sprite = new FrameSprite(frames).once(frames, style.frames / (growTime + fadeTime), null);
        double left = -w / 2, top = style.grounded ? -h + 2 * scale : -h / 2;
        sprite.setTranslateX(Math.round(center.getX() + left) - center.getX());
        sprite.setTranslateY(Math.round(center.getY() + top) - center.getY());

        entityBuilder()
                .at(center)
                .view(sprite)
                .with(new BlastComponent(radius, growTime, fadeTime, onHit))
                .zIndex(15)
                .buildAndAttach();

        switch (style) {
            case FIRE -> Particles.of(Particles.EMBER).count(16).angle(-90, 180).speed(150, 450)
                    .life(0.6).size(6).gravity(500).at(center);
            case ICE -> {
                Sfx.FREEZE.at(center);
                Particles.of(Particles.ICE).count(14).angle(-90, 180).speed(150, 400).life(0.5).size(5).gravity(400).at(center);
            }
            case DUST -> {
                Sfx.SLAM_IMPACT.at(center);
                Particles.of(Particles.DUST).count(8).angle(-160, 20).speed(150, 350).life(0.5).size(6).gravity(700).at(center);
                Particles.of(Particles.DUST).count(8).angle(-20, 20).speed(150, 350).life(0.5).size(6).gravity(700).at(center);
            }
        }
    }

    @Override
    public void onUpdate(double tpf) {
        boolean growing = elapsed < growTime;
        elapsed += tpf;
        double radius = maxRadius * Math.min(elapsed / growTime, 1.0);

        if (growing) {
            Point2D center = entity.getPosition();
            for (Entity e : Combat.enemies()) {
                if (!alreadyHit.contains(e) && e.getCenter().distance(center) <= radius + ENEMY_HALF_SIZE) {
                    alreadyHit.add(e);
                    onHit.accept(e);
                }
            }
        }
        double t = elapsed / totalTime;
        if (t > 0.85) entity.setOpacity(Math.max(0, 1 - (t - 0.85) / 0.15));
        if (t >= 1) entity.removeFromWorld();
    }
}
