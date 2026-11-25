package at.htl.flowstate.Components.Enemies.Bosses.Hydra;

import at.htl.flowstate.Combat.Combat;
import at.htl.flowstate.Combat.DamageKind;
import at.htl.flowstate.Combat.Element;
import at.htl.flowstate.Combat.Hit;
import at.htl.flowstate.Components.Enemies.Bosses.BossArena;
import at.htl.flowstate.Components.Enemies.DemonBehaviour;
import at.htl.flowstate.Components.Player.LoadoutComponent;
import at.htl.flowstate.Sfx;
import at.htl.flowstate.Skills.SkillType;
import at.htl.flowstate.Visuals.Impact;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.core.math.FXGLMath;
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
    private final HydraArena lake = BossArena.active().hydra();
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
        return new Point2D(lake.lockX() + kind.baseX + facing * 30, lake.groundY() - REST_ABOVE);
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
        if (FXGLMath.randomDouble() < tpf * 3) Sfx.CRACKLE.at(pos, 0.5, 0.7);
        timer -= dt(tpf);
        if (timer <= 1.0 && FXGLMath.randomDouble() < tpf * 14) {          // about to regrow: tar bubbles up out of it
            Particles.of(HydraArena.TAR).count(1).angle(-90, 30).speed(60, 160).life(0.5).size(5).gravity(600).at(pos.add(0, -6));
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
                Particles.of(Particles.EMBER).count(26).angle(-90, 80).speed(80, 300).life(0.7).size(5).gravity(-80).at(c);
            }
            case "frozen" -> {
                Sfx.FREEZE.at(c, 1, 0.8);
                Particles.of(Particles.ICE).count(24).angle(-90, 90).speed(80, 280).life(0.6).size(5).gravity(300).at(c);
            }
            case "rotted" -> {
                Sfx.CAST_POISON.at(c, 1, 0.7);
                Particles.of(Particles.POISON).count(24).angle(-90, 80).speed(60, 220).life(0.8).size(5).gravity(200).at(c);
            }
            default -> {
                Sfx.BRUTE_SLAM.at(c, 0.8, 1.1);
                Impact.shake(12);
                Particles.of(Particles.ROCK).count(18).angle(-90, 90).speed(100, 320).life(0.7).size(6).gravity(900).at(c);
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
