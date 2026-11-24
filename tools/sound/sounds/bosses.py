"""The Pit Lord, the Void Matriarch, the Tar Hydra and their arenas."""
import math
import random

from synth import *  # noqa: F401,F403  (the DSP toolbox: tone, noise, reverb, cavern, lowpass2, sub, toll, ...)
from common import *  # noqa: F401,F403  (sound, loop, whoosh, thump, squelch, chord, shimmer, impact)


@sound
def boss_roar():
    dur = 1.6
    voice = mix((tone(dur, sweep(55, 40, dur), 'saw', vibrato=3, vib_rate=5), 0.8),
                (tone(dur, sweep(82, 61, dur), 'saw', vibrato=4, vib_rate=4), 0.5),
                (tone(dur, sweep(110, 80, dur), 'square', vibrato=5, vib_rate=6), 0.2))
    throat = bandpass(noise(dur, 'pink', seed=267), lambda t: 300 + 260 * math.sin(math.pi * min(1.0, t / dur)), 1.2)
    sig = lowpass(drive(mix((voice, 0.8), (throat, 0.8)), 2.6), 1300)
    return amp(sig, lambda t: min(1.0, t / 0.2) * math.exp(-max(0.0, t - 0.9) / 0.35))


@sound
def gate_slam():
    roar = env(lowpass(noise(1.2, 'pink', seed=268), 1200), 0.01, 1.1)
    return mix((thump(0.8, 90, 30), 1.0), (roar, 0.7), (lowpass(clicks(1.0, 25, 0.006, (500, 2500), seed=269), 4000), 0.4))


@sound
def matriarch_screech():
    """A huge spider's shriek: a chittering, ringing scream over a low void drone."""
    dur = 1.4
    scream = tone(dur, sweep(1300, 700, dur), 'saw', vibrato=60, vib_rate=23)
    chitter = tremolo(bandpass(noise(dur, seed=320), sweep(3200, 1500, dur), 2.0), 31, 0.85)
    drone = lowpass(mix((tone(dur, sweep(70, 52, dur), 'saw'), 0.6), (tone(dur, sweep(105, 78, dur), 'sine'), 0.4)), 500)
    ring = shimmer(dur, 2500, 5200, seed=321)
    sig = lowpass(drive(mix((scream, 0.55), (chitter, 0.6), (drone, 0.6), (ring, 0.12)), 1.9), 4200)
    return amp(sig, lambda t: min(1.0, t / 0.08) * math.exp(-max(0.0, t - 0.7) / 0.3))


@sound
def thread_twang():
    """A taut strand plucked: a low, bent twang."""
    dur = 0.6
    pl = pluck(dur, 98, 0.995, 0.6, seed=328)
    bend = env(tone(dur, sweep(196, 150, dur), 'sine'), 0.002, 0.4)
    return lowpass(mix((pl, 0.8), (bend, 0.35)), 2500)


@sound
def thread_snap():
    """A strand snapping: a sharp crack and a whipping release."""
    crack = env(highpass(noise(0.06, seed=322), 1500), 0.001, 0.05)
    whip = whoosh(0.3, 600, 3500, q=2.5, peak=0.3, seed=323)
    pl = env(tone(0.35, sweep(260, 70, 0.35), 'saw'), 0.001, 0.25)
    return mix((crack, 1.0), (lowpass(pl, 1800), 0.5, 0.01), (whip, 0.6, 0.02))


@sound
def egg_hatch():
    """A silk sac tearing open: a wet rip and skittering legs."""
    rip = squelch(0.25, 2400, 700, 2.5, seed=324)
    skitter = lowpass(clicks(0.5, 60, 0.002, (2500, 6000), seed=325), 7000)
    return mix((rip, 1.0), (amp(skitter, lambda t: math.exp(-t / 0.3)), 0.6, 0.12))


@sound
def silk_spin():
    """Silk paying out fast: a hissing, rising whirr."""
    dur = 0.7
    hiss = bandpass(noise(dur, seed=326), sweep(1800, 4200, dur), 3.0)
    whirr = tremolo(tone(dur, sweep(320, 520, dur), 'square'), 45, 0.8)
    return env(mix((hiss, 0.7), (lowpass(whirr, 1500), 0.3)), 0.05, dur)


