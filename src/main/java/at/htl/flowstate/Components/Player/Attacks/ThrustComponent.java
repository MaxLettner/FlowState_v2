package at.htl.flowstate.Components.Player.Attacks;

import at.htl.flowstate.Combat.Combat;
import at.htl.flowstate.Combat.Hit;
import at.htl.flowstate.Game;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;

import java.util.HashSet;
import java.util.Set;

/** A straight jab forward and back (punches, trident stabs). */
public class ThrustComponent extends Component {
    private final Entity player;
    private final Hit hit;
    private final double reach;
    private final double duration;
    private final int facing;
    private final Set<Entity> alreadyHit = new HashSet<>();
    private double elapsed = 0;

    public ThrustComponent(Hit hit, double reach, double duration, int facing) {
        this.player = Game.getPlayer();
        this.hit = hit;
        this.reach = reach;
        this.duration = duration;
        this.facing = facing;
    }

    @Override
    public void onAdded() {
        place(0);
    }

    @Override
    public void onUpdate(double tpf) {
        elapsed += tpf;
        double t = Math.min(elapsed / duration, 1.0);
        place(t < 0.5 ? t * 2 : (1 - t) * 2);

        for (Entity e : Combat.enemies()) {
            if (!alreadyHit.contains(e) && e.isColliding(entity)) {
                alreadyHit.add(e);
                Combat.hit(e, hit);
            }
        }

        if (t >= 1.0) entity.removeFromWorld();
    }

    private void place(double extension) {
        double w = entity.getWidth();
        double x = player.getCenter().getX() + facing * (10 + extension * reach) - (facing < 0 ? w : 0);
        double y = player.getY() + player.getHeight() * 0.4 - entity.getHeight() / 2;
        entity.setPosition(x, y);
    }
}
