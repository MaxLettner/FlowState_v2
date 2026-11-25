"""The Pit Lord, the Void Matriarch, the Tar Hydra and their arenas: enormous, layered, long cavern tails."""
import math
import random

from synth import (
    SR, TAU, amp, bandpass, cavern, clicks, env, formant, highpass2, lowpass2, mix, n, noise, normalize,
    pluck, resample, reverse, saturate, seamless, stack, sub, sweep, swell, toll, tone, tremolo)
from common import impact, loop, sound, squelch, thump, whoosh


#-----Helpers-----
def growl(dur, f0, f1, shift, vowels=('a', 'o'), seed=None, voices=6, cents=60, rate=34, depth=0.5):
    """A creature's throat: a detuned saw stack, chopped by a growl tremolo, morphing between two vowels."""
    pitch = sweep(f0, f1, dur)
    src = stack(dur, lambda t: pitch(t) * (1 + 0.012 * math.sin(TAU * 5.3 * t) + 0.006 * math.sin(TAU * 11.7 * t)),
                'saw', voices, cents, seed)
    src = tremolo(src, rate, depth)
    a = formant(src, vowels[0], shift)
    b = formant(src, vowels[1], shift)
    return normalize(mix((amp(a, lambda t: 1 - min(1.0, t / dur)), 1.0), (amp(b, lambda t: min(1.0, t / dur)), 1.0)))


def shaped(sig, attack, hold, tau):
    """Fade in over attack, stay until hold, then fall exponentially with time constant tau."""
    return amp(sig, lambda t: min(1.0, t / attack) * math.exp(-max(0.0, t - hold) / tau))


def clatter(dur, rate, freq, seed, accel=0.0):
    """Chitin and stone debris: dark, soft-edged ticks."""
    return lowpass2(clicks(dur, rate, 0.005, freq, seed=seed, accel=accel), 2400)


#-----Pit Lord-----
@sound
def boss_roar():
    """The Pit Lord's roar: two stacked saw throats (one resampled an octave down) with a hellfire breath and sub."""
    dur = 1.9
    throat = growl(dur, 88, 58, 0.8, ('a', 'o'), seed=401)
    big = resample(growl(dur * 0.62, 118, 82, 0.8, ('o', 'a'), seed=402, rate=40, depth=0.6), 0.62)
    breath = amp(bandpass(noise(dur, 'pink', seed=403), lambda t: 500 + 700 * math.sin(math.pi * min(1.0, t / dur)), 1.0),
                 lambda t: min(1.0, t / 0.3))
    fire = highpass2(lowpass2(noise(dur, 'brown', seed=404), 1000), 110)
    low = sub(dur, 62, 36, dur * 0.9, 0.08)
    sig = saturate(mix((throat, 1.0), (big, 0.8), (normalize(breath), 0.45), (fire, 0.5), (low, 0.15)), 2.6)
    sig = shaped(lowpass2(sig, 3200), 0.1, 1.0, 0.4)
    return cavern(sig, 0.45, 3.6)


@sound
def gate_slam():
    """A colossal iron gate slamming in hellfire: sub boom, iron toll and chains, a rolling furnace roar, debris."""
    dur = 1.6
    boom = impact(dur, weight=0.9, body=130, crack=0.9, seed=410, room=0)
    iron = toll(2.2, 92, 1.1)
    chains = amp(clatter(1.2, 70, (300, 1400), 411), lambda t: math.exp(-t / 0.35))
    fire = shaped(lowpass2(noise(1.6, 'pink', seed=412), lambda t: 1800 * math.exp(-t / 0.5) + 350), 0.02, 0.1, 0.5)
    rubble = amp(clatter(1.4, 30, (200, 900), 413), lambda t: math.exp(-t / 0.5))
    sig = saturate(mix((boom, 1.0), (iron, 0.5, 0.01), (chains, 0.5, 0.03), (fire, 1.4, 0.02), (rubble, 0.6, 0.2)), 1.8)
    return cavern(lowpass2(sig, 4000), 0.55, 4.2)


