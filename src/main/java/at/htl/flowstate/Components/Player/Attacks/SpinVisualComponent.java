package at.htl.flowstate.Components.Player.Attacks;

import at.htl.flowstate.Game;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import javafx.geometry.Point2D;

/** Morningstar flail spin visual: rotates around the player's center. Damage is done by the ability. */
public class SpinVisualComponent extends Component {
    private static final double DEGREES_PER_SECOND = 720;
    private final Entity player = Game.getPlayer();

    @Override
    public void onAdded() {
        entity.setRotationOrigin(new Point2D(entity.getWidth() / 2, 0));
    }

    @Override
    public void onUpdate(double tpf) {
        entity.setPosition(player.getCenter().getX() - entity.getWidth() / 2, player.getCenter().getY());
        entity.setRotation(entity.getRotation() + DEGREES_PER_SECOND * tpf);
    }
}
