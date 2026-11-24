package at.htl.flowstate.Components.Player;

import javafx.scene.paint.Color;
import javafx.geometry.Point2D;
import com.almasb.fxgl.core.math.FXGLMath;
import at.htl.flowstate.Visuals.Particles;
import at.htl.flowstate.Components.SpriteComponents.SpriteComponent;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Skills.Branch;
import at.htl.flowstate.Skills.SkillManager;
import com.almasb.fxgl.entity.Entity;
import at.htl.flowstate.Visuals.Impact;
import com.almasb.fxgl.entity.component.Component;

import java.util.ArrayList;
import java.util.List;

public class PlayerStatsComponent extends Component {
    private static final double HURT_HITSTOP = 0.06;
    private static final double HURT_SHAKE = 7;
    private static final double BASE_MAX_MANA = 100;
    private static final double MANA_PER_RANK = 10;
    private static final double MANA_REGEN = 0.05;           // of max mana per second
    private static final double MANA_REGEN_PER_RANK = 0.005;
    private static final double ARCANE_FLOW_MULT = 1.5;
    private static final double HEAL_RATE = 0.01;            // of max health per second
    private static final double DAMAGE_PER_RANK = 0.05;      // Strength / Dexterity
    private static final double INVINCIBILITY_TIME = 1.0;
    private static final double BLOCK_REDUCTION = 0.6;
    private static final double SHIELD_ABSORB = 0.7;
    /** Testing: run with FLOWSTATE_INFINITE_SP=1 to get unlimited skill points. */
    private static final boolean INFINITE_SKILL_POINTS = "1".equals(System.getenv("FLOWSTATE_INFINITE_SP"));
    private static final double INFINITE_SKILL_POINTS_SHOWN = 999;

    private double health = 100;
    private double maxHealth = 100;
    private double mana = BASE_MAX_MANA;
    private double experience = 0;
    private double maxExperience = 3;
    private double skillPoints = 0;
    private double invincibleLeft = 0;

    // debuffs from demons: elite Infernal burn, elite Frostbound chill, Hex Priest curse
    private static final double BURN_DPS = 4;
    private static final double POISON_DPS = 3;
    private static final double CHILL_MOVE = 0.6;
    private static final double CURSE_JUMP = 0.72;
    private static final double DEBUFF_FX_INTERVAL = 0.1;
    private static final Color[] CURSE_MOTES = {Color.web("#f6d8ff"), Color.web("#d08cff"), Color.web("#a050f0")};
    private double burnLeft, chillLeft, curseLeft, poisonLeft, debuffFx;

    // low health: a heartbeat that speeds up, and the background gets muffled
    private static final double LOW_HEALTH = 0.35;                   // of max health
    private static final double BEAT_SLOW = 1.15, BEAT_FAST = 0.55;  // s between beats at LOW_HEALTH and near 0
    private static final double MAX_MUFFLE = 0.8;
    private double beatLeft;

    private final List<Runnable> skillPointListeners = new ArrayList<>();

    @Override
    public void onUpdate(double tpf) {
        LoadoutComponent loadout = entity.getComponent(LoadoutComponent.class);
        double maxMana = getMaxMana();

        tickDebuffs(tpf);
        if (!loadout.isManaRegenPaused() && curseLeft <= 0 && mana < maxMana) {
            double rate = MANA_REGEN + MANA_REGEN_PER_RANK * rank(Branch.MAGIC);
            if (loadout.isArcaneFlowActive()) rate *= ARCANE_FLOW_MULT;
            mana = Math.min(maxMana, mana + maxMana * rate * tpf);
        }
        if (mana > maxMana) mana = maxMana;

        if (health < maxHealth) health = Math.min(maxHealth, health + maxHealth * HEAL_RATE * tpf);
        if (invincibleLeft > 0) invincibleLeft -= tpf;
        tickLowHealth(tpf);

        SpriteComponent sprite = entity.getComponent(SpriteComponent.class);
        sprite.setBlinking(invincibleLeft > 0);
        sprite.setShield(loadout.isShieldActive());
    }

    private void tickLowHealth(double tpf) {
        if (isDead()) {
            Sfx.muffle(1);
            return;
        }
        double danger = Math.max(0, 1 - health / (maxHealth * LOW_HEALTH));     // 0 fine .. 1 nearly dead
        Sfx.muffle(danger * MAX_MUFFLE);
        if (danger <= 0) {
            beatLeft = 0;
            return;
        }
        beatLeft -= tpf;
        if (beatLeft > 0) return;
        beatLeft = BEAT_SLOW + (BEAT_FAST - BEAT_SLOW) * danger;
        Sfx.HEARTBEAT.play(0.5 + 0.5 * danger, 1 + 0.15 * danger);
    }

    private int rank(Branch branch) {
        return SkillManager.get().getRank(branch);
    }

    //-----Health-----
    /**
     * Applies damage from an enemy (contact or projectile), respecting dodge
     * invulnerability, Metal Gloves block and Mana Shield.
     * @return true if any damage was taken
     */
    public boolean takeDamage(double amount, Entity source, boolean projectile) {
        LoadoutComponent loadout = entity.getComponent(LoadoutComponent.class);

        boolean blocked = loadout.isBlocking() && isInFront(source);
        if (blocked) {
            if (projectile) { // destroyed by the fists
                Sfx.BLOCK_HIT.play();
                return false;
            }
            amount *= 1 - BLOCK_REDUCTION;
        }
        if (invincibleLeft > 0) return false;
        invincibleLeft = INVINCIBILITY_TIME;
        if (blocked) Sfx.BLOCK_HIT.play();

        if (loadout.isShieldActive()) {
            double fromMana = amount * SHIELD_ABSORB;
            if (mana >= fromMana) {
                mana -= fromMana;
                amount -= fromMana;
                Sfx.SHIELD_HIT.play();
            } else {
                amount -= mana;
                mana = 0;
                loadout.breakShield();
            }
        }
        health -= amount;
        SpriteComponent sprite = entity.getComponent(SpriteComponent.class);
        sprite.flash();
        sprite.playHurt();
        Sfx.PLAYER_HURT.play(blocked ? 0.6 : 1, 1);
        Impact.hitStop(HURT_HITSTOP);
        Impact.shake(HURT_SHAKE);
        return true;
    }

