package at.htl.flowstate.Components.Player;

import at.htl.flowstate.Combat.Element;
import at.htl.flowstate.Components.Player.Abilities.Ability;
import at.htl.flowstate.Components.Player.Abilities.AbilityFactory;
import at.htl.flowstate.Skills.Branch;
import at.htl.flowstate.Skills.SkillManager;
import at.htl.flowstate.Skills.SkillType;
import at.htl.flowstate.Skills.SlotKind;
import com.almasb.fxgl.entity.component.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Routes hotbar keys and mouse buttons to abilities, and owns the state of
 * toggles (Mana Shield, enchantments), Levitation, cooldowns and the combo meter.
 * See concept_new/Mechanics/Loadout.md.
 */
public class LoadoutComponent extends Component {
    public static final double SHARPEN_MULT = 1.15;

    private static final double SHIELD_DRAIN = 1.0;
    private static final double SHIELD_BREAK_COOLDOWN = 3.0;
    private static final double LEVITATION_COST = 25;
    private static final double LEVITATION_TIME = 3;
    private static final double LEVITATION_COOLDOWN = 10;
    private static final double SLOW_FALL_TIME = 1;
    private static final double COMBO_WINDOW = 1.5;
    private static final double COMBO_STEP = 0.02;
    private static final double COMBO_MAX = 0.20;
    private static final double ARCANE_FLOW_DELAY = 2.0;
    private static final double EFFICIENT_ENCHANTING = 0.75;
    private static final double LIFE_STEAL_HEAL = 0.08;
    private static final int PIERCE_BONUS = 2;
    private static final int RECALL_PIERCE_BONUS = 1;

    private final SkillManager skills = SkillManager.get();
    private final Map<SkillType, Ability> abilities = new EnumMap<>(SkillType.class);
    private final EnumMap<Branch, Double> cooldowns = new EnumMap<>(Branch.class);
    private final EnumMap<Branch, Double> cooldownTotals = new EnumMap<>(Branch.class);

    private int activeSlot = 0;
    private SkillType activeAttack = null;

    private boolean shieldActive = false;
    private double shieldBreakCooldown = 0;
    private SkillType activeEnchant = null;
    private Element imbueElement = null;

    private double levitationLeft = 0;
    private double slowFallLeft = 0;
    private double levitationCooldown = 0;

    private double comboBonus = 0;
    private double comboTimer = 0;
    private double timeSinceCast = 99;

    @Override
    public void onAdded() {
        for (Branch b : Branch.values()) {
            cooldowns.put(b, 0.0);
            cooldownTotals.put(b, 0.0);
        }
    }

    @Override
    public void onUpdate(double tpf) {
        cooldowns.replaceAll((b, v) -> Math.max(0, v - tpf));
        timeSinceCast += tpf;
        syncActiveAttack();

        PlayerStatsComponent stats = entity.getComponent(PlayerStatsComponent.class);
        if (shieldActive && !stats.takeMana(SHIELD_DRAIN * tpf)) breakShield();
        if (shieldBreakCooldown > 0) shieldBreakCooldown -= tpf;
        if (activeEnchant != null && !stats.takeMana(enchantDrain(activeEnchant) * EFFICIENT_ENCHANTING * tpf)) {
            activeEnchant = null;
        }

        if (levitationLeft > 0) {
            levitationLeft -= tpf;
            if (levitationLeft <= 0) slowFallLeft = SLOW_FALL_TIME;
        } else if (slowFallLeft > 0) {
            slowFallLeft -= tpf;
            if (slowFallLeft <= 0) levitationCooldown = LEVITATION_COOLDOWN;
        } else if (levitationCooldown > 0) {
            levitationCooldown -= tpf;
        }

        if (comboTimer > 0) {
            comboTimer -= tpf;
            if (comboTimer <= 0) comboBonus = 0;
        }

        for (Ability a : new ArrayList<>(abilities.values())) a.update(tpf);
    }

    //-----Hotbar & input-----
    public void pressSlot(int slot) {
        SkillType type = skills.getSlot(slot);
        if (type == null) return;
        if (type.getSlotKind() == SlotKind.ATTACK) {
            activeSlot = slot;
            syncActiveAttack();
        } else {
            activate(type);
        }
    }

