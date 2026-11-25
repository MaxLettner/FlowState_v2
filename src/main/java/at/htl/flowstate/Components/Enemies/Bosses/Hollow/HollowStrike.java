package at.htl.flowstate.Components.Enemies.Bosses.Hollow;

import at.htl.flowstate.Components.Enemies.EnemyProjectileComponent;
import at.htl.flowstate.Components.Player.Abilities.SkillMove;
import at.htl.flowstate.Components.Player.Helpers.DeleteAfterTimeComponent;
import at.htl.flowstate.Game;
import at.htl.flowstate.Visuals.FrameSprite;
import at.htl.flowstate.Visuals.Frames;
import at.htl.flowstate.Assets;
import at.htl.flowstate.Visuals.Particles;
import at.htl.flowstate.Visuals.WeaponArt;
import com.almasb.fxgl.core.math.FXGLMath;
import com.almasb.fxgl.dsl.components.OffscreenCleanComponent;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import com.almasb.fxgl.physics.BoundingShape;
import com.almasb.fxgl.physics.HitBox;
import javafx.geometry.Point2D;
import javafx.scene.image.ImageView;
import javafx.scene.Node;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;

/**
 * The Hollow Ember's void copies of the player's weapons: a swung arc, a punch and a flying projectile. Each is a
 * short-lived entity wearing the weapon's own texture recoloured to void; the damage goes through the Hollow
 * ({@link HollowBehaviour#strike}), so a landed hit steals an ember.
 */
final class HollowStrike {
    private static final double SIZE = 1.5;          // the Hollow is 1.5x the player, so are its weapons
    private static final double WINDUP = 0.35, STRIKE = 0.3;    // fractions of a swing

    private HollowStrike() {}

    /** A weapon swung from the boss's shoulder, in screen degrees (0 = right, 90 = down). */
    static void swing(HollowBehaviour owner, SkillMove m, double reach, int dir, double duration, double fromDeg, double toDeg) {
        double width = 12 * SIZE;
        ImageView tex = WeaponArt.hanging("void:" + m.texture(), width / 2, 0, SIZE);
        double length = reach * SIZE;
        entityBuilder()
                .bbox(new HitBox(BoundingShape.box(width, length)))
                .view(tex)
                .with(new SwingComponent(owner, m, duration, dir > 0 ? fromDeg : 180 - fromDeg, dir > 0 ? toDeg : 180 - toDeg))
                .zIndex(13)
                .buildAndAttach();
    }

    /** A glove thrown forward from the chest. */
    static void punch(HollowBehaviour owner, SkillMove m, int dir, double damage) {
        ImageView glove = Assets.sprite("void:" + m.texture(), 2);
        glove.setScaleX(dir * SIZE);
        glove.setScaleY(SIZE);
        double reach = m.reach() * SIZE;
        double w = Math.max(40, reach), h = 46;
        glove.setTranslateX(dir > 0 ? w - glove.getImage().getWidth() * SIZE / 2 - 10 : -glove.getImage().getWidth() * SIZE / 2 + 10);
        glove.setTranslateY(h / 2 - glove.getImage().getHeight() * SIZE / 2);
        Point2D chest = owner.chest();
        entityBuilder()
                .at(dir > 0 ? chest.getX() : chest.getX() - w, chest.getY() - h / 2)
                .bbox(new HitBox(BoundingShape.box(w, h)))
                .view(glove)
                .with(new HitOnceComponent(owner, m, damage))
                .with(new DeleteAfterTimeComponent(0.14))
                .zIndex(13)
                .buildAndAttach();
        Particles.of(Particles.VOID).count(5).angle(dir > 0 ? 0 : 180, 40).speed(80, 220)
                .life(0.3).size(4).gravity(0).at(chest.add(dir * w, 0));
    }

    /** A projectile with the copied skill's look, flying along {@code velocity}. */
    static void bolt(HollowBehaviour owner, SkillMove m, Point2D from, Point2D velocity, double damage) {
        SkillMove.Shot s = m.shot();
        double w = s.width() * (s.diagonal() ? 1 : 1), h = s.height();
        Node view = s.diagonal() ? WeaponArt.centred("void:" + s.texture(), w / 2, h / 2, 1) : sprite(s);
        Entity e = entityBuilder()
                .at(from.subtract(w / 2, h / 2))
                .bbox(new HitBox(BoundingShape.box(w, h)))
                .view(view)
                .with(new BoltComponent(velocity, m.gravity()))
                .with(new EnemyProjectileComponent(damage, p -> burst(p, s.explodes())).owner(owner.getEntity())
                        .onHurt(() -> owner.afterHit(m)))
                .with(new DeleteAfterTimeComponent(6))
                .with(new OffscreenCleanComponent())
                .zIndex(12)
                .build();
        e.setRotationOrigin(new Point2D(w / 2, h / 2));
        com.almasb.fxgl.dsl.FXGL.getGameWorld().addEntity(e);
    }

