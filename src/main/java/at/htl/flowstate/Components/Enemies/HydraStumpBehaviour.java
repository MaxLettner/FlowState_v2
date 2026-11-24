package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Combat.Combat;
import at.htl.flowstate.Combat.DamageKind;
import at.htl.flowstate.Combat.Element;
import at.htl.flowstate.Combat.Hit;
import at.htl.flowstate.Components.Player.LoadoutComponent;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Skills.SkillType;
import at.htl.flowstate.Visuals.Impact;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.entity.Entity;
import javafx.geometry.Point2D;

/**
 * The cut end of a Tar Hydra neck. It smokes for {@link #REGROW_AFTER} seconds; seal it before then or the head grows
 * back (at half health). Every build can seal it:
 * <ul>
 *   <li>a fire hit sears it shut ("seared"),</li>
 *   <li>an ice hit freezes it ("frozen"),</li>
 *   <li>a poison hit rots it ("rotted"),</li>
 *   <li>a heavy blow crushes it ("crushed"): a blunt weapon, or a hit of {@link #HEAVY_DAMAGE}+ damage or
 *   {@link #HEAVY_KNOCKBACK}+ knockback (Zweihander, Heavy Crossbow, Heavy Trident).</li>
 * </ul>
 * The hitbox is the cut face; the neck ends {@link #NECK_BELOW} radii under its centre.
 */
public class HydraStumpBehaviour extends DemonBehaviour {
    static final double REGROW_AFTER = 4.0;
    private static final double HEAVY_DAMAGE = 34, HEAVY_KNOCKBACK = 300;
    static final double NECK_BELOW = 0.6;
    private static final double SETTLE = 3.0;                    // how quickly it sinks to where it rests
    private static final double REST_ABOVE = 170;                // where it rests, above the floor
    private static final double SMOKE_FPS = 8;
    private static final double SINK_SPEED = 240, SINK_EASE = 0.5;   // px/s, reached after SINK_EASE s

    final HydraHeadBehaviour.Kind kind;
    private HydraBehaviour owner;
    private String sealed;
    private double timer = REGROW_AFTER;
    private Point2D pos;
    private int facing = 1;
    private boolean sinking;
    private double sinkT;

    public HydraStumpBehaviour(HydraHeadBehaviour.Kind kind) {
        this.kind = kind;
    }

    void setOwner(HydraBehaviour owner, int facing) {
        this.owner = owner;
        this.facing = facing;
    }

    @Override
    protected void onReady() {
        entity.setZIndex(kind.layer);                            // where its head was: behind the pillars, on its neck
        pos = entity.getCenter();
        sprite.setFacing(facing);
        sprite.loop("idle", SMOKE_FPS);
    }

    boolean isSealed() {
        return sealed != null;
    }

    /** The end of the neck it sits on. */
    Point2D neckEnd() {
        Point2D c = pos != null ? pos : entity.getCenter();         // asked before its first update, right after the cut
        return c.add(0, kind.neckRadius * NECK_BELOW);
    }

    /** Where it settles: low over the tar, near its root. */
    private Point2D rest() {
        return new Point2D(HydraArena.lockX() + kind.baseX + facing * 30, HydraArena.groundY() - REST_ABOVE);
    }

    /** The last phase coming, or the death scene: it slides down into the tar, slowly at first. */
    void sink() {
        sinking = true;
    }

    @Override
    protected void think(double tpf) {
        if (sinking) {
            sinkT += tpf;
            place(pos.add(0, SINK_SPEED * Math.min(1, sinkT / SINK_EASE) * tpf));
            return;
        }
        place(pos.add(rest().subtract(pos).multiply(Math.min(1, SETTLE * tpf))));
        if (sealed != null || owner == null) return;
        if (Math.random() < tpf * 3) Sfx.CRACKLE.at(pos, 0.5, 0.7);
        timer -= dt(tpf);
        if (timer <= 1.0 && Math.random() < tpf * 14) {          // about to regrow: tar bubbles up out of it
            Particles.burst(pos.add(0, -6), HydraArena.TAR, 1, -90, 30, 60, 160, 0.5, 5, 600);
        }
        if (timer <= 0) owner.regrow(this);
    }

    @Override
    protected void whileDisabled(double tpf) {
        place(pos);
    }

    /** Every hit gets through (the stump has more health than anything can deal); the right ones seal it. */
    @Override
    public double guard(Hit h, Entity p) {
        if (sealed != null || sinking) return 1;
        String how = sealOf(h, p);
        if (how != null) seal(how);
        return 1;
    }

    private static String sealOf(Hit h, Entity p) {
        LoadoutComponent loadout = p.getComponent(LoadoutComponent.class);
        boolean weaponHit = h.getKind() == DamageKind.MELEE || h.getKind() == DamageKind.RANGED;
        Element e = Combat.element(h, weaponHit, loadout);
        if (e == Element.FIRE) return "seared";
        if (e == Element.ICE) return "frozen";
        boolean poisonImbue = weaponHit && loadout.getActiveEnchant() == SkillType.IMBUE && loadout.getImbueElement() == Element.POISON;
        if (h.getPoisonStacks() > 0 || poisonImbue) return "rotted";
        if (h.getFamily() == SkillType.BLUNT || h.getDamage() >= HEAVY_DAMAGE || h.getKnockback() >= HEAVY_KNOCKBACK) return "crushed";
        return null;
    }

    private void seal(String how) {
        sealed = how;
        sprite.show(how, 0);
        Point2D c = pos;
        switch (how) {
            case "seared" -> {
                Sfx.CRACKLE.at(c, 1, 0.6);
                Sfx.CAST_FIREBALL.at(c, 0.6, 0.7);
                Particles.burst(c, Particles.EMBER, 26, -90, 80, 80, 300, 0.7, 5, -80);
            }
            case "frozen" -> {
                Sfx.FREEZE.at(c, 1, 0.8);
                Particles.burst(c, Particles.ICE, 24, -90, 90, 80, 280, 0.6, 5, 300);
            }
            case "rotted" -> {
                Sfx.CAST_POISON.at(c, 1, 0.7);
                Particles.burst(c, Particles.POISON, 24, -90, 80, 60, 220, 0.8, 5, 200);
            }
            default -> {
                Sfx.BRUTE_SLAM.at(c, 0.8, 1.1);
                Impact.shake(12);
                Particles.burst(c, Particles.ROCK, 18, -90, 90, 100, 320, 0.7, 6, 900);
            }
        }
        Sfx.HYDRA_ROAR.at(c, 0.5, 1.6);
        if (owner != null) owner.stumpSealed(this);
    }

    @Override
    public void onRemoved() {
        if (owner != null) owner.stumpRemoved(this);
    }

    void place(Point2D centre) {
        pos = centre;
        physics.setVelocityX(0);
        physics.setVelocityY(0);
        physics.overwritePosition(centre.subtract(entity.getWidth() / 2, entity.getHeight() / 2));
    }
}
