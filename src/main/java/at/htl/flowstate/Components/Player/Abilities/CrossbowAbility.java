package at.htl.flowstate.Components.Player.Abilities;

import at.htl.flowstate.Visuals.FrameSprite;
import at.htl.flowstate.Visuals.Frames;
import at.htl.flowstate.Assets;
import at.htl.flowstate.Combat.DamageKind;
import at.htl.flowstate.Combat.Hit;
import at.htl.flowstate.Components.Enemies.EnemyStatsComponent;
import at.htl.flowstate.Components.Player.Attacks.HeldWeaponComponent;
import at.htl.flowstate.Components.Player.Attacks.PlayerProjectileComponent;
import at.htl.flowstate.Components.Player.LoadoutComponent;
import at.htl.flowstate.Skills.Branch;
import at.htl.flowstate.Skills.SkillType;
import com.almasb.fxgl.entity.Entity;
import javafx.scene.paint.Color;

/**
 * Basic, Dual, Heavy and Poison Crossbow: flat bolts.
 * Hold to crank a bolt in (slowed while cranking; letting go early resets the crank). Once loaded it stays loaded,
 * also across weapon switches, and the next click fires it. Releasing the button after loading does nothing.
 */
public class CrossbowAbility extends Ability {
    public enum Crossbow {
        BASIC("BasicCrossbow.png", 13.5, 13.5, "Bolt.png", 20, 1.0, 1400, 200, 0, 1, 1, 22, 4),
        DUAL("DualCrossbow.png", 13.5, 13.5, "Bolt.png", 10, 1.3, 1600, 200, 0, 1, 2, 22, 4),
        HEAVY("HeavyCrossbow.png", 16.5, 15.5, "HeavyBolt.png", 70, 2.5, 2000, 0, 0, -1, 1, 34, 6),
        POISON("PoisonCrossbow.png", 13.5, 13.5, "PoisonBolt.png", 8, 0.7, 1400, 200, 900, 2, 1, 22, 4);

        final String texture, bolt;
        final double gripX, gripY; // grip inside a native frame (tools/art/ranged.py PIVOTS)
        final double damage, reload, speed, gravity, range;
        final int pierce, burst;
        final double boltLength, boltThickness;

        Crossbow(String texture, double gripX, double gripY, String bolt, double damage, double reload, double speed,
                 double gravity, double range, int pierce, int burst, double boltLength, double boltThickness) {
            this.texture = texture;
            this.gripX = gripX;
            this.gripY = gripY;
            this.bolt = bolt;
            this.damage = damage;
            this.reload = reload;
            this.speed = speed;
            this.gravity = gravity;
            this.range = range;
            this.pierce = pierce;
            this.burst = burst;
            this.boltLength = boltLength;
            this.boltThickness = boltThickness;
        }
    }

    private static final double QUICK_RELOAD = 0.85; // Crossbow passive
    private static final double CRANK_MOVE_MULT = 0.5;
    private static final double LOADED_SHOW = 0.25;  // seconds the crossbow stays out after loading
    private static final double BURST_GAP = 0.1;
    private static final double PIN_DURATION = 2.0;
    private static final int POISON_STACKS = 2;

    private final Crossbow crossbow;
    private int pendingShots = 0;
    private double burstTimer = 0;
    private boolean loaded = true;
    private boolean cranking = false;
    private double crankTime = 0;
    private Entity heldCrossbow;
    private Frames crossbowFrames;
    private FrameSprite crossbowSprite;

    public CrossbowAbility(Entity player, LoadoutComponent loadout, Crossbow crossbow) {
        super(player, loadout, Branch.RANGED);
        this.crossbow = crossbow;
    }

    @Override
    public void primaryPress() {
        if (pendingShots > 0 || cranking) return;
        if (loaded) fire();
        else startCrank();
    }

