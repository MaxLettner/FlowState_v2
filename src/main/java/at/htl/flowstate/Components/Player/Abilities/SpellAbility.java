package at.htl.flowstate.Components.Player.Abilities;

import at.htl.flowstate.Assets;
import at.htl.flowstate.Combat.Combat;
import at.htl.flowstate.Combat.DamageKind;
import at.htl.flowstate.Combat.Hit;
import at.htl.flowstate.Components.Player.Attacks.PlayerProjectileComponent;
import at.htl.flowstate.Components.Player.LoadoutComponent;
import at.htl.flowstate.Skills.Branch;
import at.htl.flowstate.Skills.SkillType;
import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.texture.Texture;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

/** Simple projectile spells: Arcane Bolt, Magic Missile, Spark, Icicle, Poison Darts. */
public class SpellAbility extends Ability {
    public enum Spell {
        // sprite strip, frame count, fps, anchor (native px on the hitbox centre, NaN = frame centre)
        ARCANE_BOLT("ArcaneBolt.png", 4, 12, Double.NaN, Double.NaN, 12, 12, 12, 10, 0.4, 900, 1),
        MAGIC_MISSILE("MagicMissile.png", 3, 12, 13, 5, 20, 20, 10, 8, 0.3, 600, 1),
        SPARK("Spark.png", 4, 16, Double.NaN, Double.NaN, 10, 10, 12, 10, 0.35, 1200, 1),
        ICICLE("Icicle.png", 1, 0, Double.NaN, Double.NaN, 50, 15, 20, 15, 0.5, 1000, 1),
        POISON_DARTS("PoisonDart.png", 1, 0, Double.NaN, Double.NaN, 30, 15, 4, 12, 0.5, 800, 3);

        final String texture;
        final int frames;
        final double fps, anchorX, anchorY, width, height;
        final double damage, mana, interval, speed;
        final int count;

        Spell(String texture, int frames, double fps, double anchorX, double anchorY, double width, double height,
              double damage, double mana, double interval, double speed, int count) {
            this.texture = texture;
            this.frames = frames;
            this.fps = fps;
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.width = width;
            this.height = height;
            this.damage = damage;
            this.mana = mana;
            this.interval = interval;
            this.speed = speed;
            this.count = count;
        }
    }

    private static final double SPARK_BURN = 1.0;
    private static final double ICICLE_SLOW = 2.0;
    private static final double DART_SPREAD = 5;
    private static final double MISSILE_TARGET_RADIUS = 250;
    private static final double MISSILE_TURN_RATE = 360;
    private static final double MISSILE_HOMING_AFTER = 120;

    private final Spell spell;

    public SpellAbility(Entity player, LoadoutComponent loadout, Spell spell) {
        super(player, loadout, Branch.MAGIC);
        this.spell = spell;
    }

    @Override
    public void primaryPress() {
        if (!ready()) return;
        if (!stats().takeMana(spell.mana)) return;
        loadout.markCast();
        sprite().playCast();
        cooldown(spell.interval);

        Point2D dir = aim();
        if (spell.count == 1) {
            fire(dir);
        } else {
            fire(dir);
            fire(rotate(dir, DART_SPREAD));
            fire(rotate(dir, -DART_SPREAD));
        }
    }

    private void fire(Point2D direction) {
        PlayerProjectileComponent projectile = new PlayerProjectileComponent(
                direction.multiply(spell.speed), pierce(0), (e, i) -> createHit());
        if (spell == Spell.MAGIC_MISSILE) {
            Entity target = Combat.nearestEnemy(FXGL.getInput().getMousePositionWorld(), MISSILE_TARGET_RADIUS);
            if (target != null) projectile.homing(target, MISSILE_TURN_RATE, MISSILE_HOMING_AFTER);
        }
        spawnProjectile(view(), spell.width, spell.height, projectile);
    }

    private Hit createHit() {
        Hit hit = Hit.of(spell.damage, DamageKind.SPELL);
        return switch (spell) {
            case SPARK -> hit.family(SkillType.ELEMENTAL).burn(SPARK_BURN);
            case ICICLE -> hit.family(SkillType.ICICLE).slow(ICICLE_SLOW).shatterSlowed();
            case POISON_DARTS -> hit.family(SkillType.POISON_DARTS).poison(1);
            case MAGIC_MISSILE -> hit.family(SkillType.MAGIC_MISSILE);
            case ARCANE_BOLT -> hit.family(SkillType.ARCANE);
        };
    }

    private Node view() {
        return projectileSprite(spell.texture, spell.frames, spell.fps, 2, spell.width, spell.height,
                spell.anchorX, spell.anchorY);
    }
}