#-----Void Matriarch-----
@sound
def matriarch_screech():
    """A chitinous void shriek: a raspy 'e'-'i' throat over a hollow void drone, with a chittering clatter."""
    dur = 1.5
    rasp = growl(dur, 520, 330, 0.9, ('e', 'i'), seed=420, voices=5, cents=90, rate=47, depth=0.8)
    drone = growl(dur, 74, 50, 0.8, ('u', 'o'), seed=421, rate=9, depth=0.4)
    chitter = tremolo(clatter(dur, 55, (600, 2200), 422), 31, 0.8)
    air = amp(bandpass(noise(dur, 'pink', seed=423), sweep(900, 400, dur), 1.5), lambda t: min(1.0, t / 0.2))
    low = sub(dur, 55, 34, dur * 0.8)
    sig = saturate(mix((rasp, 0.7), (drone, 0.7), (chitter, 0.5), (normalize(air), 0.25), (low, 0.25)), 2.2)
    sig = shaped(lowpass2(sig, 3800), 0.08, 0.7, 0.35)
    return cavern(sig, 0.4, 3.2)


@sound(variants=3)
def thread_twang(v):
    """A taut strand plucked: a low Karplus-Strong string with a bent sine under it and a short hollow tail."""
    dur = 0.8
    f = 92 * (1.0, 1.06, 0.94)[v]
    string = pluck(dur, f, 0.997, 0.45, seed=430 + v)
    bend = env(tone(dur, sweep(f * 2.2, f * 1.5, dur)), 0.002, 0.5)
    body = sub(0.5, f * 0.8, f * 0.5, 0.4)
    sig = saturate(mix((string, 0.9), (bend, 0.3), (body, 0.35)), 1.6)
    return cavern(lowpass2(sig, 2400), 0.2, 2.2)


@sound(variants=3)
def thread_snap(v):
    """A strand snapping: a dark crack, a whipping release and a falling saw, with a dull body thud."""
    crack = env(highpass2(noise(0.07, seed=440 + v), 1200), 0.0008, 0.05)
    whip = whoosh(0.35, 400, 2600, q=2.0, peak=0.3, seed=441 + v)
    fall = env(lowpass2(tone(0.45, sweep(300, 62, 0.45), 'saw'), 1400), 0.001, 0.3)
    body = impact(0.4, weight=0.6, body=105 - 6 * v, crack=0.2, seed=442 + v, room=0)
    sig = mix((crack, 0.9), (whip, 0.5, 0.01), (fall, 0.6, 0.01), (body, 0.7))
    return cavern(sig, 0.25, 2.4)


@sound(variants=3)
def egg_hatch(v):
    """A silk sac tearing open: a wet rip, a cracking shell, a hollow thud and skittering legs."""
    rip = squelch(0.3, 1500, 350, 2.5, seed=450 + v)
    shell = amp(clatter(0.35, 90, (500, 1800), 451 + v), lambda t: math.exp(-t / 0.12))
    thud = impact(0.4, weight=0.5, body=95, crack=0.1, seed=452 + v, room=0)
    skitter = amp(clatter(0.7, 55, (400, 1500), 453 + v), lambda t: math.exp(-t / 0.35))
    sig = mix((rip, 1.0), (shell, 0.7), (thud, 0.8, 0.02), (skitter, 0.5, 0.15))
    return cavern(lowpass2(sig, 3200), 0.3, 2.5)


@sound
def silk_spin():
    """Silk paying out fast: a rising friction hiss over a low creaking whirr, dark and hollow."""
    dur = 0.75
    hiss = lowpass2(bandpass(noise(dur, seed=460), sweep(900, 2600, dur), 2.5), 3000)
    whirr = tremolo(lowpass2(tone(dur, sweep(90, 160, dur), 'saw'), 700), 38, 0.85)
    hollow = amp(bandpass(noise(dur, 'pink', seed=461), sweep(260, 420, dur), 4.0), lambda t: min(1.0, t / 0.2))
    sig = env(mix((normalize(hiss), 0.5), (whirr, 0.5), (normalize(hollow), 0.5)), 0.08, dur)
    return cavern(sig, 0.2, 2.2)