    @Override
    public void primaryRelease() {
        // let go before the bolt is in: the crank slips back to empty
        if (cranking) stopCrank(false);
    }

    private double reloadTime() {
        return crossbow.reload * QUICK_RELOAD;
    }

    private void startCrank() {
        cranking = true;
        crankTime = 0;
        crossbowFrames = Assets.frames(crossbow.texture, 2, 2);
        crossbowSprite = new FrameSprite(crossbowFrames);
        crossbowSprite.show(crossbowFrames, 1); // empty
        heldCrossbow = HeldWeaponComponent.spawnAimed(crossbowSprite, crossbow.gripX * 2, crossbow.gripY * 2, 16, 0);
    }

    private void stopCrank(boolean done) {
        cranking = false;
        crankTime = 0;
        if (done) loaded = true;
        if (heldCrossbow != null) {
            crossbowSprite.show(crossbowFrames, done ? 0 : 1);
            heldCrossbow.getComponent(HeldWeaponComponent.class).setLifetime(done ? LOADED_SHOW : 0.05);
            heldCrossbow = null;
        }
    }

    private void fire() {
        loaded = false;
        fireBolt();
        pendingShots = crossbow.burst - 1;
        burstTimer = BURST_GAP;
        // loaded -> empty, stays empty while the reload runs
        Frames frames = Assets.frames(crossbow.texture, 2, 2);
        FrameSprite held = new FrameSprite(frames).play(frames, new int[]{0, 1}, 20, false, null);
        HeldWeaponComponent.spawnAimed(held, crossbow.gripX * 2, crossbow.gripY * 2, 16,
                0.15 + BURST_GAP * (crossbow.burst - 1) + 0.2);
    }

    @Override
    public void update(double tpf) {
        if (cranking) {
            crankTime += tpf;
            if (crankTime >= reloadTime()) stopCrank(true);
        }
        if (pendingShots <= 0) return;
        burstTimer -= tpf;
        if (burstTimer <= 0) {
            fireBolt();
            pendingShots--;
            burstTimer = BURST_GAP;
        }
    }

    @Override
    public void onDeselect() {
        pendingShots = 0;
        if (cranking) stopCrank(false);
    }

    @Override
    public double getChargeProgress() {
        return cranking ? Math.min(crankTime / reloadTime(), 1) : -1;
    }

    @Override
    public double getMoveSpeedMult() {
        return cranking ? CRANK_MOVE_MULT : 1;
    }

    @Override
    public double getSlotCooldown() {
        if (loaded) return 0;
        return 1 - (cranking ? Math.min(crankTime / reloadTime(), 1) : 0);
    }

    @Override
    public double getSlotCooldownTotal() {
        return reloadTime();
    }

    @Override
    public String getStatusText() {
        return loaded ? "loaded" : cranking ? "cranking" : "empty";
    }

    private void fireBolt() {
        PlayerProjectileComponent bolt = new PlayerProjectileComponent(
                aim().multiply(crossbow.speed),
                pierce(crossbow.pierce),
                (e, index) -> {
                    Hit h = Hit.of(crossbow.damage, DamageKind.RANGED).family(SkillType.CROSSBOW);
                    if (crossbow == Crossbow.POISON && index == 0) h.poison(POISON_STACKS); // only the first hit poisons
                    return h;
                })
                .gravity(crossbow.gravity);
        if (crossbow.range > 0) bolt.maxDistance(crossbow.range);
        if (crossbow == Crossbow.HEAVY) {
            bolt.onStop((pos, vel, lastEnemy, terrain) -> {
                if (terrain && lastEnemy != null && lastEnemy.isActive()) {
                    lastEnemy.getComponent(EnemyStatsComponent.class).pin(PIN_DURATION);
                }
            });
        }
        spawnProjectile(projectileSprite(crossbow.bolt, crossbow.boltLength, crossbow.boltThickness),
                crossbow.boltLength, crossbow.boltThickness, bolt);
    }
}
