"""The demon roster (waves 1-3), status effects and world pickups."""
import math
import random

from synth import *  # noqa: F401,F403  (the DSP toolbox: tone, noise, reverb, cavern, lowpass2, sub, toll, ...)
from common import *  # noqa: F401,F403  (sound, loop, whoosh, thump, squelch, chord, shimmer, impact)


@sound
def enemy_jump():
    return env(tone(0.12, sweep(130, 330, 0.1), vibrato=1.5, vib_rate=25), 0.003, 0.12)


@sound
def stun():
    return mix(*[(env(tone(0.1, sweep(2000 + k * 250, 3000 + k * 250, 0.08)), 0.005, 0.1), 0.5, k * 0.11) for k in range(3)])


@sound
def imp_leap():
    """A shrill little chitter as the imp coils to hop."""
    chit = mix(*[(env(tone(0.05, sweep(1500 + k * 260, 2300 + k * 200, 0.04), 'saw'), 0.002, 0.05), 0.45, k * 0.045)
                 for k in range(3)])
    return mix((bandpass(chit, 2200, 1.2), 1.0), (whoosh(0.16, 600, 2400, 1.6, 0.5, seed=200), 0.4, 0.1))


@sound
def hound_growl():
    """Low rattling growl while the hound crouches."""
    dur = 0.55
    base = tremolo(tone(dur, sweep(85, 105, dur), 'saw', vibrato=4, vib_rate=7), 26, 0.7)
    rasp = tremolo(bandpass(noise(dur, seed=201), 500, 1.5), 31, 0.8)
    return env(lowpass(drive(mix((base, 0.8), (rasp, 0.6)), 1.6), 1300), 0.06, dur)


@sound
def hound_lunge():
    snarl = env(bandpass(drive(tone(0.25, sweep(240, 120, 0.25), 'saw', vibrato=6, vib_rate=30), 2), 900, 1.2), 0.005, 0.25)
    return mix((snarl, 0.8), (whoosh(0.3, 300, 1800, 1.4, 0.35, seed=202), 0.7))


@sound
def brute_roar():
    dur = 0.8
    voice = mix((tone(dur, sweep(70, 55, dur), 'saw', vibrato=3, vib_rate=6), 0.7),
                (tone(dur, sweep(105, 82, dur), 'saw', vibrato=4, vib_rate=5), 0.4))
    throat = bandpass(noise(dur, 'pink', seed=203), lambda t: 380 + 220 * math.sin(math.pi * min(1.0, t / dur)), 1.3)
    sig = lowpass(drive(mix((voice, 0.8), (throat, 0.7)), 2.2), 1500)
    return amp(sig, lambda t: min(1.0, t / 0.12) * math.exp(-max(0.0, t - 0.4) / 0.25))


@sound
def brute_slam():
    boom = drive(mix((thump(0.9, 70, 24), 1.0), (lowpass(env(noise(0.9, 'brown', seed=204), 0.001, 0.8), 400), 1.0)), 2.8)
    crack = highpass(env(noise(0.08, seed=205), 0.0005, 0.07), 1500)
    rng = random.Random(206)
    rubble = mix(*[(lowpass(env(noise(0.06, seed=207 + k), 0.001, 0.05), rng.uniform(800, 2500)), rng.uniform(0.2, 0.5),
                    0.08 + k * rng.uniform(0.03, 0.07)) for k in range(10)])
    return mix((boom, 1.0), (crack, 0.6), (rubble, 0.6))


@sound
def magma_splash():
    hiss = env(highpass(noise(0.6, seed=208), 2500), 0.003, 0.5)
    glop = squelch(0.25, 900, 200, 2.5, seed=209)
    pops = lowpass(clicks(0.5, 30, 0.006, (600, 2500), seed=210), 4000)
    return mix((glop, 1.0), (hiss, 0.45), (amp(pops, lambda t: math.exp(-t / 0.2)), 0.5, 0.03))


@sound
def bat_screech():
    dur = 0.22
    sq = env(tone(dur, sweep(3200, 4800, dur), 'square', vibrato=90, vib_rate=45), 0.004, dur)
    return bandpass(mix((sq, 0.8), (highpass(env(noise(dur, seed=211), 0.004, dur), 4000), 0.3)), 3800, 1.0)


