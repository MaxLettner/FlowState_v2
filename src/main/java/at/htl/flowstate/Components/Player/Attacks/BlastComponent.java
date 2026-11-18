package at.htl.flowstate.Components.Player.Attacks;

import at.htl.flowstate.Assets;
import at.htl.flowstate.Combat.Combat;
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
        Frames frames = Assets.frames(style.texture, style.frames, 2);
        double scale = radius / (style.artRadius * 2);   // frames are already x2
        double w = frames.width() * scale, h = frames.height() * scale;
        FrameSprite sprite = new FrameSprite(frames).once(frames, style.frames / (growTime + fadeTime), null);
        sprite.setFitWidth(w);
        sprite.setFitHeight(h);
        sprite.setTranslateX(-w / 2);
        sprite.setTranslateY(style.grounded ? -h + 4 * scale : -h / 2);

        entityBuilder()
                .at(center)
                .view(sprite)
                .with(new BlastComponent(radius, growTime, fadeTime, onHit))
                .zIndex(15)
                .buildAndAttach();

        switch (style) {
            case FIRE -> Particles.burst(center, Particles.EMBER, 16, -90, 180, 150, 450, 0.6, 6, 500);
            case ICE -> Particles.burst(center, Particles.ICE, 14, -90, 180, 150, 400, 0.5, 5, 400);
            case DUST -> {
                Particles.burst(center, Particles.DUST, 8, -160, 20, 150, 350, 0.5, 6, 700);
                Particles.burst(center, Particles.DUST, 8, -20, 20, 150, 350, 0.5, 6, 700);
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
