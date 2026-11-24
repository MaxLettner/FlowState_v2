package at.htl.flowstate.Components.Enemies;

import at.htl.flowstate.Components.SpriteComponents.SpriteComponent;
import at.htl.flowstate.Visuals.FrameSprite;
import at.htl.flowstate.Visuals.Frames;
import com.almasb.fxgl.entity.Entity;

/** The last few seconds of the player (position, facing, animation frame), one sample per frame, for the echoes. */
final class HollowRecorder {
    static final int SIZE = 240;       // about 4 s at 60 fps

    /** A copy of the recording, oldest sample first. */
    record Clip(double[] x, double[] y, int[] facing, Frames[] frames, int[] frame, int length) {}

    private final double[] x = new double[SIZE], y = new double[SIZE];
    private final int[] facing = new int[SIZE], frame = new int[SIZE];
    private final Frames[] frames = new Frames[SIZE];
    private int next, count;

    void record(Entity player) {
        SpriteComponent sc = player.getComponent(SpriteComponent.class);
        FrameSprite sprite = sc.getSprite();
        x[next] = player.getX();
        y[next] = player.getY();
        facing[next] = sc.getFacing();
        frames[next] = sprite.getFrames();
        frame[next] = sprite.getFrameIndex();
        next = (next + 1) % SIZE;
        count = Math.min(SIZE, count + 1);
    }

    int count() {
        return count;
    }

    /** The newest {@code length} samples (at most what was recorded). */
    Clip clip(int length) {
        int n = Math.min(length, count);
        Clip c = new Clip(new double[n], new double[n], new int[n], new Frames[n], new int[n], n);
        for (int i = 0; i < n; i++) {
            int k = Math.floorMod(next - n + i, SIZE);
            c.x[i] = x[k];
            c.y[i] = y[k];
            c.facing[i] = facing[k];
            c.frames[i] = frames[k];
            c.frame[i] = frame[k];
        }
        return c;
    }
}