@sound
def lights_out():
    """The arena going dark: a long falling swell that swallows the room."""
    dur = 2.2
    fall = mix((tone(dur, sweep(220, 40, dur), 'saw'), 0.4), (tone(dur, sweep(330, 60, dur), 'sine'), 0.4))
    air = lowpass(noise(dur, 'pink', seed=327), sweep(3000, 200, dur))
    return amp(lowpass(mix((fall, 0.7), (air, 0.6)), 1600), lambda t: min(1.0, t / 0.4) * math.exp(-max(0.0, t - 1.4) / 0.4))


@sound
def acid_spit():
    """The Matriarch spitting acid: a wet, throaty hock and a hissing spray."""
    hock = squelch(0.22, 520, 170, 3.0, seed=330)
    gurgle = env(tremolo(lowpass(noise(0.25, 'brown', seed=331), 700), 28, 0.7), 0.01, 0.22)
    spray = env(bandpass(noise(0.45, seed=332), sweep(2400, 5200, 0.45), 2.0), 0.01, 0.35)
    return mix((gurgle, 0.7), (hock, 1.0, 0.08), (spray, 0.55, 0.16))


@sound
def acid_sizzle():
    """Acid eating into stone: a fizzing hiss with little popping bubbles."""
    dur = 0.9
    hiss = env(highpass(noise(dur, seed=333), 3200), 0.015, dur * 0.8)
    pops = lowpass(clicks(dur, 40, 0.004, (800, 3000), seed=334), 5000)
    return mix((tremolo(hiss, 17, 0.4), 0.6), (amp(pops, lambda t: math.exp(-t / 0.5)), 0.5))


@sound
def hydra_roar():
    """A Tar Hydra head roaring: a low, wet bellow with tar bubbling in the throat."""
    dur = 1.3
    voice = mix((tone(dur, sweep(62, 48, dur), 'saw', vibrato=4, vib_rate=5), 0.7),
                (tone(dur, sweep(93, 70, dur), 'saw', vibrato=5, vib_rate=4), 0.45),
                (tone(dur, sweep(140, 104, dur), 'square', vibrato=6, vib_rate=7), 0.15))
    throat = bandpass(noise(dur, 'pink', seed=340), lambda t: 300 + 260 * math.sin(math.pi * min(1.0, t / dur)), 1.2)
    gurgle = tremolo(lowpass(noise(dur, 'brown', seed=341), 600), 19, 0.8)
    sig = lowpass(drive(mix((voice, 0.8), (throat, 0.6), (gurgle, 0.5)), 2.4), 1600)
    return amp(sig, lambda t: min(1.0, t / 0.15) * math.exp(-max(0.0, t - 0.75) / 0.25))


@sound
def tar_spit():
    """The spitter hocking a glob of burning tar: a thick gulp, then a fiery whoosh."""
    gulp = squelch(0.2, 320, 110, 3.5, seed=342)
    whoosh_ = whoosh(0.5, 300, 1800, q=1.2, peak=0.3, seed=343)
    roar = env(lowpass(noise(0.45, 'brown', seed=344), 900), 0.02, 0.4)
    return mix((gulp, 1.0), (whoosh_, 0.6, 0.06), (roar, 0.5, 0.05))


@sound
def tar_splash():
    """A glob of tar landing: a heavy wet slap, sizzling flames."""
    slap = squelch(0.3, 420, 90, 2.2, seed=345)
    thud = thump(0.35, 90, 40)
    hiss = env(highpass(noise(0.7, seed=346), 2800), 0.01, 0.6)
    pops = lowpass(clicks(0.6, 25, 0.006, (500, 2000), seed=347), 3500)
    return mix((slap, 1.0), (thud, 0.6), (hiss, 0.35, 0.05), (amp(pops, lambda t: math.exp(-t / 0.3)), 0.4, 0.05))


@sound
def tar_wave():
    """A wave of tar rolling over the lake: a long churning wash with a low rumble under it."""
    dur = 1.8
    wash = bandpass(noise(dur, 'pink', seed=348), lambda t: 500 + 700 * math.sin(math.pi * min(1.0, t / dur)), 0.9)
    rumble = lowpass(noise(dur, 'brown', seed=349), 220)
    glug = tremolo(lowpass(noise(dur, 'brown', seed=350), 700), 9, 0.7)
    sig = mix((wash, 0.6), (rumble, 0.9), (glug, 0.4))
    return amp(sig, lambda t: min(1.0, t / 0.35) * math.exp(-max(0.0, t - 1.1) / 0.3))
