package at.htl.flowstate.Components.Enemies;

import com.almasb.fxgl.entity.component.Component;

public class BallisticProjectileComponent extends Component {
    private static final double GRAVITY = 800;

    private double vx;
    private double vy;

    public BallisticProjectileComponent(double vx, double vy) {
        this.vx = vx;
        this.vy = vy;
    }

    @Override
    public void onAdded() {
        entity.setRotationOrigin(new javafx.geometry.Point2D(entity.getWidth() / 2, entity.getHeight() / 2));
    }

    @Override
    public void onUpdate(double tpf) {
        vy += GRAVITY * tpf;

        entity.translateX(vx * tpf);
        entity.translateY(vy * tpf);

        if (Math.abs(vx) > 0.01 || Math.abs(vy) > 0.01) {
            double angle = Math.toDegrees(Math.atan2(vy, vx));
            entity.setRotation(angle);
        }
    }
}