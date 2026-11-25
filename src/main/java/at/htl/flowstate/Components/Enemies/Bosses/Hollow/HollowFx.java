package at.htl.flowstate.Components.Enemies.Bosses.Hollow;

import at.htl.flowstate.Assets;
import at.htl.flowstate.Skills.SkillType;
import com.almasb.fxgl.entity.component.Component;
import javafx.geometry.Point2D;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;

/** Small effects of the Hollow Ember. */
final class HollowFx {
    private HollowFx() {}

    /** A void copy of the skill's hotbar icon flashing up over a spot (the Hollow swapping to that skill). */
    static void icon(SkillType skill, Point2D above) {
        ImageView icon;
        try {
            icon = Assets.sprite("void:" + skill.getIconName(), 2);
        } catch (IllegalArgumentException e) {
            return;
        }
        icon.setEffect(new DropShadow(10, Color.web("#a86cff")));
        double w = icon.getImage().getWidth();
        entityBuilder()
                .at(above.subtract(w / 2, icon.getImage().getHeight()))
                .view(icon)
                .with(new Rise())
                .zIndex(30)
                .buildAndAttach();
    }

    private static final class Rise extends Component {
        private static final double LIFE = 1.1;
        private double age;

        @Override
        public void onUpdate(double tpf) {
            age += tpf;
            entity.translateY(-40 * tpf);
            // flashes in, holds, fades
            entity.setOpacity(age < 0.12 ? age / 0.12 : age < 0.7 ? (((int) (age * 20)) % 2 == 0 ? 1 : 0.55) : Math.max(0, 1 - (age - 0.7) / (LIFE - 0.7)));
            if (age >= LIFE) entity.removeFromWorld();
        }
    }
}
