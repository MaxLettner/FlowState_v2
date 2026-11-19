"""Tiny stdlib-only synthesizer for the game's sound effects.

A signal is a plain list of floats in -1..1 at SR samples per second. Every helper returns a new list.
Parameters that may change over time (frequencies, cutoffs) accept a number or a function of t in seconds.
"""
import math
import random
import struct
import wave

SR = 44100
TAU = 2 * math.pi


def n(seconds):
    return max(1, int(seconds * SR))


def _fn(v):
    return v if callable(v) else (lambda t, v=v: v)


def sweep(a, b, dur, curve='exp'):
    """A value moving from a to b over dur seconds (exp = perceptually even for frequencies)."""
    def f(t):
        u = min(max(t / dur, 0.0), 1.0)
        if curve == 'exp' and a > 0 and b > 0:
            return a * (b / a) ** u
        return a + (b - a) * u
    return f


#-----Sources-----
def tone(dur, freq, shape='sine', phase=0.0, vibrato=0.0, vib_rate=0.0):
    """Oscillator with a (possibly moving) frequency. vibrato is in semitones."""
    f = _fn(freq)
    out = []
    ph = phase
    for i in range(n(dur)):
        t = i / SR
        fr = f(t)
        if vibrato:
            fr *= 2 ** (vibrato * math.sin(TAU * vib_rate * t) / 12)
        ph += fr / SR
        p = ph % 1.0
        if shape == 'sine':
            s = math.sin(TAU * p)
        elif shape == 'square':
            s = 1.0 if p < 0.5 else -1.0
        elif shape == 'saw':
            s = 2 * p - 1
        elif shape == 'tri':
            s = 4 * abs(p - 0.5) - 1
        else:
            raise ValueError(shape)
        out.append(s)
    return out


def noise(dur, color='white', seed=None):
    rng = random.Random(seed)
    out = []
    if color == 'white':
        return [rng.uniform(-1, 1) for _ in range(n(dur))]
    if color == 'brown':
        v = 0.0
        for _ in range(n(dur)):
            v = (v + rng.uniform(-1, 1) * 0.06) * 0.995
            out.append(v * 3.5)
        return out
    if color == 'pink':
        b0 = b1 = b2 = 0.0
        for _ in range(n(dur)):
            w = rng.uniform(-1, 1)
            b0 = 0.99765 * b0 + w * 0.0990460
            b1 = 0.96300 * b1 + w * 0.2965164
            b2 = 0.57000 * b2 + w * 1.0526913
            out.append((b0 + b1 + b2 + w * 0.1848) * 0.2)
        return out
    raise ValueError(color)


def bell(dur, freq, partials=((1, 1.0), (2.76, 0.5), (5.4, 0.25), (8.93, 0.12)), decay=0.25):
    """Inharmonic struck partials; higher partials die faster."""
    out = [0.0] * n(dur)
    for ratio, amp in partials:
        tau = decay / (1 + 0.5 * (ratio - 1))
        for i in range(len(out)):
            t = i / SR
            out[i] += amp * math.sin(TAU * freq * ratio * t) * math.exp(-t / tau)
    return out


def pluck(dur, freq, damping=0.996, brightness=0.5, seed=None):
    """Karplus-Strong plucked string."""
    rng = random.Random(seed)
    period = max(2, int(SR / freq))
    buf = [rng.uniform(-1, 1) for _ in range(period)]
    # darker excitation for low brightness
    for _ in range(int((1 - brightness) * 4)):
        buf = [(buf[i] + buf[i - 1]) * 0.5 for i in range(period)]
    out = []
    idx = 0
    for _ in range(n(dur)):
        a = buf[idx]
        b = buf[(idx + 1) % period]
        buf[idx] = (a + b) * 0.5 * damping
        out.append(a)
        idx = (idx + 1) % period
    return out


def clicks(dur, rate, length=0.004, freq=(1500, 4000), jitter=0.3, seed=None, accel=0.0):
    """Random short filtered ticks: ratchets, crackle, debris. rate = clicks per second."""
    rng = random.Random(seed)
    out = [0.0] * n(dur)
    t = 0.0
    while t < dur:
        start = int(t * SR)
        f = rng.uniform(*freq)
        amp = rng.uniform(0.5, 1.0)
        for i in range(n(length)):
            if start + i >= len(out):
                break
            tt = i / SR
            out[start + i] += amp * math.sin(TAU * f * tt) * math.exp(-tt / (length / 4))
        r = rate * (1 + accel * t / dur)
        t += (1 / r) * (1 + rng.uniform(-jitter, jitter))
    return out