    private static Node sprite(SkillMove.Shot s) {
        Frames f = Assets.frames("void:" + s.texture(), s.frames(), s.scale());
        FrameSprite sprite = new FrameSprite(f);
        if (s.frames() > 1 && s.fps() > 0) sprite.loop(f, s.fps());
        double ax = Double.isNaN(s.anchorX()) ? f.width() / 2 : (s.anchorX() + 0.5) * s.scale();
        double ay = Double.isNaN(s.anchorY()) ? f.height() / 2 : (s.anchorY() + 0.5) * s.scale();
        sprite.setTranslateX(s.width() / 2 - ax);
        sprite.setTranslateY(s.height() / 2 - ay);
        return sprite;
    }

    private static void burst(Entity bolt, boolean big) {
        Point2D c = bolt.getCenter();
        Particles.of(Particles.VOID).count(big ? 26 : 6).angle(-90, 180).speed(60, big ? 420 : 200)
                .life(0.4).size(big ? 7 : 4).gravity(200).at(c);
        if (big) {
            at.htl.flowstate.Sfx.EXPLOSION.at(c, 0.7, 0.6);
            Entity player = Game.getPlayer();
            Point2D p = player.getCenter();
            if (p.distance(c) < 110 && !bolt.isColliding(player)) {
                player.getComponent(at.htl.flowstate.Components.Player.PlayerStatsComponent.class).takeDamage(10, bolt, true);
            }
        }
    }

    /** Straight or falling flight, pointing the way it flies. */
    private static final class BoltComponent extends Component {
        private Point2D velocity;
        private final double gravity;

        BoltComponent(Point2D velocity, double gravity) {
            this.velocity = velocity;
            this.gravity = gravity;
        }

        @Override
        public void onUpdate(double tpf) {
            velocity = velocity.add(0, gravity * tpf);
            entity.translate(velocity.multiply(tpf));
            entity.setRotation(Math.toDegrees(Math.atan2(velocity.getY(), velocity.getX())));
        }
    }

    /** Hurts the player once if they overlap the entity. */
    private static final class HitOnceComponent extends Component {
        private final HollowBehaviour owner;
        private final SkillMove move;
        private final double damage;
        private boolean done;

        HitOnceComponent(HollowBehaviour owner, SkillMove move, double damage) {
            this.owner = owner;
            this.move = move;
            this.damage = damage;
        }

        @Override
        public void onUpdate(double tpf) {
            if (done || !entity.isColliding(Game.getPlayer())) return;
            done = true;
            owner.strike(damage, move);
        }
    }

    /** Windup (pulled back), strike (the only phase that hits), follow-through; like MeleeSwingComponent. */
    private static final class SwingComponent extends Component {
        private static final double PULL_BACK = 28, OVERSHOOT = 14;
        private final HollowBehaviour owner;
        private final SkillMove move;
        private final double duration, from, to, sign;
        private double elapsed, angle;
        private boolean hit;

        SwingComponent(HollowBehaviour owner, SkillMove move, double duration, double from, double to) {
            this.owner = owner;
            this.move = move;
            this.duration = duration;
            this.from = from;
            this.to = to;
            this.sign = Math.signum(to - from);
            this.angle = from;
        }

        @Override
        public void onAdded() {
            entity.setRotationOrigin(new Point2D(entity.getWidth() / 2, 0));
            place();
        }

        @Override
        public void onUpdate(double tpf) {
            elapsed += tpf;
            double t = Math.min(elapsed / duration, 1);
            double strikeEnd = WINDUP + STRIKE;
            double strikeStart = from - sign * PULL_BACK;
            if (t < WINDUP) {
                double k = t / WINDUP;
                angle = from - sign * PULL_BACK * (1 - (1 - k) * (1 - k));
            } else if (t < strikeEnd) {
                double k = (t - WINDUP) / STRIKE;
                angle = strikeStart + (to + sign * OVERSHOOT - strikeStart) * (1 - Math.pow(1 - k, 3));
            } else {
                double k = (t - strikeEnd) / (1 - strikeEnd);
                angle = to + sign * OVERSHOOT * (1 - k * (2 - k));
            }
            place();
            if (!hit && t >= WINDUP && t < strikeEnd + 0.1 && entity.isColliding(Game.getPlayer())) {
                hit = true;
                owner.strike(owner.damageOf(move), move);
            }
            if (t >= WINDUP && t < strikeEnd && FXGLMath.randomDouble() < 0.5) {
                Particles.of(Particles.VOID).count(1).angle(angle, 60).speed(20, 90).life(0.3).size(4).gravity(0).at(tip());
            }
            entity.setOpacity(t < 0.7 ? 1 : Math.max(0, 1 - (t - 0.7) / 0.3));
            if (t >= 1) entity.removeFromWorld();
        }

        private Point2D tip() {
            Point2D p = owner.shoulder();
            double r = Math.toRadians(angle);
            return p.add(Math.cos(r) * entity.getHeight(), Math.sin(r) * entity.getHeight());
        }

        private void place() {
            Point2D p = owner.shoulder();
            entity.setRotation(angle - 90);
            entity.setPosition(p.getX() - entity.getWidth() / 2, p.getY());
        }
    }
}