    private boolean isInFront(Entity source) {
        if (source == null) return false;
        int side = source.getCenter().getX() >= entity.getCenter().getX() ? 1 : -1;
        return side == entity.getComponent(PlayerMovementComponent.class).getCurrentWatchDirection();
    }

    //-----Debuffs-----
    public void burn(double seconds) { burnLeft = Math.max(burnLeft, seconds); }
    /** Poison (the Hollow Ember's copies of the poison skills): a slower, longer burn. */
    public void poison(double seconds) { poisonLeft = Math.max(poisonLeft, seconds); }
    public void chill(double seconds) { chillLeft = Math.max(chillLeft, seconds); }
    /** Hex Priest: jumps are lower and mana doesn't regenerate. */
    public void curse(double seconds) {
        if (curseLeft <= 0) Sfx.CURSE.play();
        curseLeft = Math.max(curseLeft, seconds);
    }
    public boolean isCursed() { return curseLeft > 0; }
    public double getMoveMult() { return chillLeft > 0 ? CHILL_MOVE : 1; }
    public double getJumpMult() { return curseLeft > 0 ? CURSE_JUMP : 1; }

    private void tickDebuffs(double tpf) {
        if (burnLeft > 0) {
            burnLeft -= tpf;
            health -= BURN_DPS * tpf;           // burning ignores i-frames, like the enemies' Burn
        }
        if (poisonLeft > 0) {
            poisonLeft -= tpf;
            health -= POISON_DPS * tpf;
        }
        chillLeft = Math.max(0, chillLeft - tpf);
        curseLeft = Math.max(0, curseLeft - tpf);
        debuffFx -= tpf;
        if (debuffFx > 0) return;
        debuffFx = DEBUFF_FX_INTERVAL;
        Point2D c = entity.getCenter();
        if (burnLeft > 0) Particles.burst(c.add(FXGLMath.random(-14, 14), FXGLMath.random(-4, 30)), Particles.EMBER, 1, -90, 15, 40, 90, 0.6, 4, -80);
        if (poisonLeft > 0) Particles.burst(c.add(FXGLMath.random(-14, 14), FXGLMath.random(0, 30)), Particles.POISON, 1, -90, 10, 20, 50, 0.7, 4, -40);
        if (chillLeft > 0) Particles.burst(c.add(FXGLMath.random(-16, 16), FXGLMath.random(-20, 30)), Particles.ICE, 1, 90, 30, 10, 40, 0.6, 4, 60);
        if (curseLeft > 0) Particles.burst(c.add(FXGLMath.random(-18, 18), FXGLMath.random(-30, 20)), CURSE_MOTES, 1, -90, 40, 20, 50, 0.7, 4, -30);
    }

    public void grantInvulnerability(double seconds) {
        invincibleLeft = Math.max(invincibleLeft, seconds);
    }

    public void heal(double amount) {
        health = Math.min(maxHealth, health + amount);
    }

    public boolean isDead() {
        return health <= 0;
    }

    //-----Mana-----
    public boolean takeMana(double m) {
        if (mana - m < 0) return false;
        mana -= m;
        return true;
    }

    //-----Multipliers-----
    public double getMeleeMultiplier() {
        return 1 + DAMAGE_PER_RANK * rank(Branch.MELEE);
    }

    public double getRangedMultiplier() {
        return 1 + DAMAGE_PER_RANK * rank(Branch.RANGED);
    }

    //-----Experience & skill points-----
    public void addExperience() {
        addExperience(1);
    }

    /** Kills give experience by how tough the demon was; leftover experience carries into the next level. */
    public void addExperience(double amount) {
        experience += amount;
        if (experience >= maxExperience - 1e-9) {
            experience = Math.max(0, experience - maxExperience);
            skillPoints++;
            maxExperience++;
            Sfx.LEVEL_UP.play();
            fireSkillPointsChanged();
        }
    }

    public void addSkillPoint() {
        skillPoints++;
        fireSkillPointsChanged();
    }

    public boolean takeSkillPoints(double s) {
        if (INFINITE_SKILL_POINTS) return true;
        if (skillPoints - s < 0) return false;
        skillPoints -= s;
        fireSkillPointsChanged();
        return true;
    }

    public void addSkillPointsListener(Runnable listener) {
        skillPointListeners.add(listener);
    }

    private void fireSkillPointsChanged() {
        for (Runnable r : new ArrayList<>(skillPointListeners)) r.run();
    }

    //-----Getters-----
    public double getMana() { return mana; }
    public double getMaxMana() { return BASE_MAX_MANA + MANA_PER_RANK * rank(Branch.MAGIC); }
    public double getHealth() { return health; }
    public double getMaxHealth() { return maxHealth; }
    public double getExperience() { return experience; }
    public double getMaxExperience() { return maxExperience; }
    public double getSkillPoints() { return INFINITE_SKILL_POINTS ? INFINITE_SKILL_POINTS_SHOWN : skillPoints; }
}