@sound
def lights_out():
    """The arena going dark: an inward choir swell and swallowed air, then a sub boom and a long falling drone."""
    dur = 2.8
    choir = amp(formant(stack(dur, sweep(58, 52, dur), 'saw', 6, 50, seed=470), 'o', 0.85),
                lambda t: min(1.0, (t / 1.3) ** 2))
    air = lowpass2(noise(dur, 'pink', seed=471), sweep(2800, 140, dur))
    boom = mix((sub(1.6, 70, 28, 1.4), 1.0), (lowpass2(noise(1.2, 'brown', seed=472), 140), 0.8), (impact(1.0, 0.8, 100, 0.1, 473, 0), 0.5))
    fall = lowpass2(tone(dur, sweep(240, 40, dur), 'saw'), 700)
    sig = saturate(mix((normalize(choir), 1.0), (normalize(air), 0.5), (boom, 0.55, 1.2), (fall, 0.7)), 1.8)
    sig = shaped(sig, 0.5, 1.7, 0.45)
    return cavern(sig, 0.55, 4.5)


@sound
def acid_spit():
    """The Matriarch spitting acid: a throaty hock and gurgle, a hollow slap and a hissing spray."""
    hock = squelch(0.25, 480, 150, 3.0, seed=480)
    gurgle = env(tremolo(lowpass2(noise(0.3, 'brown', seed=481), 650), 26, 0.7), 0.01, 0.26)
    throat = env(formant(stack(0.25, sweep(120, 70, 0.25), 'saw', 4, 80, seed=482), 'o', 0.8), 0.01, 0.2)
    thud = sub(0.3, 110, 50, 0.2)
    spray = env(lowpass2(bandpass(noise(0.5, seed=483), sweep(2200, 4200, 0.5), 2.0), 5000), 0.01, 0.38)
    sig = mix((gurgle, 0.7), (hock, 1.0, 0.06), (normalize(throat), 0.5), (thud, 0.25, 0.05), (spray, 0.5, 0.14))
    return cavern(sig, 0.3, 2.4)


@sound(variants=3)
def acid_sizzle(v):
    """Acid eating into stone: a fizzing hiss with thick low bubbling pops."""
    dur = 1.0
    hiss = env(highpass2(noise(dur, seed=490 + v), 2800), 0.015, dur * 0.85)
    fizz = tremolo(hiss, 17 + 3 * v, 0.4)
    bubbles = amp(clatter(dur, 34, (150, 700), 491 + v), lambda t: math.exp(-t / 0.55))
    wet = env(lowpass2(noise(dur, 'pink', seed=492 + v), 900), 0.02, dur * 0.8)
    sig = mix((fizz, 0.45), (bubbles, 0.9), (wet, 0.4))
    return cavern(sig, 0.2, 2.0)


#-----Tar Hydra-----
@sound
def hydra_roar():
    """A Tar Hydra head roaring: a deep 'u'-'o' bellow with a tar gurgle in the throat and a molten breath."""
    dur = 1.5
    voice = growl(dur, 72, 50, 0.7, ('u', 'o'), seed=500, voices=6, cents=70, rate=22, depth=0.55)
    big = resample(growl(dur * 0.65, 105, 78, 0.75, ('o', 'u'), seed=501, rate=18, depth=0.6), 0.65)
    gurgle = tremolo(bandpass(noise(dur, 'brown', seed=502), lambda t: 280 + 200 * math.sin(TAU * 3 * t), 2.5), 19, 0.85)
    breath = lowpass2(noise(dur, 'pink', seed=503), 1100)
    low = sub(dur, 56, 34, dur * 0.9, 0.08)
    sig = saturate(mix((voice, 1.0), (big, 0.8), (normalize(gurgle), 0.8), (highpass2(breath, 100), 0.6), (low, 0.15)), 2.6)
    sig = shaped(lowpass2(sig, 2400), 0.15, 0.8, 0.35)
    return cavern(sig, 0.45, 3.4)


@sound
def tar_spit():
    """The spitter hocking a glob of burning tar: a thick gulp, a molten whoosh and a low furnace flare."""
    gulp = squelch(0.25, 300, 90, 3.5, seed=510)
    body = thump(0.3, 110, 48, 0.25)
    flare = whoosh(0.55, 200, 1300, q=1.1, peak=0.35, seed=511)
    roar = env(lowpass2(noise(0.5, 'brown', seed=512), 800), 0.03, 0.45)
    pops = amp(clatter(0.5, 40, (200, 900), 513), lambda t: math.exp(-t / 0.3))
    sig = saturate(mix((gulp, 1.0), (body, 0.6, 0.02), (flare, 0.6, 0.06), (roar, 0.6, 0.05), (pops, 0.4, 0.1)), 1.6)
    return cavern(sig, 0.25, 2.4)


