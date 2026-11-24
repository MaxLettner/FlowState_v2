package at.htl.flowstate.Visuals;

import com.almasb.fxgl.core.View;
import javafx.scene.Node;
import javafx.scene.image.ImageView;

/**
 * An ImageView that plays frames from a strip. When it is a direct child of an entity's view
 * (entityBuilder().view(...) or ViewComponent.addChild) FXGL calls onUpdate every frame.
 * Otherwise call {@link #onUpdate(double)} yourself.
 */
public class FrameSprite extends ImageView implements View {
    private Frames frames;
    private int[] sequence;
    private double fps;
    private boolean loop;
    private boolean paused;
    private double time;
    private int index;
    private Runnable onFinished;

    public FrameSprite(Frames frames) {
        setSmooth(false);
        show(frames, 0);
    }

    /** Loops through all frames of the strip. */
    public FrameSprite loop(Frames frames, double fps) {
        return play(frames, all(frames), fps, true, null);
    }

    /** Plays all frames once and then holds the last one. */
    public FrameSprite once(Frames frames, double fps, Runnable onFinished) {
        return play(frames, all(frames), fps, false, onFinished);
    }

    /** Plays the given frame indices (once or looping). */
    public FrameSprite play(Frames frames, int[] sequence, double fps, boolean loop, Runnable onFinished) {
        this.frames = frames;
        this.sequence = sequence;
        this.fps = fps;
        this.loop = loop;
        this.onFinished = onFinished;
        this.time = 0;
        this.index = 0;
        setImage(frames.image());
        apply();
        return this;
    }

    /** Shows one fixed frame (stops any animation). */
    public void show(Frames frames, int frame) {
        this.frames = frames;
        this.sequence = new int[]{frame};
        this.fps = 0;
        this.loop = false;
        this.onFinished = null;
        this.index = 0;
        setImage(frames.image());
        apply();
    }

    public boolean isPlaying(Frames f) {
        return frames == f;
    }

    public boolean isFinished() {
        return !loop && index >= sequence.length - 1;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    /** The strip frame showing right now. */
    public int getFrameIndex() {
        return sequence[Math.min(index, sequence.length - 1)];
    }

    public Frames getFrames() {
        return frames;
    }

    @Override
    public void onUpdate(double tpf) {
        if (paused || fps <= 0 || sequence.length <= 1) return;
        time += tpf;
        double frameTime = 1.0 / fps;
        if (time < frameTime) return; // same frame as before: nothing to change
        while (time >= frameTime) {
            time -= frameTime;
            if (index < sequence.length - 1) {
                index++;
            } else if (loop) {
                index = 0;
            } else {
                Runnable done = onFinished;
                onFinished = null;
                fps = 0;
                if (done != null) done.run();
                return;
            }
        }
        apply();
    }

    private void apply() {
        setViewport(frames.viewport(sequence[Math.min(index, sequence.length - 1)]));
    }

    private static int[] all(Frames frames) {
        int[] seq = new int[frames.count()];
        for (int i = 0; i < seq.length; i++) seq[i] = i;
        return seq;
    }

    @Override
    public Node getNode() {
        return this;
    }

    @Override
    public void dispose() {}
}
