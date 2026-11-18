package at.htl.flowstate.Components.Chests;

import at.htl.flowstate.Assets;
import at.htl.flowstate.Components.Player.PlayerStatsComponent;
import at.htl.flowstate.Game;
import at.htl.flowstate.Visuals.FrameSprite;
import at.htl.flowstate.Visuals.Frames;
import at.htl.flowstate.Visuals.Particles;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.component.Component;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;

/**
 * Chest strip (tools/art/effects.py): 0-1 idle (1 = glint), 2-5 opening, 6 open + glow, 7 open and empty.
 * Hitbox 80x50, art 40x34 shown at x2 and bottom-anchored.
 */
public class ChestComponent extends Component {
    private static final int[] IDLE = {0, 0, 0, 0, 0, 0, 0, 1};
    private static final int[] OPEN = {2, 3, 4, 5, 6, 6, 6, 6, 6, 6};
    private static final Color[] GOLD = {Color.web("#fff4b0"), Color.web("#f5cd52"), Color.web("#cf9c2f")};

    private final Entity player = Game.getPlayer();
    private final Frames frames = Assets.frames("Chest.png", 8, 2);
    private final FrameSprite sprite = new FrameSprite(frames);
    private boolean isClosed = true;

    @Override
    public void onAdded() {
        sprite.setTranslateX(entity.getWidth() / 2 - frames.width() / 2);
        sprite.setTranslateY(entity.getHeight() - 66);
        sprite.play(frames, IDLE, 4, true, null);
        entity.getViewComponent().addChild(sprite);
    }

    @Override
    public void onUpdate(double tpf) {
        if (isClosed && entity.isColliding(player)) {
            isClosed = false;
            player.getComponent(PlayerStatsComponent.class).addSkillPoint();
            sprite.play(frames, OPEN, 12, false, () -> sprite.show(frames, 7));
            Point2D top = entity.getPosition().add(entity.getWidth() / 2, entity.getHeight() - 30);
            Particles.burst(top, GOLD, 16, -90, 40, 200, 480, 0.8, 5, 700);
        }
    }
}
