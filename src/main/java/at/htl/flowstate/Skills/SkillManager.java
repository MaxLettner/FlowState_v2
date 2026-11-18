package at.htl.flowstate.Skills;

import at.htl.flowstate.Components.Player.PlayerStatsComponent;
import at.htl.flowstate.Game;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;

/**
 * Owns the progression state: stat ranks, unlocked nodes and the hotbar.
 * Rules: concept_new/Mechanics/Progression.md and Loadout.md.
 */
public final class SkillManager {
    public static final int HOTBAR_SIZE = 4;
    public static final int MAX_RANK = 10;

    private static final int STAT_COST = 1;
    private static final int SUBTREE_COST = 2;
    private static final int SUBTREE_REQUIRED_RANK = 1;
    private static final int SKILL_BASE_COST = 3;         // 3 / 4 / 5 SP
    private static final int SKILL_BASE_REQUIRED_RANK = 3; // rank 3 / 5 / 7

    private static SkillManager instance;

    private final EnumMap<Branch, Integer> ranks = new EnumMap<>(Branch.class);
    private final EnumSet<SkillType> owned = EnumSet.of(SkillType.RUSTY_SWORD);
    private final SkillType[] hotbar = new SkillType[HOTBAR_SIZE];
    private final List<Runnable> listeners = new ArrayList<>();

    private SkillManager() {
        for (Branch b : Branch.values()) ranks.put(b, 0);
        hotbar[0] = SkillType.RUSTY_SWORD;
    }

    public static SkillManager get() {
        if (instance == null) instance = new SkillManager();
        return instance;
    }

    //-----Queries-----
    public static SkillType statOf(Branch branch) {
        return switch (branch) {
            case MELEE -> SkillType.STRENGTH;
            case RANGED -> SkillType.DEXTERITY;
            case MAGIC -> SkillType.MANA;
        };
    }

    public int getRank(Branch branch) {
        return ranks.get(branch);
    }

    public boolean owns(SkillType type) {
        if (type.getKind() == NodeKind.STAT) return getRank(type.getBranch()) > 0;
        return owned.contains(type);
    }

    public int ownedSkillsIn(SkillType subtree) {
        int count = 0;
        for (SkillType t : SkillType.skillsOf(subtree)) if (owned.contains(t)) count++;
        return count;
    }

    public int cost(SkillType type) {
        return switch (type.getKind()) {
            case START -> 0;
            case STAT -> STAT_COST;
            case SUBTREE -> SUBTREE_COST;
            case SKILL -> SKILL_BASE_COST + ownedSkillsIn(type.getParent());
        };
    }

    public int requiredRank(SkillType type) {
        return switch (type.getKind()) {
            case START, STAT -> 0;
            case SUBTREE -> SUBTREE_REQUIRED_RANK;
            case SKILL -> SKILL_BASE_REQUIRED_RANK + 2 * ownedSkillsIn(type.getParent());
        };
    }

    /** @return why the node cannot be bought (ignoring skill points), or null if it can. */
    public String lockReason(SkillType type) {
        int rank = getRank(type.getBranch());
        String stat = statOf(type.getBranch()).getDisplayName();
        return switch (type.getKind()) {
            case START -> "Owned";
            case STAT -> rank >= MAX_RANK ? "Max rank" : null;
            case SUBTREE -> owned.contains(type) ? "Owned"
                    : rank < SUBTREE_REQUIRED_RANK ? "Needs " + stat + " " + SUBTREE_REQUIRED_RANK : null;
            case SKILL -> owned.contains(type) ? "Owned"
                    : !owned.contains(type.getParent()) ? "Needs " + type.getParent().getDisplayName()
                    : rank < requiredRank(type) ? "Needs " + stat + " " + requiredRank(type) : null;
        };
    }

    public boolean canBuy(SkillType type, double skillPoints) {
        return lockReason(type) == null && skillPoints >= cost(type);
    }

    //-----Buying-----
    public boolean buy(SkillType type) {
        if (lockReason(type) != null) return false;
        PlayerStatsComponent stats = Game.getPlayer().getComponent(PlayerStatsComponent.class);
        if (!stats.takeSkillPoints(cost(type))) return false;

        if (type.getKind() == NodeKind.STAT) {
            ranks.merge(type.getBranch(), 1, Integer::sum);
        } else {
            owned.add(type);
            autoAssign(type);
        }
        fire();
        return true;
    }

    //-----Hotbar-----
    public SkillType getSlot(int slot) {
        return hotbar[slot];
    }

    public int slotOf(SkillType type) {
        for (int i = 0; i < HOTBAR_SIZE; i++) if (hotbar[i] == type) return i;
        return -1;
    }

    public int firstEmptySlot() {
        for (int i = 0; i < HOTBAR_SIZE; i++) if (hotbar[i] == null) return i;
        return -1;
    }

    /** Puts a skill into a slot; if it already sits in another slot the two slots swap. */
    public void assign(SkillType type, int slot) {
        if (!owns(type) || !type.isUsable() || slot < 0 || slot >= HOTBAR_SIZE) return;
        int old = slotOf(type);
        if (old == slot) return;
        if (old >= 0) hotbar[old] = hotbar[slot];
        hotbar[slot] = type;
        fire();
    }

    private void autoAssign(SkillType type) {
        if (!type.isUsable() || slotOf(type) >= 0) return;
        int empty = firstEmptySlot();
        if (empty >= 0) hotbar[empty] = type;
    }

    //-----Listeners-----
    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public void fire() {
        for (Runnable r : new ArrayList<>(listeners)) r.run();
    }
}