    public void primaryPress() { Ability a = activeAbility(); if (a != null) a.primaryPress(); }
    public void primaryRelease() { Ability a = activeAbility(); if (a != null) a.primaryRelease(); }
    public void secondaryPress() { Ability a = activeAbility(); if (a != null) a.secondaryPress(); }
    public void secondaryRelease() { Ability a = activeAbility(); if (a != null) a.secondaryRelease(); }

    private void syncActiveAttack() {
        SkillType slotType = skills.getSlot(activeSlot);
        SkillType current = slotType != null && slotType.getSlotKind() == SlotKind.ATTACK ? slotType : null;
        if (current != activeAttack) {
            if (activeAttack != null && abilities.containsKey(activeAttack)) abilities.get(activeAttack).onDeselect();
            activeAttack = current;
        }
    }

    private Ability activeAbility() {
        syncActiveAttack();
        return activeAttack == null ? null : ability(activeAttack);
    }

    private Ability ability(SkillType type) {
        Ability a = abilities.get(type);
        if (a == null) {
            a = AbilityFactory.create(type, entity, this);
            if (a != null) abilities.put(type, a);
        }
        return a;
    }

    //-----Toggles & instants-----
    private void activate(SkillType type) {
        if (type == SkillType.MANA_SHIELD) toggleShield();
        else if (type == SkillType.LEVITATION) startLevitation();
        else if (type.isEnchantment()) toggleEnchant(type);
    }

    private void toggleShield() {
        if (shieldActive) shieldActive = false;
        else if (shieldBreakCooldown <= 0) shieldActive = true;
    }

    public void breakShield() {
        shieldActive = false;
        shieldBreakCooldown = SHIELD_BREAK_COOLDOWN;
    }

    private void startLevitation() {
        if (levitationLeft > 0 || slowFallLeft > 0 || levitationCooldown > 0) return;
        if (!entity.getComponent(PlayerStatsComponent.class).takeMana(LEVITATION_COST)) return;
        levitationLeft = LEVITATION_TIME;
        markCast();
    }

    private void toggleEnchant(SkillType type) {
        if (type != SkillType.IMBUE) {
            activeEnchant = activeEnchant == type ? null : type;
            return;
        }
        List<Element> elements = ownedElements();
        if (elements.isEmpty()) return;
        if (activeEnchant != SkillType.IMBUE) {
            activeEnchant = SkillType.IMBUE;
            imbueElement = elements.getFirst();
            return;
        }
        // already active: cycle Fire -> Ice -> Poison -> off
        int next = elements.indexOf(imbueElement) + 1;
        if (next >= elements.size()) {
            activeEnchant = null;
        } else {
            imbueElement = elements.get(next);
        }
    }

    public List<Element> ownedElements() {
        List<Element> list = new ArrayList<>();
        if (skills.owns(SkillType.FIRE_BALL)) list.add(Element.FIRE);
        if (skills.owns(SkillType.ICICLE)) list.add(Element.ICE);
        if (skills.owns(SkillType.POISON_DARTS)) list.add(Element.POISON);
        return list;
    }

    private double enchantDrain(SkillType enchant) {
        return switch (enchant) {
            case ENCHANTING -> 1.5;
            case IMBUE -> 2.5;
            default -> 2.0; // Life Steal, Piercing
        };
    }

    //-----Cooldowns-----
    public boolean isReady(Branch branch) {
        return cooldowns.get(branch) <= 0;
    }

    public double getCooldown(Branch branch) {
        return cooldowns.get(branch);
    }

    public void setCooldown(Branch branch, double seconds) {
        cooldowns.put(branch, seconds);
        cooldownTotals.put(branch, seconds);
    }

    /**
     * How much of a hotbar skill's cooldown is still left: 1 right after use, 0 when ready.
     * Weapons and spells share one cooldown per branch, so every attack of that branch reports it.
     */
    public double getCooldownProgress(SkillType type) {
        if (type == SkillType.MANA_SHIELD) return shieldBreakCooldown > 0 ? shieldBreakCooldown / SHIELD_BREAK_COOLDOWN : 0;
        if (type == SkillType.LEVITATION) return levitationCooldown > 0 ? levitationCooldown / LEVITATION_COOLDOWN : 0;
        if (type.getSlotKind() != SlotKind.ATTACK || type.getBranch() == null) return 0;
        Ability a = abilities.get(type);
        if (a != null && a.getSlotCooldown() >= 0) return a.getSlotCooldown();
        double left = cooldowns.get(type.getBranch());
        double total = cooldownTotals.get(type.getBranch());
        return left > 0 && total > 0 ? Math.min(1, left / total) : 0;
    }