@sound(variants=3)
def tar_splash(v):
    """A glob of tar landing: a heavy wet slap, a sub thump, a thick churn and a sizzle of burning bubbles."""
    dur = 1.0
    slap = squelch(0.35, 480 - 40 * v, 80, 2.0, seed=520 + v)
    thud = impact(0.7, weight=0.5, body=110 + 8 * v, crack=0.1, seed=521 + v, room=0)
    churn = env(lowpass2(noise(dur, 'pink', seed=522 + v), lambda t: 1100 * math.exp(-t / 0.4) + 300), 0.01, 0.8)
    hiss = env(highpass2(noise(0.8, seed=523 + v), 2600), 0.01, 0.6)
    pops = amp(clatter(0.8, 25, (180, 900), 524 + v), lambda t: math.exp(-t / 0.4))
    sig = mix((slap, 0.9), (thud, 0.8), (churn, 1.0), (hiss, 0.15, 0.04), (pops, 0.5, 0.08))
    return cavern(sig, 0.3, 2.6)


@sound
def tar_wave():
    """A wave of tar rolling over the lake: a long churning wash, thick glugs and a deep surge under it."""
    dur = 2.0
    wash = bandpass(noise(dur, 'pink', seed=530), lambda t: 300 + 900 * math.sin(math.pi * min(1.0, t / dur)), 0.9)
    churn = tremolo(highpass2(lowpass2(noise(dur, 'brown', seed=531), 800), 90), 7, 0.7)
    surge = highpass2(lowpass2(noise(dur, 'brown', seed=532), 300), 70)
    glugs = amp(clatter(dur, 14, (150, 600), 533, accel=0.5), lambda t: math.sin(math.pi * t / dur))
    low = sub(dur, 70, 34, dur * 0.9, 0.3)
    sig = saturate(mix((normalize(wash), 1.2), (churn, 0.8), (surge, 0.6), (glugs, 0.7), (low, 0.12)), 1.7)
    sig = shaped(sig, 0.4, 1.2, 0.4)
    return cavern(lowpass2(sig, 2200), 0.4, 3.4)


#-----Arena loops-----
# the periodic layers (drums, drones, events) are built circularly, so they wrap exactly; only the noise layers
# go through seamless(), and the two are added after the crossfade so no layer ever doubles up in it.
def whole(freq, length):
    """The nearest frequency that fits a whole number of cycles into the loop."""
    return round(freq * length) / length


def put(buf, sig, at):
    """Adds sig to a circular buffer at time at, wrapping past the end."""
    size = len(buf)
    start = int(at * SR)
    for i, x in enumerate(sig):
        buf[(start + i) % size] += x


def cyclic(fn, buf, lead=2.0):
    """Runs a filter or reverb over a circular buffer without a seam: it sees the end of the loop first."""
    k = n(lead)
    return fn(buf[-k:] + buf)[k:k + len(buf)]


def noise_bed(sig, xf):
    return seamless(highpass2(sig, 25), xf)


