package at.htl.flowstate;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The background loops, mixed on their own audio line so they can change while they play: one loop crossfades into
 * the next (abyss, boss arena, menu), the whole bed ducks under heavy hits and gets muffled (low-passed) as the
 * player's health runs out. JavaFX's AudioClip can't change volume mid-play, hence javax.sound here.
 * Every loop is decoded up front ({@link #preload}); without an audio device everything here stays silent and
 * {@link #isAvailable()} is false, so Sfx can fall back to plain AudioClip loops.
 */
final class Ambience {
    private static final float RATE = 44100;
    private static final int CHUNK = 1024;                  // frames per write, ~23 ms
    private static final double FADE_IN = 2.5, FADE_OUT = 3.5;   // s for a full crossfade
    private static final double DUCK_RELEASE = 1.2;         // s to come back after a duck
    private static final double MUFFLE_LOWEST = 260;        // Hz, the cutoff at full muffle
    private static final double MUFFLE_SPEED = 0.8;         // muffle units per s

    private static final Map<String, short[]> pcm = new HashMap<>();
    private static final List<Voice> voices = new ArrayList<>();
    private static final Object lock = new Object();
    private static SourceDataLine line;
    private static boolean failed;

    // written from the FX thread, read by the mixer
    private static volatile boolean muted;
    private static volatile double duckDepth, duckHold;      // how far down, for how long before releasing
    private static volatile double muffleTarget;
    private static volatile long duckStamp;

    private Ambience() {}

    private static final class Voice {
        final String file;
        final short[] data;
        int pos;
        double gain, target, speed;

        Voice(String file, short[] data) {
            this.file = file;
            this.data = data;
        }
    }

    //-----Control (FX thread)-----
    /** Crossfades to this loop at this volume; null fades everything out. Already playing: only the volume moves. */
    static void play(String file, double volume) {
        if (!start()) return;
        synchronized (lock) {
            Voice found = null;
            for (Voice v : voices) {
                if (v.file.equals(file)) {
                    found = v;
                } else {
                    v.target = 0;
                    v.speed = 1 / FADE_OUT;
                }
            }
            if (file == null) return;
            if (found == null) {
                short[] data = load(file);
                if (data == null) return;
                found = new Voice(file, data);
                voices.add(found);
            }
            found.target = volume;
            found.speed = 1 / FADE_IN;
        }
    }

    /** Pushes the loops down by depth (0..1) for hold seconds, then they swell back. A bigger duck wins. */
    static void duck(double depth, double hold) {
        long now = System.nanoTime();
        double left = duckHold - (now - duckStamp) / 1e9;
        if (depth < duckDepth && left > 0) return;
        duckDepth = depth;
        duckHold = hold;
        duckStamp = now;
    }

    /** 0 = clear, 1 = heard through a wall (low health). Moves there smoothly. */
    static void muffle(double amount) {
        muffleTarget = Math.max(0, Math.min(1, amount));
    }

    static void setMuted(boolean m) {
        muted = m;
    }

    static boolean isAvailable() {
        return start();
    }

    /** Decodes the loops now: loading one mid-game would stall a frame. */
    static void preload(String... files) {
        for (String f : files) load(f);
    }

    //-----Mixer (own thread)-----
    private static boolean start() {
        if (line != null) return true;
        if (failed) return false;
        try {
            AudioFormat format = new AudioFormat(RATE, 16, 1, true, false);
            line = AudioSystem.getSourceDataLine(format);
            line.open(format, CHUNK * 2 * 4);
            line.start();
        } catch (Exception | LinkageError e) {
            System.err.println("No ambience line, falling back to plain loops: " + e.getMessage());
            failed = true;
            line = null;
            return false;
        }
        Thread t = new Thread(Ambience::run, "ambience");
        t.setDaemon(true);
        t.start();
        return true;
    }

    private static void run() {
        double[] mixBuf = new double[CHUNK];
        byte[] out = new byte[CHUNK * 2];
        double dt = CHUNK / RATE;
        double duck = 0, muffle = 0, lp = 0;
        while (true) {
            java.util.Arrays.fill(mixBuf, 0);
            synchronized (lock) {
                for (int i = voices.size() - 1; i >= 0; i--) {
                    Voice v = voices.get(i);
                    double from = v.gain;
                    double step = v.speed * dt;
                    v.gain = v.gain < v.target ? Math.min(v.target, v.gain + step) : Math.max(v.target, v.gain - step);
                    for (int k = 0; k < CHUNK; k++) {
                        double g = from + (v.gain - from) * k / CHUNK;      // no zipper noise
                        mixBuf[k] += v.data[v.pos] / 32768.0 * g;
                        if (++v.pos >= v.data.length) v.pos = 0;
                    }
                    if (v.gain <= 0 && v.target <= 0) voices.remove(i);
                }
            }
            // duck: straight down, hold, then a slow swell back
            double since = (System.nanoTime() - duckStamp) / 1e9;
            double want = since < duckHold ? duckDepth : 0;
            duck = want > duck ? Math.min(want, duck + dt / 0.06) : Math.max(want, duck - dt / DUCK_RELEASE);
            muffle += Math.max(-MUFFLE_SPEED * dt, Math.min(MUFFLE_SPEED * dt, muffleTarget - muffle));
            double cutoff = 18000 * Math.pow(MUFFLE_LOWEST / 18000, muffle);
            double a = 1 - Math.exp(-2 * Math.PI * cutoff / RATE);
            double gain = muted ? 0 : (1 - duck) * (1 - 0.25 * muffle);
            for (int k = 0; k < CHUNK; k++) {
                lp += a * (mixBuf[k] - lp);
                int s = (int) Math.max(-32768, Math.min(32767, lp * gain * 32767));
                out[2 * k] = (byte) s;
                out[2 * k + 1] = (byte) (s >> 8);
            }
            line.write(out, 0, out.length);         // blocks: this paces the thread
        }
    }

    //-----Loading-----
    private static short[] load(String file) {
        synchronized (pcm) {
            if (pcm.containsKey(file)) return pcm.get(file);
            short[] data = decode(file);
            pcm.put(file, data);
            return data;
        }
    }

    private static short[] decode(String file) {
        String path = "/assets/sounds/" + file + ".wav";
        try (InputStream raw = Ambience.class.getResourceAsStream(path)) {
            if (raw == null) {
                System.err.println("Missing ambience: " + path);
                return null;
            }
            try (AudioInputStream in = AudioSystem.getAudioInputStream(new BufferedInputStream(raw))) {
                byte[] bytes = in.readAllBytes();
                short[] data = new short[bytes.length / 2];
                for (int i = 0; i < data.length; i++) data[i] = (short) ((bytes[2 * i] & 0xff) | (bytes[2 * i + 1] << 8));
                return data.length > 0 ? data : null;
            }
        } catch (Exception e) {
            System.err.println("Can't load ambience " + path + ": " + e.getMessage());
            return null;
        }
    }
}