#-----Filters-----
def lowpass(sig, cutoff):
    c = _fn(cutoff)
    out = []
    y = 0.0
    for i, x in enumerate(sig):
        a = 1 - math.exp(-TAU * c(i / SR) / SR)
        y += a * (x - y)
        out.append(y)
    return out


def highpass(sig, cutoff):
    low = lowpass(sig, cutoff)
    return [x - l for x, l in zip(sig, low)]


def bandpass(sig, center, q=2.0):
    """State-variable band-pass; center may move over time."""
    c = _fn(center)
    low = band = 0.0
    damp = 1.0 / q
    out = []
    for i, x in enumerate(sig):
        f = 2 * math.sin(math.pi * min(c(i / SR), SR / 6) / SR)
        low += f * band
        high = x - low - damp * band
        band += f * high
        out.append(band)
    return out


#-----Shaping-----
def env(sig, attack=0.002, decay=0.2, hold=0.0, curve='exp'):
    """Attack ramp, optional hold, then an exponential (or linear) release over decay seconds."""
    out = []
    for i, x in enumerate(sig):
        t = i / SR
        if t < attack:
            g = t / attack
        elif t < attack + hold:
            g = 1.0
        else:
            u = t - attack - hold
            g = math.exp(-u / (decay / 4)) if curve == 'exp' else max(0.0, 1 - u / decay)
        out.append(x * g)
    return out


def swell(sig, peak_at=0.5):
    """Rises to full at peak_at (fraction of length) then falls: whooshes."""
    L = len(sig)
    out = []
    for i, x in enumerate(sig):
        u = i / L
        g = (u / peak_at) if u < peak_at else (1 - (u - peak_at) / (1 - peak_at))
        out.append(x * math.sin(max(0.0, g) * math.pi / 2) ** 2)
    return out


def amp(sig, g):
    f = _fn(g)
    return [x * f(i / SR) for i, x in enumerate(sig)]


def drive(sig, k=2.0):
    norm = math.tanh(k)
    return [math.tanh(k * x) / norm for x in sig]


def fade(sig, fade_in=0.002, fade_out=0.01):
    out = list(sig)
    a, b = n(fade_in), n(fade_out)
    for i in range(min(a, len(out))):
        out[i] *= i / a
    for i in range(min(b, len(out))):
        out[-1 - i] *= i / b
    return out


def tremolo(sig, rate, depth=0.5):
    r = _fn(rate)
    out = []
    ph = 0.0
    for i, x in enumerate(sig):
        ph += r(i / SR) / SR
        out.append(x * (1 - depth * 0.5 * (1 + math.sin(TAU * ph))))
    return out


#-----Combining-----
def mix(*parts):
    """mix((sig, gain, offset_seconds), ...) or plain signals."""
    norm = []
    for p in parts:
        if isinstance(p, tuple):
            sig, g = p[0], p[1]
            off = p[2] if len(p) > 2 else 0.0
        else:
            sig, g, off = p, 1.0, 0.0
        norm.append((sig, g, int(off * SR)))
    length = max(len(s) + o for s, _, o in norm)
    out = [0.0] * length
    for s, g, o in norm:
        for i, x in enumerate(s):
            out[o + i] += x * g
    return out


def echo(sig, delay=0.09, feedback=0.35, taps=4, damp=3000):
    """A few darkening repeats: stone rooms."""
    d = n(delay)
    out = list(sig) + [0.0] * (d * taps)
    tap = list(sig)
    for k in range(1, taps + 1):
        tap = lowpass(tap, damp)
        g = feedback ** k
        for i, x in enumerate(tap):
            out[i + d * k] += x * g
    return out


def seamless(sig, xfade):
    """Makes a loop: the tail is cross-faded into the head, the result is len - xfade long."""
    x = n(xfade)
    body = sig[:len(sig) - x]
    tail = sig[len(sig) - x:]
    for i in range(x):
        u = i / x
        body[i] = body[i] * math.sqrt(u) + tail[i] * math.sqrt(1 - u)
    return body


def normalize(sig, peak=0.9):
    m = max(1e-9, max(abs(x) for x in sig))
    return [x * peak / m for x in sig]


def remove_dc(sig):
    return highpass(sig, 20)


def silence(dur):
    return [0.0] * n(dur)


def write(path, sig, peak=0.9, trim=True):
    # loops keep their seam: they remove DC themselves before seamless()
    sig = normalize(remove_dc(sig) if trim else sig, peak)
    if trim:
        # drop the inaudible tail
        end = len(sig)
        while end > 1 and abs(sig[end - 1]) < 0.0015:
            end -= 1
        sig = fade(sig[:end], 0.0005, min(0.01, end / SR / 4))
    with wave.open(path, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(b''.join(struct.pack('<h', int(max(-1, min(1, x)) * 32767)) for x in sig))
    return sig