@loop
def amb_lord():
    """The Pit Lord's arena: a distant war-drum heartbeat, a hellfire roar, a minor choir drone under a long hall."""
    length, xf = 15.0, 1.0
    dur = length + xf
    # war drum: a double beat every 2.5 s, soft-edged and far away
    drums = [0.0] * n(length)
    for k in range(6):
        for at, weight, f in ((0.0, 1.0, 82), (0.46, 0.65, 70)):
            hit = mix((sub(1.4, f * 0.8, 40, 0.8, 0.012), 0.6), (env(tone(0.6, sweep(f * 1.9, f * 1.2, 0.2)), 0.012, 0.35), 0.9),
                      (lowpass2(env(noise(0.4, 'brown', seed=600 + k), 0.015, 0.3), 200), 0.5))
            put(drums, [x * weight for x in saturate(hit, 1.8)], k * 2.5 + at)
    drums = cyclic(lambda s: cavern(lowpass2(s, 400), 0.6, 3.5), drums, 4.0)
    # choir: stacked saws on a minor chord, detuned by whole beats per loop, through a dark 'o'
    voices = [(whole(f, length) + d / length) for f in (55, 65.4, 82.5, 110) for d in (-1, 0, 1)]
    choir = cyclic(lambda s: formant(s, 'o', 0.85), [sum(x) / len(voices) for x in zip(
        *[tone(length, f, 'saw') for f in voices])], 1.0)
    choir = amp(normalize(choir), lambda t: 0.6 + 0.4 * math.sin(TAU * t / (length / 3)))
    # hellfire: a broad furnace roar breathing slowly, with a few low embers
    fire = lowpass2(noise(dur, 'pink', seed=610), lambda t: 520 + 220 * math.sin(TAU * t / 5.0) + 90 * math.sin(TAU * t / 3.0))
    fire = amp(fire, lambda t: 0.6 + 0.3 * math.sin(TAU * t / 7.5 + 1) + 0.1 * math.sin(TAU * t / 3.0))
    embers = lowpass2(clicks(dur, 7, 0.006, (400, 1200), seed=611), 1200)
    rumble = lowpass2(noise(dur, 'brown', seed=612), 90)
    fire = highpass2(fire, 80)
    bed = noise_bed(mix((fire, 1.2), (embers, 0.12), (rumble, 0.25)), xf)
    return mix((drums, 0.8), (choir, 0.9), (bed, 0.9))


@loop
def amb_matriarch():
    """The Matriarch's chasm: hollow wind from below, faint skittering, a dissonant drone and creaking silk."""
    length, xf = 14.0, 1.0
    dur = length + xf
    # hollow wind: a resonant band sweeping slowly, like a breath across a huge opening
    wind = bandpass(noise(dur, 'pink', seed=620), lambda t: 230 + 110 * math.sin(TAU * t / 7.0) + 60 * math.sin(TAU * t / 3.5), 3.0)
    wind = amp(normalize(wind), lambda t: 0.55 + 0.35 * math.sin(TAU * t / 14.0 + 0.5) + 0.1 * math.sin(TAU * t / 2.8))
    air = lowpass2(noise(dur, 'pink', seed=621), 500)
    skitter = lowpass2(clicks(dur, 9, 0.004, (500, 1700), jitter=0.8, seed=622, accel=0.4), 1800)
    skitter = amp(skitter, lambda t: max(0.0, math.sin(TAU * t / 3.5)) ** 3)
    bed = noise_bed(mix((wind, 0.9), (air, 0.4), (skitter, 0.12)), xf)
    # dissonant drone: a minor second and a tritone, each pair beating slowly
    notes = [whole(f, length) + d / length for f, d in ((46, 0), (46, 1), (48.8, 0), (65.2, 0), (65.2, -1), (97.5, 0))]
    drone = [sum(x) / len(notes) for x in zip(*[tone(length, f, 'saw') for f in notes])]
    drone = cyclic(lambda s: lowpass2(s, 380), drone, 1.0)
    drone = amp(normalize(drone), lambda t: 0.65 + 0.35 * math.sin(TAU * t / (length / 2)))
    # creaking silk: slow strained glides with a ratcheting tremolo
    creaks = [0.0] * n(length)
    for at, f0, f1, dur_ in ((1.5, 150, 205, 1.3), (6.0, 240, 170, 1.7), (10.2, 120, 180, 1.1)):
        c = tremolo(lowpass2(tone(dur_, sweep(f0, f1, dur_), 'saw'), 600), 14, 0.9)
        put(creaks, [x * math.sin(math.pi * i / len(c)) ** 2 for i, x in enumerate(c)], at)
    creaks = cyclic(lambda s: cavern(s, 0.5, 3.0), creaks, 3.0)
    return mix((bed, 1.0), (drone, 0.55), (creaks, 0.45))


