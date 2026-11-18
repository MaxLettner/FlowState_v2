package at.htl.flowstate.Components.Player;

import at.htl.flowstate.Components.SpriteComponents.SpriteComponent;
import at.htl.flowstate.Skills.Branch;
import at.htl.flowstate.Skills.SkillManager;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;

import java.util.ArrayList;
import java.util.List;

public class PlayerStatsComponent extends Component {
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

    private double health = 100;
    private double maxHealth = 100;
    private double mana = BASE_MAX_MANA;
    private double experience = 0;
    private double maxExperience = 3;
    private double skillPoints = 0;
    private double invincibleLeft = 0;

    private final List<Runnable> skillPointListeners = new ArrayList<>();

    @Override
    public void onUpdate(double tpf) {
        LoadoutComponent loadout = entity.getComponent(LoadoutComponent.class);
        double maxMana = getMaxMana();

        if (!loadout.isManaRegenPaused() && mana < maxMana) {
            double rate = MANA_REGEN + MANA_REGEN_PER_RANK * rank(Branch.MAGIC);
            if (loadout.isArcaneFlowActive()) rate *= ARCANE_FLOW_MULT;
            mana = Math.min(maxMana, mana + maxMana * rate * tpf);
        }
        if (mana > maxMana) mana = maxMana;

        if (health < maxHealth) health = Math.min(maxHealth, health + maxHealth * HEAL_RATE * tpf);
        if (invincibleLeft > 0) invincibleLeft -= tpf;

        SpriteComponent sprite = entity.getComponent(SpriteComponent.class);
        sprite.setBlinking(invincibleLeft > 0);
        sprite.setShield(loadout.isShieldActive());
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

        if (loadout.isBlocking() && isInFront(source)) {
            if (projectile) return false; // destroyed by the fists
            amount *= 1 - BLOCK_REDUCTION;
        }
        if (invincibleLeft > 0) return false;
        invincibleLeft = INVINCIBILITY_TIME;

        if (loadout.isShieldActive()) {
            double fromMana = amount * SHIELD_ABSORB;
            if (mana >= fromMana) {
                mana -= fromMana;
                amount -= fromMana;
            } else {
                amount -= mana;
                mana = 0;
                loadout.breakShield();
            }
        }
        health -= amount;
        entity.getComponent(SpriteComponent.class).flash();
        return true;
    }

    private boolean isInFront(Entity source) {
        if (source == null) return false;
        int side = source.getCenter().getX() >= entity.getCenter().getX() ? 1 : -1;
        return side == entity.getComponent(PlayerMovementComponent.class).getCurrentWatchDirection();
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
        experience++;
        if (experience >= maxExperience) {
            skillPoints++;
            maxExperience++;
            experience = 0;
            fireSkillPointsChanged();
        }
    }

    public void addSkillPoint() {
        skillPoints++;
        fireSkillPointsChanged();
    }

    public boolean takeSkillPoints(double s) {
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
    public double getSkillPoints() { return skillPoints; }
}
