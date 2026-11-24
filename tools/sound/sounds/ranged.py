"""Bows, crossbows, thrown weapons: heavy dark wood and iron, thick strings, brutal impacts."""
import math
import random

from synth import *  # noqa: F401,F403  (the DSP toolbox: tone, noise, reverb, cavern, lowpass2, sub, toll, ...)
from common import *  # noqa: F401,F403  (sound, loop, whoosh, thump, squelch, chord, shimmer, impact)


def pitch(v):
    """Per-variant pitch factor: the same sound, slightly different each time."""
    return (1.0, 0.94, 1.07)[v % 3]


def low_air(dur, lo, hi, peak=0.4, seed=None, q=1.0):
    """A heavy low whoosh: pink noise swept through a band, then darkened with a 12 dB low-pass."""
    return lowpass2(whoosh(dur, lo, hi, q, peak, seed, 'pink'), hi * 1.4)


@sound
def bow_draw():
    """A thick bowstring creaking taut: slow saw growl with random wood groans, rising string tone, rope rub."""
    dur = 0.9
    rng = random.Random(60)
    groan = lowpass2(stack(dur, sweep(45, 95, dur), 'saw', 3, 18, seed=60), 420, 2.0)
    groan = amp(groan, lambda t: 0.3 + 0.7 * (0.5 + 0.5 * math.sin(TAU * (6 + 5 * t) * t)) * (t / dur))
    string = amp(lowpass2(tone(dur, sweep(70, 150, dur), 'tri'), 500), lambda t: (t / dur) ** 1.5)
    rub = lowpass2(bandpass(noise(dur, 'pink', seed=61), sweep(250, 700, dur), 2), 1200)
    creaks = amp(lowpass2(clicks(dur, 16, 0.012, (180, 500), seed=rng.randrange(1000)), 900),
                 lambda t: 0.3 + 0.7 * t / dur)
    sig = saturate(mix((groan, 0.9), (string, 0.5), (rub, 0.35), (creaks, 0.6)), 1.8)
    return amp(sig, lambda t: min(1.0, t / 0.1))


@sound(variants=3)
def bow_release(v):
    """A heavy bowstring snapping: low plucked string with a sub thump, wood knock and a dark whoosh."""
    p = pitch(v)
    twang = lowpass2(env(pluck(0.45, 80 * p, 0.994, 0.3, seed=61 + v), 0.001, 0.4), 700)
    thud = mix((sub(0.25, 90 * p, 40, 0.2), 0.8), (thump(0.1, 220 * p, 100, 0.08), 0.6))
    knock = lowpass2(env(noise(0.04, seed=65 + v), 0.0005, 0.03), 1500)
    air = low_air(0.2, 200, 900 * p, 0.25, 62 + v)
    return cavern(saturate(mix((twang, 1.0), (thud, 0.8), (knock, 0.5), (air, 0.5)), 2.0), 0.15, 0.7)


@sound(variants=3)
def arrow_thunk(v):
    """An arrow burying itself: heavy flesh and wood thud, wet low squelch, shaft quiver."""
    p = pitch(v)
    body = mix((thump(0.16, 200 * p, 70, 0.14), 1.0), (sub(0.2, 80, 38, 0.15), 0.7))
    flesh = lowpass2(squelch(0.08, 700 * p, 300, 2, seed=63 + v), 1000)
    quiver = lowpass2(env(pluck(0.25, 170 * p, 0.985, 0.2, seed=64 + v), 0.001, 0.2), 800)
    return cavern(saturate(mix((body, 1.0), (flesh, 0.6), (quiver, 0.35)), 2.0), 0.15, 0.7)


@sound(variants=3)
def bone_shatter(v):
    """Bone snapping apart: dry mid cracks and fragments over a hollow thud."""
    p = pitch(v)
    cracks = lowpass2(clicks(0.18, 60, 0.007, (700 * p, 2800 * p), seed=65 + v), 4000)
    cracks = amp(cracks, lambda t: math.exp(-t / 0.07))
    body = mix((thump(0.1, 260 * p, 100, 0.08), 0.8), (sub(0.15, 90, 40, 0.1), 0.5))
    return cavern(saturate(mix((cracks, 1.0), (body, 0.8)), 1.8), 0.2, 0.8)


@loop
def crank():
    """A loop of iron ratchet ticks (0.5 s, six ticks, nothing rings across the seam): dull clacks with a low knock."""
    def tick(f, s):
        clack = lowpass2(bell(0.05, f, ((1, 1.0), (2.4, 0.5)), 0.012), 2500)
        grit = lowpass2(env(noise(0.012, seed=s), 0.0002, 0.008), 1800)
        return saturate(mix((clack, 1.0), (grit, 0.6), (thump(0.05, 140, 80, 0.03), 0.7)), 1.5)
    ticks = [(tick(520 + (k % 2) * 140, 66 + k), 1.0, k / 12) for k in range(6)]
    return mix(*ticks, (silence(0.5), 0))


