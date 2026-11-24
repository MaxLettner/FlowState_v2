package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Combat.Combat;
import at.htl.flowstate.Combat.DamageKind;
import at.htl.flowstate.Combat.Element;
import at.htl.flowstate.Combat.Hit;
import at.htl.flowstate.Components.Player.LoadoutComponent;
import at.htl.flowstate.Components.Player.PlayerStatsComponent;
import at.htl.flowstate.Game;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Skills.SkillManager;
import at.htl.flowstate.Skills.SkillType;
import at.htl.flowstate.Components.SpriteComponents.DemonSpriteComponent;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.core.math.FXGLMath;
import com.almasb.fxgl.dsl.components.HealthDoubleComponent;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import com.almasb.fxgl.physics.PhysicsComponent;
import javafx.geometry.Point2D;

import java.util.HashSet;
import java.util.Set;

/** Enemy health, death and status effects (concept_new/Mechanics/Status Effects.md). */
public class EnemyStatsComponent extends Component {
    private static final double BURN_DPS = 5;
    private static final double POISON_DPS_PER_STACK = 2;
    private static final int POISON_MAX_STACKS = 10;
    private static final double POISON_DURATION = 5;
    private static final double BLEED_DPS_PER_STACK = 3;
    private static final int BLEED_MAX_STACKS = 5;
    private static final double BLEED_DURATION = 4;
    private static final double SLOW_FACTOR = 0.6;
    private static final double ELEMENTAL_MASTERY = 1.25;
    private static final double KNOCKBACK_FRICTION = 600;
    private static final double BOUNCE_DAMAGE = 10;
    /** Stuns shorter than this (after the demon's stun resistance) don't register at all. */
    private static final double MIN_STUN = 0.15;

    private final Entity player;
    private final DemonType type;
    private PhysicsComponent physics;
    private DemonSpriteComponent sprite;
    private EliteComponent elite;
    private boolean invulnerable;
    private double ward;               // Hex Priest shield: soaks damage before the health
    private boolean summoned;          // came through a portal: no experience, or a Gatekeeper is a farm

    private double stunLeft, freezeLeft, slowLeft, burnLeft, pinLeft;
    private int poisonStacks, bleedStacks;
    private double poisonLeft, bleedLeft;
    private Point2D pinPosition;

    private double fizzleCooldown;
    private static final javafx.scene.paint.Color[] SMOKE = {
            javafx.scene.paint.Color.web("#8c8487"), javafx.scene.paint.Color.web("#5e5659"), javafx.scene.paint.Color.web("#b3abab")};

    private double knockbackVelocity = 0;
    private boolean launched = false;
    private final Set<Entity> bounced = new HashSet<>();

    private boolean dead = false;
    private double statusFxTimer = 0;
    private static final double STATUS_FX_INTERVAL = 0.12;

    public EnemyStatsComponent(DemonType type) {
        player = Game.getPlayer();
        this.type = type;
    }

    public DemonType getType() {
        return type;
    }

    @Override
    public void onUpdate(double tpf) {
        if (dead) return;
        resolveComponents();

        tickDamageOverTime(tpf);
        if (dead) return;

        if (knockbackVelocity == 0) stunLeft = Math.max(0, stunLeft - tpf);
        freezeLeft = Math.max(0, freezeLeft - tpf);
        fizzleCooldown = Math.max(0, fizzleCooldown - tpf);
        slowLeft = Math.max(0, slowLeft - tpf);

        if (pinLeft > 0) {
            pinLeft -= tpf;
            physics.setVelocityX(0);
            physics.setVelocityY(0);
            physics.overwritePosition(pinPosition);
        }

        calculateKnockback(tpf);
        updateStatusVisuals(tpf);
    }

    /** Not in onAdded: FXGL calls onAdded before the entity's later components (sprite) exist. */
    private void resolveComponents() {
        if (sprite != null) return;
        physics = entity.getComponent(PhysicsComponent.class);
        sprite = entity.getComponent(DemonSpriteComponent.class);
        elite = entity.getComponentOptional(EliteComponent.class).orElse(null);
    }

    public EliteAffix getAffix() {
        resolveComponents();
        return elite == null ? null : elite.getAffix();
    }