@sound
def cultist_cast():
    """An eerie, rising hum with whispers while the hellfire gathers."""
    dur = 0.85
    hum = mix((tone(dur, sweep(110, 220, dur), 'saw'), 0.4), (tone(dur, sweep(165, 330, dur)), 0.5),
              (tone(dur, sweep(116, 232, dur)), 0.3))
    whisper = bandpass(noise(dur, 'pink', seed=212), lambda t: 1200 + 1800 * t / dur, 2.5)
    sig = mix((lowpass(hum, 1600), 0.7), (tremolo(whisper, 13, 0.6), 0.45), (shimmer(dur, 1800, 3600, seed=213), 0.25))
    return amp(sig, lambda t: (t / dur) ** 1.2)


@sound
def hellfire_launch():
    roar = env(lowpass(noise(0.4, 'pink', seed=214), sweep(800, 3000, 0.25)), 0.01, 0.35)
    zap = env(tone(0.25, sweep(300, 900, 0.2), 'saw'), 0.003, 0.22)
    return mix((roar, 0.8), (bandpass(zap, 900, 1.5), 0.5), (whoosh(0.3, 500, 2500, 1.5, 0.3, seed=215), 0.6))


@sound
def teleport():
    up = env(tone(0.35, sweep(200, 1600, 0.3), 'sine', vibrato=20, vib_rate=25), 0.01, 0.35)
    puff = whoosh(0.4, 400, 3500, 1.2, 0.25, seed=216)
    return mix((up, 0.5), (puff, 0.8), (shimmer(0.35, 2000, 5000, seed=217), 0.35, 0.05))


@sound
def flail_swing():
    """A heavy chain whirled through the air, links rattling."""
    rattle = highpass(clicks(0.4, 55, 0.004, (2500, 6000), seed=230), 2000)
    return mix((whoosh(0.4, 200, 1100, 1.2, 0.55, seed=231), 1.0), (amp(rattle, lambda t: math.sin(math.pi * min(1.0, t / 0.4))), 0.45))


@sound
def stone_crack():
    rng = random.Random(250)
    cracks = mix(*[(highpass(env(noise(0.03, seed=251 + k), 0.0005, 0.025), 1500), rng.uniform(0.4, 1.0), k * rng.uniform(0.02, 0.05))
                   for k in range(9)])
    rumble = lowpass(env(noise(0.5, 'brown', seed=260), 0.01, 0.45), 300)
    return mix((cracks, 1.0), (rumble, 0.7), (thump(0.2, 140, 60), 0.5))


@sound
def watcher_charge():
    """A rising whine as the eye locks on."""
    dur = 1.5
    whine = mix((tone(dur, sweep(300, 1400, dur), 'sine', vibrato=12, vib_rate=7), 0.6), (tone(dur, sweep(600, 2800, dur)), 0.25))
    return amp(mix((whine, 1.0), (shimmer(dur, 2000, 4000, seed=261), 0.2)), lambda t: (t / dur) ** 1.5)


@sound
def beam_fire():
    zap = drive(mix((tone(0.4, sweep(900, 300, 0.4), 'saw'), 0.7), (bandpass(noise(0.4, seed=262), 2500, 1.0), 0.7)), 2.0)
    return mix((env(zap, 0.002, 0.38), 1.0), (thump(0.25, 200, 60), 0.6))


@sound
def portal_open():
    dur = 0.9
    swirl = bandpass(noise(dur, 'pink', seed=263), lambda t: 300 + 1500 * math.sin(math.pi * t / dur), 2.0)
    drone = mix((tone(dur, sweep(80, 160, dur), 'saw'), 0.4), (tone(dur, sweep(120, 240, dur)), 0.3))
    return amp(mix((tremolo(swirl, 9, 0.5), 0.8), (lowpass(drone, 900), 0.6)), lambda t: math.sin(math.pi * min(1.0, t / dur)))


@sound
def hex_cast():
    dur = 0.6
    chant = mix(*[(env(tone(dur, f, 'saw', vibrato=4, vib_rate=5), 0.08, dur), 0.3) for f in (110, 131, 165)])
    whisper = tremolo(bandpass(noise(dur, 'pink', seed=264), 1800, 3), 17, 0.7)
    return mix((lowpass(chant, 1200), 0.8), (whisper, 0.5), (shimmer(dur, 1500, 3000, seed=265), 0.2))