@sound
def crossbow_loaded():
    """The bolt locking home: two heavy iron latch clacks and a low knock."""
    def clack(f, s):
        ring = lowpass2(bell(0.12, f, ((1, 1.0), (2.3, 0.6), (3.9, 0.2)), 0.05), 2500)
        grit = lowpass2(env(noise(0.015, seed=s), 0.0002, 0.01), 2000)
        return mix((ring, 1.0), (grit, 0.7))
    sig = mix((clack(420, 66), 0.8), (clack(290, 67), 1.0, 0.08), (thump(0.12, 160, 70, 0.1), 0.8, 0.08),
              (sub(0.2, 80, 40, 0.15), 0.5, 0.08))
    return cavern(saturate(sig, 1.8), 0.2, 0.8)


@sound(variants=3)
def crossbow_fire(v):
    """A crossbow slamming off: thick low string thwack, iron snap, sub kick and a dark rush of air."""
    p = pitch(v)
    thwack = lowpass2(env(pluck(0.3, 62 * p, 0.99, 0.3, seed=68 + v), 0.0005, 0.25), 600)
    snap = lowpass2(env(noise(0.025, seed=69 + v), 0.0002, 0.015), 2500)
    kick = mix((sub(0.3, 100, 36, 0.25), 1.0), (thump(0.12, 240 * p, 90, 0.1), 0.9))
    air = low_air(0.2, 250, 1100 * p, 0.2, 70 + v)
    sig = saturate(mix((thwack, 1.0), (snap, 0.6), (kick, 1.0), (air, 0.4)), 2.2)
    return cavern(sig, 0.18, 0.8)


@sound(variants=3)
def throw(v):
    """A heavy weapon hurled: low pitch-swept whoosh, a second grainy air layer and a muscle thump."""
    p = pitch(v)
    air = low_air(0.34, 120 * p, 800 * p, 0.4, 71 + v, 1.2)
    grain = lowpass2(whoosh(0.3, 300, 1200, 2.0, 0.35, 75 + v, 'pink'), 1400)
    sig = saturate(mix((air, 1.0), (grain, 0.4), (thump(0.1, 130, 60, 0.07), 0.5)), 1.6)
    return cavern(sig, 0.12, 0.5)


@sound
def trident_stick():
    """A trident driving into flesh or stone: heavy thud with sub, wet squelch, long low ring of the shaft."""
    body = mix((thump(0.2, 220, 70, 0.16), 1.0), (sub(0.35, 85, 36, 0.3), 0.9))
    ring = lowpass2(env(pluck(0.6, 110, 0.996, 0.3, seed=72), 0.001, 0.5), 700)
    flesh = lowpass2(squelch(0.08, 800, 300, 2, seed=73), 1100)
    sig = saturate(mix((body, 1.0), (ring, 0.6), (flesh, 0.5), (bell(0.5, 190, ((1, 1.0), (2.76, 0.3)), 0.15), 0.25)), 2.0)
    return cavern(sig, 0.22, 1.0)


@sound
def recall():
    """The weapon flying back: a reversed rising rush, a rising dark tone and a heavy caught-in-hand thump."""
    rush = lowpass2(whoosh(0.5, 100, 900, 1.2, 0.85, 74, 'pink'), 1300)
    rise = env(tone(0.5, sweep(70, 220, 0.5)), 0.15, 0.5, curve='lin')
    rise = saturate(lowpass2(rise, 600), 2.0)
    catch = mix((thump(0.15, 200, 80, 0.12), 1.0), (sub(0.25, 80, 38, 0.2), 0.8), (toll(0.5, 98, 0.35), 0.3))
    return cavern(mix((rush, 0.9), (rise, 0.4), (catch, 1.0, 0.4)), 0.22, 0.9)


@sound
def pickup():
    """Taking an item: a low muted toll with a minor third under it and a soft body thump."""
    bellsig = lowpass2(mix((toll(0.7, 130, 0.45), 1.0), (toll(0.7, 155, 0.3), 0.4, 0.03)), 1800)
    body = mix((thump(0.15, 170, 80, 0.1), 0.7), (sub(0.2, 80, 42, 0.15), 0.5))
    return cavern(saturate(mix((bellsig, 1.0), (body, 0.8)), 1.6), 0.25, 1.6)


@sound(variants=3)
def enemy_throw(v):
    """An enemy hurling something: low breathy whoosh with a grunt of weight."""
    p = pitch(v)
    air = low_air(0.24, 120 * p, 700 * p, 0.4, 76 + v, 1.3)
    sig = mix((air, 1.0), (thump(0.08, 120, 60, 0.05), 0.4))
    return cavern(saturate(sig, 1.5), 0.12, 0.5)
