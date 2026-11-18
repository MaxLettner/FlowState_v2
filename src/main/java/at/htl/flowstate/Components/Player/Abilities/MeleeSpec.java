package at.htl.flowstate.Components.Player.Abilities;

import at.htl.flowstate.Combat.DamageKind;
import at.htl.flowstate.Combat.Hit;
import at.htl.flowstate.Components.Player.Attacks.MeleeSwingComponent;
import at.htl.flowstate.Skills.SkillType;
import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.physics.BoundingShape;
import com.almasb.fxgl.physics.HitBox;
import at.htl.flowstate.Visuals.WeaponArt;
import javafx.scene.image.ImageView;

/**
 * Stats and visuals of a swung melee weapon. Values from concept_new.
 * The texture is placed by its handle at the swing pivot (see WeaponArt).
 */
public record MeleeSpec(String texture,
                        double width, double reach, double damage, double interval, double swingTime,
                        double critPercent, double knockback, double stun, SkillType family, boolean launch) {

    public static final MeleeSpec RUSTY_SWORD = new MeleeSpec("RustySword.png", 10, 80, 10, 0.6, 0.4, 1, 200, 0, null, false);
    public static final MeleeSpec IRON_SWORD = new MeleeSpec("BasicSword.png", 10, 90, 15, 0.6, 0.4, 2, 200, 0, SkillType.SWORDS, false);
    public static final MeleeSpec SHORTSWORD = new MeleeSpec("ShortSword.png", 10, 85, 15, 0.45, 0.3, 3, 150, 0, SkillType.SWORDS, false);
    public static final MeleeSpec DUAL_WIELDING = new MeleeSpec("DualWieldingSword.png", 10, 100, 8, 0.25, 0.4, 1, 100, 0, SkillType.SWORDS, false);
    public static final MeleeSpec ZWEIHANDER = new MeleeSpec("Zweihander.png", 20, 160, 35, 1.2, 0.7, 2, 400, 0, SkillType.SWORDS, false);
    public static final MeleeSpec CLUB = new MeleeSpec("Knuppel.png", 10, 90, 15, 0.9, 0.5, 1, 300, 0.5, SkillType.BLUNT, false);
    public static final MeleeSpec HAMMER = new MeleeSpec("BigHammer.png", 20, 110, 20, 1.2, 0.6, 1, 500, 1.0, SkillType.BLUNT, false);
    public static final MeleeSpec MORNINGSTAR = new MeleeSpec("BigMace.png", 20, 150, 25, 1.2, 0.7, 1, 450, 0, SkillType.BLUNT, false);
    public static final MeleeSpec SPRING_HAMMER = new MeleeSpec("SpringHammer.png", 15, 100, 3, 0.3, 0.25, 0, 1100, 0, SkillType.BLUNT, true);

    /** The default hit of this weapon in the given knockback direction. */
    public Hit hit(int facing) {
        Hit h = Hit.of(damage, DamageKind.MELEE).family(family).crit(critPercent).knockback(knockback, facing).stun(stun);
        return launch ? h.launch() : h;
    }

    /** Spawns a swing entity. sizeMult scales the reach and texture (Zweihander charge). */
    public Entity swing(Hit hit, double duration, boolean reverse, double sizeMult) {
        ImageView tex = WeaponArt.hanging(texture, width / 2, 0, sizeMult);
        return FXGL.entityBuilder()
                .bbox(new HitBox(BoundingShape.box(width, reach * sizeMult)))
                .view(tex)
                .with(new MeleeSwingComponent(hit, duration, reverse))
                .zIndex(13)
                .buildAndAttach();
    }
}