@loop
def amb_hydra():
    """The tar lake: slow churning tar, glooping bubbles, a deep rumble and distant crackling fire."""
    length, xf = 16.0, 1.0
    dur = length + xf
    # churning tar: brown noise whose weight rolls in slow waves
    churn = lowpass2(noise(dur, 'brown', seed=630), lambda t: 420 + 200 * math.sin(TAU * t / 8.0))
    churn = amp(churn, lambda t: 0.6 + 0.3 * math.sin(TAU * t / 5.333) + 0.1 * math.sin(TAU * t / 3.2))
    rumble = lowpass2(noise(dur, 'brown', seed=631), 70)
    fire = lowpass2(clicks(dur, 16, 0.005, (500, 1400), seed=632), 1500)
    fire = amp(fire, lambda t: 0.5 + 0.5 * math.sin(TAU * t / 4.0) ** 2)
    bed = noise_bed(mix((highpass2(churn, 60), 1.3), (rumble, 0.3), (fire, 0.1)), xf)
    # bubbles: soft low gloops rising slightly, at fixed seeded times around the loop
    rng = random.Random(633)
    bubbles = [0.0] * n(length)
    for _ in range(15):
        f = rng.uniform(70, 190)
        dur_ = rng.uniform(0.22, 0.5)
        gloop = env(tone(dur_, sweep(f, f * rng.uniform(1.3, 1.9), dur_ * 0.7)), 0.015, dur_)
        put(bubbles, [x * rng.uniform(0.3, 0.9) for x in gloop], rng.uniform(0, length))
    bubbles = cyclic(lambda s: cavern(lowpass2(s, 600), 0.35, 2.5), bubbles, 3.0)
    # deep rumble tones: two sines a beat apart
    deep = [a + b for a, b in zip(tone(length, whole(36, length)), tone(length, whole(36, length) + 2 / length))]
    deep = amp(deep, lambda t: 0.6 + 0.4 * math.sin(TAU * t / (length / 2)))
    return mix((bed, 1.0), (bubbles, 0.9), (deep, 0.1))


#-----Hollow Ember-----
def void_voice(dur, f0, f1, seed, vowels=('o', 'u')):
    """The double's throat: a hollowed, detuned slime growl with a ring of void resonance."""
    return growl(dur, f0, f1, 0.75, vowels, seed=seed, voices=5, cents=90, rate=15, depth=0.45)


@sound
def hollow_rise():
    """The reflection climbing out of the black glass: a glassy groan, embers going out one by one, the crown igniting."""
    dur = 2.5
    glass = tremolo(bandpass(noise(dur, 'brown', seed=700), lambda t: 140 + 220 * t / dur, 2.0), 9, 0.7)
    glass = amp(normalize(glass), lambda t: math.sin(math.pi * t / dur) ** 1.5)
    inward = reverse(cavern(env(void_voice(1.2, 62, 48, 701), 0.01, 0.5), 0.3, 1.6))
    parts = [(normalize(glass), 0.9), (inward, 0.5, 0.5)]
    for i in range(5):
        # an ember going out: a short dull pop and a downward hiss
        snuff = mix((env(thump(0.3, 150 - 12 * i, 60, 0.12), 0.003, 0.15), 0.6),
                    (env(lowpass2(noise(0.3, 'pink', seed=710 + i), lambda t: 900 * math.exp(-t / 0.08) + 200), 0.002, 0.18), 0.5))
        parts.append((snuff, 0.7, 0.2 + 0.28 * i))
    low = sub(dur, 38, 30, dur * 0.8, 0.9)
    crown = shaped(lowpass2(growl(1.0, 78, 96, 0.7, ('o', 'a'), seed=720, rate=20), 1800), 0.5, 0.6, 0.3)
    flame = shaped(lowpass2(noise(1.0, 'pink', seed=721), 1200), 0.45, 0.55, 0.3)
    parts += [(low, 0.2), (crown, 0.9, 1.5), (flame, 0.5, 1.5)]
    sig = saturate(mix(*parts), 2.0)
    return cavern(lowpass2(sig, 2200), 0.45, 3.2)


@sound
def hollow_roar():
    """The Hollow Ember's cry: a slime squelch hollowed out and stretched into a distorted void bellow."""
    dur = 1.6
    voice = void_voice(dur, 85, 58, 730)
    echo_voice = resample(void_voice(dur * 0.8, 120, 80, 731, ('u', 'o')), 0.7)
    squelch_ = squelch(0.4, 380, 90, 3.0, seed=732)
    hollow = bandpass(noise(dur, 'pink', seed=733), lambda t: 330 - 120 * t / dur, 6.0)
    hollow = tremolo(normalize(hollow), 7, 0.6)
    low = sub(dur, 54, 34, dur * 0.9, 0.06)
    sig = saturate(mix((voice, 1.0), (echo_voice, 0.6, 0.12), (squelch_, 0.7), (hollow, 0.55, 0.1), (low, 0.14)), 2.6)
    sig = shaped(lowpass2(sig, 2300), 0.1, 0.7, 0.35)
    return cavern(sig, 0.45, 3.4)