    //-----Damage & death-----
    public void damage(double amount, DamageKind kind) {
        if (dead || amount <= 0 || invulnerable) return;
        resolveComponents();
        if (ward > 0) {
            double soaked = Math.min(ward, amount);
            ward -= soaked;
            amount -= soaked;
            if (ward <= 0) {
                sprite.setWard(false);
                Sfx.SHIELD_BREAK.at(entity.getCenter(), 0.6, 1.2);
            } else {
                Sfx.SHIELD_HIT.at(entity.getCenter(), 0.6, 1.2);
            }
            if (amount <= 0) {
                if (kind != DamageKind.DOT) sprite.flash();
                return;
            }
        }
        if (freezeLeft > 0 && kind != DamageKind.DOT) { // shatter (fire demons shatter for more)
            amount *= type.nature == DemonNature.FIRE ? DemonNature.FIRE_SHATTER : DemonNature.SHATTER;
            freezeLeft = 0;
            Sfx.SHATTER.at(entity.getCenter());
        }
        HealthDoubleComponent hp = entity.getComponent(HealthDoubleComponent.class);
        hp.damage(amount);
        if (kind != DamageKind.DOT) sprite.flash();
        if (hp.isZero()) die();
    }

    private void die() {
        if (dead) return;
        dead = true;
        if (!type.isPart()) {
            if (!summoned) player.getComponent(PlayerStatsComponent.class).addExperience(type.xp * (getAffix() != null ? EliteComponent.XP_MULT : 1));
            player.getComponent(LoadoutComponent.class).onKill();
        }
        DemonBehaviour behaviour = DemonBehaviour.of(entity);
        if (behaviour != null && behaviour.diesSlowly()) {      // a boss plays its own death scene
            behaviour.onDeath();
            return;
        }
        Point2D c = entity.getCenter();
        double size = Math.min(2.2, Math.sqrt(type.width * type.height) / 60);   // 1 = player sized
        Sfx.ENEMY_DEATH.at(c, Math.min(1, 0.6 + size * 0.4), type.deathPitch);
        Particles.splat(c.add(0, type.height * 0.25), type.splat);
        // demons burn away: embers rising out of the body
        Particles.burst(c, Particles.EMBER, (int) (8 + 10 * size), -90, 70, 60, 220 * Math.max(1, size), 0.9, 5, -120);
        if (behaviour != null) behaviour.onDeath();
        entity.removeFromWorld();
    }

    /** Caught by a boss's blow or shockwave (the Pit Lord doesn't spare its own imps): damage and a shove. */
    public void struckByDemon(double amount, int direction, double knockback) {
        if (dead) return;
        damage(amount, DamageKind.OTHER);
        if (!dead) applyKnockback(knockback, direction, false);
    }

    public boolean isDead() {
        return dead;
    }

    public void markSummoned() { summoned = true; }

    /** Stone skin: no damage, no status effects, no knockback. */
    public void setInvulnerable(boolean on) { invulnerable = on; }
    public boolean isInvulnerable() { return invulnerable; }

    /** A shield soaking the next {@code amount} damage (the Hex Priest's ward). */
    public void ward(double amount) {
        resolveComponents();
        ward = Math.max(ward, amount);
        sprite.setWard(true);
    }
    public boolean hasWard() { return ward > 0; }

    public void heal(double amount) {
        HealthDoubleComponent hp = entity.getComponent(HealthDoubleComponent.class);
        hp.restore(amount);
    }

    private void tickDamageOverTime(double tpf) {
        double dot = 0;
        if (burnLeft > 0) {
            burnLeft -= tpf;
            dot += BURN_DPS * type.nature.multiplier(Element.FIRE) * tpf;
        }
        if (poisonStacks > 0) {
            poisonLeft -= tpf;
            dot += POISON_DPS_PER_STACK * poisonStacks * tpf;
            if (poisonLeft <= 0) poisonStacks = 0;
        }
        if (bleedStacks > 0) {
            bleedLeft -= tpf;
            dot += BLEED_DPS_PER_STACK * bleedStacks * tpf;
            if (bleedLeft <= 0) bleedStacks = 0;
        }
        if (dot > 0) damage(dot, DamageKind.DOT);
    }

    //-----Status effects-----
    private double mastery() {
        return SkillManager.get().owns(SkillType.ELEMENTAL) ? ELEMENTAL_MASTERY : 1.0;
    }

    public void stun(double duration) {
        duration *= type.stunMult;
        if (duration < MIN_STUN) return;
        if (!isStunned()) Sfx.STUN.at(entity.getCenter());
        stunLeft = Math.max(stunLeft, duration);
    }
    public void freeze(double duration) {
        if (getAffix() == EliteAffix.FROSTBOUND) return;
        freezeLeft = Math.max(freezeLeft, duration * mastery());
    }
    public void slow(double duration) {
        if (getAffix() == EliteAffix.FROSTBOUND) return;
        slowLeft = Math.max(slowLeft, duration * mastery());
    }
    public void burn(double duration) {
        if (getAffix() == EliteAffix.INFERNAL) return;
        if (type.nature.burnImmune()) {
            fizzle();
            return;
        }
        burnLeft = Math.max(burnLeft, duration * mastery());
    }
    public void clearSlow() { slowLeft = 0; }

