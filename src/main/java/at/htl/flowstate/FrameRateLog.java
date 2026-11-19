package at.htl.flowstate;

import javafx.animation.AnimationTimer;

/**
 * Debugging aid for the game speed. Run with {@code FLOWSTATE_FPS=1} and every 2 s the console shows how many frames
 * JavaFX drew, the frame rate FXGL assumes, how fast game time ran compared to real time, and the longest frame.
 * <p>
 * FXGL does not time each frame: it counts the frames of the last 2 s and uses that rate for the next 2 s.
 * So a hitch (one long frame) makes the following 2 s run too fast, and an uncapped frame rate makes the
 * whole game too fast. A game speed far from 100 % here is the sign of either.
 */
public final class FrameRateLog {
    private static final boolean ON = "1".equals(System.getenv("FLOWSTATE_FPS"));
    private static final long WINDOW = 2_000_000_000L;

    private static long windowStart, lastFrame, longest;
    private static int frames;
    private static double gameTime, lastTpf;

    private FrameRateLog() {}

    public static void install() {
        if (!ON) return;
        new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (windowStart == 0) {
                    windowStart = lastFrame = now;
                    return;
                }
                frames++;
                longest = Math.max(longest, now - lastFrame);
                lastFrame = now;
                if (now - windowStart < WINDOW) return;
                double real = (now - windowStart) / 1e9;
                System.out.printf("[fps] drawn %3.0f/s   FXGL assumes %3.0f/s   game speed %3.0f %%   longest frame %3.0f ms%n",
                        frames / real, lastTpf > 0 ? 1 / lastTpf : 0, gameTime / real * 100, longest / 1e6);
                windowStart = now;
                frames = 0;
                longest = 0;
                gameTime = 0;
            }
        }.start();
    }

    /** Call from the game's update with FXGL's tpf. */
    public static void gameUpdate(double tpf) {
        if (!ON) return;
        gameTime += tpf;
        lastTpf = tpf;
    }
}