@sound
def hollow_steal():
    """An ember shard torn off the player: a wet rip, a cracking tear and a dull violet hum sucked away."""
    dur = 0.9
    rip = whoosh(0.35, 1500, 250, q=1.0, peak=0.4, seed=740, color='pink')
    tear = env(lowpass2(noise(0.3, seed=741), 2500), 0.002, 0.1)
    wet = squelch(0.3, 260, 80, 3.0, seed=742)
    thud = impact(0.5, weight=0.5, body=105, crack=0.15, seed=743, room=0)
    suck = reverse(env(tone(0.5, sweep(320, 140, 0.5)), 0.01, 0.35))
    hum = env(lowpass2(tone(dur, sweep(110, 82, dur), 'saw'), 500), 0.05, 0.4)
    sig = saturate(mix((rip, 0.8), (tear, 0.4), (wet, 0.8, 0.05), (thud, 0.8, 0.08), (suck, 0.5, 0.25), (hum, 0.5, 0.1)), 1.8)
    return cavern(lowpass2(sig, 3000), 0.3, 2.2)


@sound
def hollow_shard_pickup():
    """The player takes an ember shard back: a warm low toll, a rising minor-third glow and an ember settling."""
    dur = 1.4
    t1 = toll(dur, 196, 2.2)
    t2 = toll(dur, 233, 1.8)
    glow = swell(lowpass2(tone(1.0, sweep(180, 235, 1.0), 'saw'), 700), 0.55)
    warm = formant(stack(1.0, sweep(98, 117, 1.0), 'saw', 4, 20, 750), 'o', 0.9)
    crackle = lowpass2(amp(clicks(0.8, 18, 0.005, (400, 1100), seed=751), lambda t: math.exp(-t / 0.4)), 1300)
    thud = thump(0.4, 100, 50, 0.2)
    sig = saturate(mix((t1, 0.8), (t2, 0.45, 0.09), (glow, 0.4), (normalize(warm), 0.35), (crackle, 0.3, 0.05), (thud, 0.5)), 1.5)
    return cavern(lowpass2(sig, 2400), 0.35, 2.6)


@sound
def hollow_flare():
    """The hollow flares open: an inward gasp, then a hollow violet boom with a ringing, wide void pulse."""
    dur = 1.3
    gasp = amp(reverse(lowpass2(noise(0.45, 'pink', seed=760), lambda t: 200 + 1100 * t / 0.45)), lambda t: 1.0)
    boom = impact(1.0, weight=1.0, body=90, crack=0.2, seed=761, room=0)
    ring = mix((toll(dur, 147, 2.0), 0.6), (toll(dur, 208, 1.6), 0.35))
    pulse = tremolo(env(lowpass2(tone(dur, sweep(110, 70, dur), 'saw'), 600), 0.02, 0.5), 6, 0.6)
    sig = saturate(mix((gasp, 0.7), (boom, 1.0, 0.4), (ring, 0.6, 0.4), (pulse, 0.5, 0.4)), 2.0)
    return cavern(lowpass2(sig, 2400), 0.45, 3.0)


@sound
def hollow_echo():
    """An echo ghost appears: a hollow whisper pulled in from the void, a soft thud and a faint delayed copy."""
    dur = 1.2
    whisper = reverse(bandpass(noise(0.6, 'pink', seed=770), lambda t: 250 + 500 * t / 0.6, 3.0))
    voice = reverse(env(void_voice(0.7, 90, 66, 771), 0.01, 0.5))
    pop = impact(0.5, weight=0.4, body=100, crack=0.1, seed=772, room=0)
    base = mix((amp(whisper, lambda t: 0.8), 0.7), (voice, 0.6), (pop, 0.8, 0.45))
    ghost = lowpass2(base, 700)
    sig = saturate(mix((base, 1.0), (ghost, 0.5, 0.28)), 1.6)
    return cavern(lowpass2(sig, 2200), 0.5, 3.0)