    /** A fire demon shrugging off a burn: a puff of smoke (at most every half second). */
    private void fizzle() {
        if (fizzleCooldown > 0 || entity == null) return;
        fizzleCooldown = 0.5;
        Particles.burst(entity.getCenter().add(0, -10), SMOKE, 6, -90, 40, 30, 90, 0.7, 6, -60);
    }

    public void addPoison(int stacks) {
        poisonStacks = Math.min(POISON_MAX_STACKS, poisonStacks + stacks);
        poisonLeft = POISON_DURATION * mastery();
    }

    public void addBleed(int stacks) {
        bleedStacks = Math.min(BLEED_MAX_STACKS, bleedStacks + stacks);
        bleedLeft = BLEED_DURATION;
    }

    public void pin(double duration) {
        pinLeft = Math.max(pinLeft, duration);
        pinPosition = entity.getPosition();
        knockbackVelocity = 0;
        launched = false;
    }

    public boolean isStunned() { return stunLeft > 0 || freezeLeft > 0 || pinLeft > 0; }
    public boolean isFrozen() { return freezeLeft > 0; }
    public boolean isBurning() { return burnLeft > 0; }
    public boolean isSlowed() { return slowLeft > 0; }
    public double getSpeedMult() {
        return (isSlowed() ? SLOW_FACTOR : 1.0) * (getAffix() == EliteAffix.SWIFT ? EliteComponent.SWIFT_MULT : 1.0);
    }
    public int getPoisonStacks() { return poisonStacks; }

    /** Damage per second its burn, poison and bleed deal right now (the Tar Hydra's heads share it). */
    public double getDotPerSecond() {
        double dps = 0;
        if (burnLeft > 0) dps += BURN_DPS * type.nature.multiplier(Element.FIRE);
        if (poisonStacks > 0) dps += POISON_DPS_PER_STACK * poisonStacks;
        if (bleedStacks > 0) dps += BLEED_DPS_PER_STACK * bleedStacks;
        return dps;
    }

    //-----Knockback-----
    public boolean isKnockedBack() {
        return knockbackVelocity != 0;
    }

    public void applyKnockback(double strength, int direction, boolean launch) {
        if (direction != 1 && direction != -1) return;
        if (pinLeft > 0) return;
        resolveComponents();
        strength *= type.knockbackMult * (type.nature == DemonNature.VOID ? DemonNature.VOID_KNOCKBACK : 1);
        if (strength < 1) return;
        knockbackVelocity = strength * direction;
        launched = launch;
        bounced.clear();
        physics.setVelocityY(-strength * 0.3);
    }

    private void calculateKnockback(double tpf) {
        if (knockbackVelocity == 0) return;
        physics.setVelocityX(knockbackVelocity);
        if (launched) checkBounce();
        if (dead) return;

        if (knockbackVelocity > 0) {
            knockbackVelocity = Math.max(0, knockbackVelocity - KNOCKBACK_FRICTION * tpf);
        } else {
            knockbackVelocity = Math.min(0, knockbackVelocity + KNOCKBACK_FRICTION * tpf);
        }
        if (knockbackVelocity == 0) launched = false;
    }

    /** Spring Hammer: a launched enemy damages every enemy it crashes into (and itself). */
    private void checkBounce() {
        for (Entity other : Combat.enemies()) {
            if (other == entity || bounced.contains(other) || !entity.isColliding(other)) continue;
            bounced.add(other);
            Combat.hit(other, Hit.of(BOUNCE_DAMAGE, DamageKind.OTHER)
                    .knockback(Math.abs(knockbackVelocity) * 0.5, knockbackVelocity > 0 ? 1 : -1));
            damage(BOUNCE_DAMAGE, DamageKind.OTHER);
            if (dead) return;
        }
    }

    private void updateStatusVisuals(double tpf) {
        sprite.setFrozen(isFrozen());
        sprite.setStunned((stunLeft > 0 || pinLeft > 0) && !isFrozen());

        statusFxTimer -= tpf;
        if (statusFxTimer > 0) return;
        statusFxTimer = STATUS_FX_INTERVAL;
        Point2D c = entity.getCenter();
        if (burnLeft > 0) {
            Particles.burst(c.add(FXGLMath.random(-16, 16), FXGLMath.random(-4, 30)), Particles.EMBER, 1, -90, 15, 40, 90, 0.6, 4, -80);
        }
        if (poisonStacks > 0) {
            Particles.burst(c.add(FXGLMath.random(-16, 16), FXGLMath.random(0, 30)), Particles.POISON, 1, -90, 10, 20, 50, 0.7, 4, -40);
        }
        if (bleedStacks > 0) {
            Particles.burst(c.add(FXGLMath.random(-14, 14), FXGLMath.random(0, 20)), Particles.BLOOD, 1, 90, 10, 10, 30, 0.5, 4, 500);
        }
    }
}