@sound
def curse():
    """A descending ghostly moan: you've been hexed."""
    dur = 0.8
    moan = env(tone(dur, sweep(520, 180, dur), 'sine', vibrato=18, vib_rate=6), 0.05, dur)
    return mix((moan, 0.8), (env(bandpass(noise(dur, seed=266), 900, 2), 0.05, dur), 0.4))


@sound
def drake_screech():
    """A raspy, falling wyvern cry from high up."""
    dur = 0.7
    cry = tone(dur, sweep(1500, 600, dur), 'saw', vibrato=40, vib_rate=19)
    rasp = tremolo(bandpass(noise(dur, seed=301), sweep(2600, 1200, dur), 1.6), 37, 0.7)
    return env(lowpass(drive(mix((cry, 0.6), (rasp, 0.6)), 1.8), 3800), 0.03, dur)


@sound
def bomb_drop():
    """A short falling whistle as a fire bomb leaves the drake's belly."""
    dur = 0.45
    whistle = env(tone(dur, sweep(1800, 700, dur), 'sine', vibrato=8, vib_rate=11), 0.02, dur)
    air = whoosh(dur, 500, 2500, 1.4, 0.3, seed=302)
    return mix((whistle, 0.6), (air, 0.5))


@sound
def void_cast():
    """A deep drone pulling in on itself, with a reversed-sounding swell, while the void orb gathers."""
    dur = 1.15
    drone = mix((tone(dur, sweep(55, 82, dur), 'saw'), 0.5), (tone(dur, sweep(110, 164, dur), 'sine'), 0.4))
    suck = bandpass(noise(dur, 'pink', seed=303), lambda t: 2400 - 2000 * (t / dur), 2.2)
    sig = mix((lowpass(drone, 700), 0.8), (tremolo(suck, 13, 0.6), 0.5), (shimmer(dur, 3000, 6000, seed=304), 0.15))
    return amp(sig, lambda t: (t / dur) ** 1.3)


@sound
def well_open():
    """The well snapping open: a low thud and a rushing inhale."""
    thud = thump(0.5, 120, 35)
    rush = bandpass(noise(0.7, 'pink', seed=305), lambda t: 3000 * math.exp(-t * 4) + 200, 1.8)
    return mix((thud, 0.9), (env(rush, 0.01, 0.7), 0.6))


@sound
def well_hum():
    """One beat of the well's throbbing hum (played on repeat while it lasts)."""
    dur = 0.95
    hum = mix((tone(dur, 46, 'saw'), 0.5), (tone(dur, 69, 'sine'), 0.5), (tone(dur, 92.5, 'sine'), 0.2))
    return amp(lowpass(hum, 400), lambda t: math.sin(math.pi * t / dur) ** 2)


@sound
def mimic_chomp():
    """A wooden lid slamming shut on teeth: a knock, a crunch and a wet snap."""
    knock = mix((thump(0.18, 300, 90), 0.9), (bandpass(env(noise(0.08, seed=306), 0.001, 0.06), 1800, 2.0), 0.6))
    crunch = lowpass(clicks(0.12, 140, 0.003, (1200, 4000), seed=307), 5000)
    wet = squelch(0.12, 1600, 500, 3, seed=308)
    return mix((knock, 1.0), (amp(crunch, lambda t: math.exp(-t / 0.05)), 0.5, 0.01), (wet, 0.4, 0.02))


@sound
def chest_open():
    creak = bandpass(tone(0.4, sweep(45, 30, 0.4), 'saw'), 700, 3)
    creak = env(creak, 0.02, 0.4, hold=0.2, curve='lin')
    rng = random.Random(106)
    coins = mix(*[(bell(0.3, rng.uniform(2800, 4200), ((1, 1.0), (1.5, 0.5)), 0.12), rng.uniform(0.3, 0.6),
                   0.3 + rng.uniform(0, 0.35)) for _ in range(9)])
    sparkle = chord(0.8, (1047, 1319, 1568, 2093), 0.4, ((1, 1.0), (2.0, 0.2)), spread=0.06)
    return mix((creak, 0.6), (thump(0.1, 160, 80), 0.5, 0.33), (coins, 0.5), (sparkle, 0.5, 0.32))


@sound
def level_up():
    arp = chord(1.1, (523, 659, 784, 1047), 0.6, ((1, 1.0), (2.0, 0.35), (3.0, 0.1)), spread=0.08)
    return mix((arp, 0.8), (shimmer(0.7, 2500, 6000, seed=107), 0.4, 0.3))