@sound
def hollow_swap():
    """It changes to another copied skill: a short hollow gulp and a low violet slide between two pitches."""
    dur = 0.7
    gulp = squelch(0.25, 300, 100, 3.0, seed=780)
    slide = env(lowpass2(tone(0.5, sweep(160, 105, 0.5), 'saw'), 800), 0.02, 0.25)
    ring = toll(dur, 123, 1.0)
    swish = whoosh(0.35, 400, 1200, q=1.2, peak=0.3, seed=781, color='pink')
    sig = saturate(mix((gulp, 0.8), (slide, 0.7, 0.05), (ring, 0.4, 0.12), (swish, 0.5, 0.05)), 1.6)
    return cavern(lowpass2(sig, 2400), 0.3, 2.0)


@sound
def hollow_death():
    """The double shrinks to a puddle: shards rushing back, a warm ember flaring in the hollow, a slow seep into glass."""
    dur = 2.5
    rush = reverse(lowpass2(noise(0.9, 'pink', seed=790), lambda t: 300 + 1500 * t / 0.9))
    shards = amp(clatter(1.0, 30, (300, 1400), 791, accel=0.8), lambda t: 1 - 0.7 * t)
    warm = swell(toll(1.2, 98, 1.5), 0.4)
    ember = shaped(lowpass2(growl(1.0, 70, 48, 0.7, ('o', 'u'), seed=792, rate=14), 1500), 0.1, 0.3, 0.3)
    shrink = resample(env(void_voice(1.6, 90, 70, 793), 0.01, 0.8), 0.6)
    seep = amp(lowpass2(noise(1.4, 'brown', seed=794), lambda t: 500 * math.exp(-t / 0.5) + 70), lambda t: math.exp(-t / 0.55))
    glass = env(thump(0.5, 70, 38, 0.3), 0.003, 0.3)
    low = sub(dur, 48, 30, dur * 0.7, 0.2)
    sig = saturate(mix((normalize(rush), 0.6), (shards, 0.6, 0.3), (warm, 0.5, 0.6), (ember, 0.8, 0.6),
                       (shrink, 0.7, 0.9), (normalize(seep), 0.7, 1.1), (glass, 0.8, 1.8), (low, 0.15, 0.5)), 1.8)
    return cavern(lowpass2(sig, 2200), 0.5, 3.6)


@loop
def amb_hollow():
    """The black glass arena: a hollow, glassy drone, faint reflected ember ticks and long cold resonances."""
    length, xf = 16.0, 1.0
    dur = length + xf
    # glass wind: a resonant band drifting slowly, like breath over a mirror
    wind = bandpass(noise(dur, 'pink', seed=800), lambda t: 200 + 90 * math.sin(TAU * t / 8.0) + 50 * math.sin(TAU * t / 3.2), 4.0)
    wind = amp(normalize(wind), lambda t: 0.5 + 0.4 * math.sin(TAU * t / 16.0 + 1) + 0.1 * math.sin(TAU * t / 4.0))
    rumble = lowpass2(noise(dur, 'brown', seed=801), 80)
    bed = noise_bed(mix((wind, 1.6), (rumble, 0.12)), xf)
    # hollow drone: a minor second beating slowly, through a dark 'u'
    voices = [(whole(f, length) + d / length) for f in (82.5, 87.3, 110) for d in (-1, 0, 1)]
    drone = cyclic(lambda s: formant(s, 'u', 0.85), [sum(x) / len(voices) for x in zip(
        *[tone(length, f, 'saw') for f in voices])], 1.0)
    drone = amp(normalize(drone), lambda t: 0.55 + 0.45 * math.sin(TAU * t / (length / 2)))
    # reflected embers: sparse, dull ticks and cold toll tails at fixed seeded times
    rng = random.Random(802)
    ticks = [0.0] * n(length)
    for _ in range(10):
        tk = amp(clatter(0.2, 40, (250, 900), rng.randint(0, 9999)), lambda t: math.exp(-t / 0.05))
        put(ticks, [x * rng.uniform(0.3, 0.8) for x in tk], rng.uniform(0, length))
    for at, f in ((2.0, 82.5), (7.5, 62), (12.5, 73)):
        put(ticks, [x * 0.35 for x in toll(3.0, f, 1.4)], at)
    ticks = cyclic(lambda s: cavern(lowpass2(s, 900), 0.5, 3.0), ticks, 3.0)
    return mix((bed, 1.0), (drone, 0.5), (ticks, 0.7))