    /** Full length in seconds of the cooldown {@link #getCooldownProgress} is reporting. */
    public double getCooldownTotal(SkillType type) {
        if (type == SkillType.MANA_SHIELD) return SHIELD_BREAK_COOLDOWN;
        if (type == SkillType.LEVITATION) return LEVITATION_COOLDOWN;
        if (type.getSlotKind() != SlotKind.ATTACK || type.getBranch() == null) return 0;
        Ability a = abilities.get(type);
        if (a != null && a.getSlotCooldown() >= 0) return a.getSlotCooldownTotal();
        return cooldownTotals.get(type.getBranch());
    }

    public void markCast() {
        timeSinceCast = 0;
    }

    //-----Combo meter (Fisticuffs)-----
    public void registerComboHit() {
        comboBonus = Math.min(COMBO_MAX, comboBonus + COMBO_STEP);
        comboTimer = COMBO_WINDOW;
    }

    public double getComboBonus() { return comboBonus; }
    public double getComboProgress() { return comboBonus / COMBO_MAX; }

    //-----Events-----
    public void onKill() {
        if (activeEnchant == SkillType.LIFE_STEAL) {
            PlayerStatsComponent stats = entity.getComponent(PlayerStatsComponent.class);
            stats.heal(stats.getMaxHealth() * LIFE_STEAL_HEAL);
        }
    }

    //-----State queries-----
    public int getPierceBonus(boolean recallTrident) {
        if (activeEnchant != SkillType.PIERCING) return 0;
        return recallTrident ? RECALL_PIERCE_BONUS : PIERCE_BONUS;
    }

    public boolean isLevitating() { return levitationLeft > 0; }
    public boolean isSlowFalling() { return slowFallLeft > 0; }
    public boolean isShieldActive() { return shieldActive; }
    public SkillType getActiveEnchant() { return activeEnchant; }
    public Element getImbueElement() { return imbueElement; }
    public int getActiveSlot() { return activeSlot; }

    public boolean isManaRegenPaused() {
        return shieldActive || activeEnchant != null;
    }

    public boolean isArcaneFlowActive() {
        return skills.owns(SkillType.ARCANE) && timeSinceCast >= ARCANE_FLOW_DELAY;
    }

    public boolean isBlocking() {
        Ability a = activeAttack == null ? null : abilities.get(activeAttack);
        return a != null && a.isBlocking();
    }

    public double getMoveSpeedMult() {
        Ability a = activeAttack == null ? null : abilities.get(activeAttack);
        return a == null ? 1.0 : a.getMoveSpeedMult();
    }

    /** 0..1 while an attack is charging, -1 otherwise. */
    public double getChargeProgress() {
        Ability a = activeAttack == null ? null : abilities.get(activeAttack);
        return a == null ? -1 : a.getChargeProgress();
    }

    public boolean isToggleOn(SkillType type) {
        if (type == SkillType.MANA_SHIELD) return shieldActive;
        if (type == SkillType.LEVITATION) return levitationLeft > 0;
        return type.isEnchantment() && activeEnchant == type;
    }

    /** Short status line shown under the skill name in the hotbar. */
    public String getSlotStatus(SkillType type) {
        if (type == SkillType.MANA_SHIELD) {
            if (shieldActive) return "ON";
            return shieldBreakCooldown > 0 ? "broken " + seconds(shieldBreakCooldown) : "";
        }
        if (type == SkillType.LEVITATION) {
            if (levitationLeft > 0) return "flying " + seconds(levitationLeft);
            if (slowFallLeft > 0) return "gliding";
            return levitationCooldown > 0 ? seconds(levitationCooldown) : "";
        }
        if (type.isEnchantment()) {
            if (activeEnchant == type) return type == SkillType.IMBUE ? imbueElement.getDisplayName() : "ON";
            return type == SkillType.IMBUE && ownedElements().isEmpty() ? "needs Elemental skill" : "";
        }
        Ability a = abilities.get(type);
        return a == null ? "" : a.getStatusText();
    }

    private static String seconds(double s) {
        return String.format("%.1fs", Math.max(0, s));
    }
}
