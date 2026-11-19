"""Writes every sound effect into src/main/resources/assets/sounds as 16-bit mono WAV.

Run from anywhere:  python tools/sound/generate_all.py [name ...]
Every file is peak-normalized; how loud each one plays in game is set in Sfx.java.
"""
import math
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from synth import (SR, TAU, n, sweep, tone, noise, bell, pluck, clicks, lowpass, highpass, bandpass, env, swell,
                   amp, drive, fade, tremolo, mix, echo, seamless, write)

OUT = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'sounds'))
SOUNDS = {}


def sound(fn):
    SOUNDS[fn.__name__] = fn
    return fn


def whoosh(dur, lo, hi, q=1.5, peak=0.45, seed=None, color='white'):
    """Filtered noise rising to hi and back to lo: anything moving through air."""
    centre = lambda t: lo + (hi - lo) * math.sin(math.pi * min(1.0, t / dur)) ** 1.5
    return swell(bandpass(noise(dur, color, seed), centre, q), peak)


def thump(dur, f0, f1, decay=None):
    return env(tone(dur, sweep(f0, f1, dur * 0.7)), 0.001, decay or dur)


def squelch(dur, f0, f1, q=3, seed=None):
    return env(bandpass(noise(dur, seed=seed), sweep(f0, f1, dur), q), 0.002, dur)


def chord(dur, freqs, decay, partials=((1, 1.0), (2.0, 0.3), (3.0, 0.12)), spread=0.0):
    return mix(*[(bell(dur, f, partials, decay), 1.0, i * spread) for i, f in enumerate(freqs)])


def shimmer(dur, lo, hi, seed=None):
    """Sparkly high glints: magic."""
    rng = random.Random(seed)
    parts = []
    for _ in range(int(dur * 22)):
        f = rng.uniform(lo, hi)
        parts.append((bell(0.25, f, ((1, 1.0),), 0.12), rng.uniform(0.2, 0.6), rng.uniform(0, dur)))
    return mix(*parts)


#=====Player movement=====
@sound
def jump():
    blob = env(tone(0.16, sweep(170, 520, 0.12), vibrato=1.5, vib_rate=28), 0.003, 0.16)
    wet = squelch(0.08, 900, 2200, 4, seed=1)
    return mix((blob, 1.0), (wet, 0.35))


@sound
def land():
    return mix((thump(0.16, 140, 55), 1.0), (squelch(0.14, 1400, 400, 3, seed=2), 0.6),
               (lowpass(env(noise(0.08, seed=3), 0.001, 0.06), 900), 0.4))


@sound
def step():
    return mix((squelch(0.07, 700, 1600, 3, seed=4), 1.0), (thump(0.06, 210, 110), 0.5))


@sound
def dash():
    return mix((whoosh(0.28, 400, 2600, 1.2, 0.35, seed=5), 1.0), (squelch(0.06, 2000, 900, 3, seed=6), 0.3))


@sound
def levitate():
    air = whoosh(0.9, 300, 1400, 1.0, 0.3, seed=7, color='pink')
    rise = env(tone(0.9, sweep(330, 880, 0.9), vibrato=0.3, vib_rate=5), 0.15, 0.9, curve='lin')
    return mix((air, 1.0), (rise, 0.25), (shimmer(0.8, 1800, 4200, seed=8), 0.35))


@sound
def player_hurt():
    hit = mix((thump(0.14, 180, 60), 1.0), (lowpass(env(noise(0.06, seed=9), 0.001, 0.05), 2500), 0.6))
    blorp = env(tone(0.28, sweep(480, 160, 0.25), vibrato=2.0, vib_rate=22), 0.01, 0.28)
    return drive(mix((hit, 1.0), (blorp, 0.55, 0.02), (squelch(0.18, 1800, 500, 3, seed=10), 0.4)), 1.6)


@sound
def player_death():
    melt = env(tone(1.3, sweep(320, 55, 1.2), vibrato=2.5, vib_rate=7), 0.02, 1.3, curve='lin')
    rng = random.Random(11)
    bubbles = mix(*[(env(tone(0.05, sweep(f, f * 1.8, 0.05)), 0.002, 0.05), rng.uniform(0.2, 0.5), rng.uniform(0.05, 1.1))
                    for f in (rng.uniform(250, 700) for _ in range(16))])
    goo = lowpass(squelch(1.1, 1200, 200, 2, seed=12), 1500)
    return mix((melt, 0.8), (bubbles, 0.6), (goo, 0.5))


@sound
def fall():
    whistle = env(tone(1.1, sweep(1300, 180, 1.1), vibrato=0.4, vib_rate=6), 0.05, 1.1, curve='lin')
    return mix((whistle, 0.5), (whoosh(1.1, 200, 900, 1.0, 0.2, seed=13, color='pink'), 0.6))


#=====Melee=====
@sound
def swing_light():
    return mix((whoosh(0.2, 700, 3600, 1.4, 0.4, seed=20), 1.0), (highpass(whoosh(0.12, 3000, 7000, 2, 0.5, seed=21), 2000), 0.3, 0.03))


@sound
def swing_heavy():
    return mix((whoosh(0.4, 180, 1300, 1.2, 0.5, seed=22, color='pink'), 1.0), (whoosh(0.3, 600, 2200, 1.5, 0.5, seed=23), 0.4, 0.06))


@sound
def punch():
    return mix((whoosh(0.1, 600, 2400, 1.5, 0.5, seed=24), 0.8), (thump(0.05, 150, 90), 0.2))


@sound
def stab():
    return mix((whoosh(0.15, 1200, 4500, 1.8, 0.3, seed=25), 1.0), (bell(0.12, 2100, ((1, 1), (2.3, 0.4)), 0.06), 0.15, 0.06))


@sound
def spin():
    return whoosh(0.38, 250, 1600, 1.3, 0.55, seed=26, color='pink')


@sound
def hit_slash():
    slice_ = highpass(env(noise(0.05, seed=30), 0.0005, 0.04), 2500)
    ring = bell(0.2, 2300, ((1, 1.0), (1.47, 0.6), (2.09, 0.3)), 0.1)
    flesh = mix((thump(0.09, 170, 70), 1.0), (squelch(0.1, 1500, 600, 2.5, seed=31), 0.6))
    return mix((slice_, 0.9), (ring, 0.18), (flesh, 0.8))


@sound
def hit_blunt():
    body = drive(mix((thump(0.22, 120, 40), 1.0), (lowpass(env(noise(0.1, seed=32), 0.001, 0.08), 1200), 0.8)), 2.5)
    return mix((body, 1.0), (squelch(0.12, 900, 300, 2, seed=33), 0.5))


@sound
def hit_punch():
    return drive(mix((thump(0.09, 190, 75), 1.0), (lowpass(env(noise(0.03, seed=34), 0.0005, 0.025), 3000), 0.7),
                     (squelch(0.07, 1200, 500, 2, seed=35), 0.4)), 1.8)


@sound
def hit_arrow():
    return mix((thump(0.07, 320, 130), 1.0), (squelch(0.06, 1600, 800, 3, seed=36), 0.6),
               (lowpass(env(noise(0.015, seed=37), 0.0005, 0.012), 5000), 0.5))


@sound
def hit_magic():
    pop = env(tone(0.15, sweep(900, 1500, 0.08), vibrato=0.5, vib_rate=40), 0.001, 0.15)
    fizz = bandpass(env(noise(0.18, seed=38), 0.001, 0.16), 3500, 1.5)
    return mix((pop, 0.5), (fizz, 0.6), (shimmer(0.15, 2500, 5000, seed=39), 0.4), (thump(0.06, 180, 90), 0.4))


@sound
def crit():
    ding = chord(0.45, (1760, 2637), 0.3, ((1, 1.0), (2.0, 0.25)))
    click = highpass(env(noise(0.01, seed=40), 0.0002, 0.008), 3000)
    return mix((ding, 0.6), (click, 0.6))


@sound
def enemy_death():
    splat = drive(mix((squelch(0.32, 2400, 250, 1.8, seed=41), 1.0), (thump(0.2, 220, 50), 0.9)), 1.8)
    gloop = env(tone(0.3, sweep(300, 90, 0.3), vibrato=3, vib_rate=18), 0.01, 0.3)
    rng = random.Random(42)
    drops = mix(*[(squelch(0.04, rng.uniform(1500, 3000), 600, 4, seed=43 + k), 0.4, 0.12 + k * 0.05) for k in range(4)])
    return mix((splat, 1.0), (gloop, 0.4), (drops, 0.5))


@sound
def block_raise():
    clank = bell(0.18, 420, ((1, 1.0), (2.69, 0.6), (4.45, 0.35)), 0.1)
    return mix((clank, 0.7), (lowpass(env(noise(0.03, seed=50), 0.0005, 0.025), 3000), 0.5))


@sound
def block_hit():
    clang = bell(0.55, 760, ((1, 1.0), (1.78, 0.7), (2.95, 0.5), (4.1, 0.3)), 0.4)
    return mix((clang, 0.7), (highpass(env(noise(0.04, seed=51), 0.0005, 0.03), 1500), 0.7), (thump(0.08, 200, 90), 0.5))


@sound
def deflect():
    ting = bell(0.4, 2500, ((1, 1.0), (1.56, 0.6), (2.08, 0.4)), 0.3)
    return mix((ting, 0.7), (highpass(env(noise(0.02, seed=52), 0.0005, 0.015), 3000), 0.6))


@sound
def flow_up():
    return chord(0.4, (880, 1320), 0.28, ((1, 1.0), (2.0, 0.3), (4.0, 0.08)), spread=0.03)


@sound
def flow_max():
    arp = chord(0.9, (880, 1109, 1319, 1760), 0.45, ((1, 1.0), (2.0, 0.3)), spread=0.045)
    return mix((arp, 0.8), (shimmer(0.6, 2500, 6000, seed=53), 0.4, 0.1))


@sound
def flow_ready():
    return bell(0.05, 2600, ((1, 1.0),), 0.02)


@sound
def charge_weapon():
    dur = 1.5
    hum = lowpass(tone(dur, sweep(80, 170, dur), 'saw'), sweep(300, 1600, dur))
    hum = tremolo(hum, sweep(5, 22, dur), 0.4)
    hiss = bandpass(noise(dur, seed=54), sweep(600, 3000, dur), 2)
    return amp(mix((hum, 0.7), (hiss, 0.4)), lambda t: min(1.0, t / dur) ** 1.2 * 0.9 + 0.1)


@sound
def charge_ready():
    return mix((chord(0.5, (1319, 1976), 0.35, ((1, 1.0), (2.0, 0.2))), 0.7), (shimmer(0.3, 3000, 6000, seed=55), 0.4))


@sound
def perfect():
    shing = highpass(env(noise(0.5, seed=56), 0.001, 0.45), sweep(2000, 6000, 0.4))
    ch = chord(0.8, (1319, 1661, 1976, 2637), 0.5, ((1, 1.0), (2.0, 0.25)), spread=0.02)
    return mix((shing, 0.4), (ch, 0.6))


@sound
def slam_dive():
    return whoosh(0.45, 500, 2600, 1.2, 0.75, seed=57)


@sound
def slam_impact():
    boom = drive(mix((thump(0.6, 85, 28), 1.0), (lowpass(env(noise(0.5, seed=58), 0.001, 0.4), sweep(1500, 150, 0.4)), 0.9)), 2.2)
    debris = lowpass(clicks(0.6, 40, 0.006, (600, 2500), seed=59), 4000)
    return mix((boom, 1.0), (amp(debris, lambda t: math.exp(-t / 0.2)), 0.5, 0.03))


@sound
def bounce():
    return env(tone(0.35, sweep(180, 620, 0.3), 'tri', vibrato=3, vib_rate=26), 0.002, 0.35)


#=====Ranged=====
@sound
def bow_draw():
    dur = 0.9
    creak = bandpass(tone(dur, sweep(35, 75, dur), 'saw'), 900, 3)
    creak = amp(creak, lambda t: 0.3 + 0.7 * (0.5 + 0.5 * math.sin(TAU * 7 * t)) * (t / dur))
    stretch = bandpass(noise(dur, seed=60), sweep(300, 800, dur), 2)
    return amp(mix((creak, 0.7), (stretch, 0.3)), lambda t: min(1.0, t / 0.1))


@sound
def bow_release():
    twang = env(pluck(0.4, 150, 0.993, 0.6, seed=61), 0.001, 0.35)
    return mix((twang, 1.0), (whoosh(0.15, 1200, 4000, 1.5, 0.2, seed=62), 0.5))


@sound
def arrow_thunk():
    return mix((thump(0.12, 260, 140), 1.0), (squelch(0.06, 900, 600, 3, seed=63), 0.6),
               (env(pluck(0.15, 380, 0.98, 0.4, seed=64), 0.001, 0.12), 0.4))


@sound
def bone_shatter():
    return mix((clicks(0.16, 70, 0.006, (2500, 6500), seed=65), 1.0), (thump(0.05, 400, 200), 0.3))


@sound
def crank():
    # a loop of ratchet ticks: plays while a crossbow is cranked
    tick = lambda f: mix((bell(0.03, f, ((1, 1.0), (2.4, 0.5)), 0.012), 1.0),
                         (highpass(env(noise(0.008, seed=int(f)), 0.0002, 0.006), 2000), 0.6))
    return mix(*[(tick(1700 + (k % 2) * 300), 1.0, k / 12) for k in range(6)], (silence_pad(0.5), 0))


def silence_pad(dur):
    return [0.0] * n(dur)


@sound
def crossbow_loaded():
    clack = lambda f, s: mix((bell(0.06, f, ((1, 1.0), (2.3, 0.6)), 0.03), 1.0),
                             (highpass(env(noise(0.01, seed=s), 0.0002, 0.008), 1500), 0.8))
    return mix((clack(1300, 66), 0.8), (clack(900, 67), 1.0, 0.07), (thump(0.08, 180, 100), 0.5, 0.07))


@sound
def crossbow_fire():
    thwack = env(pluck(0.25, 95, 0.985, 0.8, seed=68), 0.0005, 0.2)
    snap = highpass(env(noise(0.02, seed=69), 0.0002, 0.015), 1500)
    return drive(mix((thwack, 1.0), (snap, 0.8), (whoosh(0.15, 1500, 4500, 1.5, 0.2, seed=70), 0.4)), 1.4)


@sound
def throw():
    return mix((whoosh(0.32, 300, 1800, 1.3, 0.35, seed=71), 1.0), (thump(0.05, 140, 90), 0.2))


@sound
def trident_stick():
    twang = env(pluck(0.5, 290, 0.995, 0.5, seed=72), 0.001, 0.45)
    return mix((thump(0.1, 260, 120), 1.0), (twang, 0.6), (squelch(0.05, 1200, 700, 3, seed=73), 0.5))


@sound
def recall():
    rise = env(tone(0.55, sweep(500, 1300, 0.55), vibrato=0.4, vib_rate=9), 0.2, 0.55, curve='lin')
    return mix((whoosh(0.55, 300, 2200, 1.2, 0.85, seed=74), 1.0), (rise, 0.2), (shimmer(0.5, 2000, 5000, seed=75), 0.3))


@sound
def pickup():
    return chord(0.25, (1500, 2250), 0.14, ((1, 1.0), (2.7, 0.3)), spread=0.04)


@sound
def enemy_throw():
    return whoosh(0.22, 300, 1500, 1.4, 0.4, seed=76)


#=====Magic=====
@sound
def cast_arcane():
    pew = env(tone(0.2, sweep(1300, 420, 0.18), vibrato=0.8, vib_rate=30), 0.002, 0.2)
    return mix((pew, 0.6), (shimmer(0.15, 2500, 5000, seed=80), 0.4), (whoosh(0.15, 800, 3000, 1.5, 0.2, seed=81), 0.3))


@sound
def cast_spark():
    rng = random.Random(82)
    zap = env(tone(0.16, lambda t: 2400 * math.exp(-t * 9) + 500 + rng.uniform(-300, 300), 'square'), 0.001, 0.15)
    crackle = highpass(clicks(0.14, 120, 0.003, (3000, 8000), seed=83), 2000)
    return mix((lowpass(zap, 5000), 0.4), (crackle, 0.6))


@sound
def cast_icicle():
    shing = highpass(env(noise(0.25, seed=84), 0.002, 0.22), sweep(3000, 7000, 0.2))
    return mix((shing, 0.5), (bell(0.35, 2637, ((1, 1.0), (1.5, 0.5), (2.0, 0.3)), 0.22), 0.4))


@sound
def cast_poison():
    puff = lambda s: env(bandpass(noise(0.07, seed=s), 1300, 1.5), 0.003, 0.06)
    return mix((puff(85), 1.0), (puff(86), 0.8, 0.025), (puff(87), 0.7, 0.05), (squelch(0.1, 600, 300, 2, seed=88), 0.3))


@sound
def cast_fireball():
    roar = lowpass(noise(0.5, 'pink', seed=89), sweep(300, 1600, 0.4))
    roar = swell(roar, 0.3)
    crackle = lowpass(clicks(0.45, 60, 0.004, (800, 3000), seed=90), 5000)
    return mix((roar, 1.0), (amp(crackle, lambda t: 1 - t / 0.45), 0.4))


@sound
def charge_magic():
    dur = 1.5
    core = env(tone(dur, sweep(180, 620, dur), vibrato=0.6, vib_rate=8), 0.3, dur, hold=dur - 0.35, curve='lin')
    roar = lowpass(noise(dur, 'pink', seed=91), sweep(200, 1400, dur))
    crackle = clicks(dur, 25, 0.004, (800, 3500), seed=92, accel=2.0)
    return amp(mix((core, 0.4), (roar, 0.7), (crackle, 0.3)), lambda t: 0.2 + 0.8 * min(1.0, t / dur))


@sound
def explosion():
    boom = drive(mix((thump(0.7, 75, 26), 1.0), (lowpass(env(noise(0.8, seed=93), 0.002, 0.7), sweep(2500, 180, 0.6)), 1.0)), 2.6)
    crackle = lowpass(clicks(0.8, 50, 0.005, (700, 3000), seed=94), 4500)
    return mix((boom, 1.0), (amp(crackle, lambda t: math.exp(-t / 0.3)), 0.5, 0.05))


@sound
def freeze():
    crackle = highpass(clicks(0.45, 90, 0.004, (3000, 9000), seed=95), 2500)
    frost = highpass(env(noise(0.5, seed=96), 0.02, 0.45), 5000)
    return mix((amp(crackle, lambda t: math.exp(-t / 0.25)), 0.8), (frost, 0.3), (shimmer(0.35, 2500, 5000, seed=97), 0.5))


@sound
def shatter():
    rng = random.Random(98)
    shards = mix(*[(bell(0.5, rng.uniform(2200, 7000), ((1, 1.0), (1.7, 0.4)), rng.uniform(0.08, 0.3)),
                    rng.uniform(0.2, 0.6), rng.uniform(0, 0.08)) for _ in range(14)])
    burst = highpass(env(noise(0.15, seed=99), 0.0005, 0.12), 2500)
    return mix((shards, 0.6), (burst, 0.7), (thump(0.06, 300, 150), 0.3))


@sound
def crackle():
    roar = env(lowpass(noise(0.6, 'pink', seed=100), 700), 0.15, 0.45)
    pops = lowpass(clicks(0.6, 14, 0.006, (500, 2200), seed=101), 4000)
    return mix((roar, 0.5), (pops, 0.9))


@sound
def no_mana():
    buzz = lowpass(env(tone(0.18, 110, 'square'), 0.003, 0.18, hold=0.08, curve='lin'), 900)
    fizz = bandpass(env(noise(0.15, seed=102), 0.002, 0.12), 2500, 2)
    return mix((buzz, 0.6), (fizz, 0.3))


@sound
def shield_on():
    hum = mix(*[(env(tone(0.5, sweep(f, f * 2, 0.35)), 0.05, 0.5, curve='lin'), 0.4) for f in (330, 333, 495)])
    return mix((hum, 1.0), (shimmer(0.4, 2000, 4500, seed=103), 0.4))


@sound
def shield_off():
    return mix(*[(env(tone(0.35, sweep(f, f / 2, 0.3)), 0.01, 0.35, curve='lin'), 0.4) for f in (660, 663, 990)])


@sound
def shield_hit():
    bwom = env(tone(0.3, sweep(380, 260, 0.3), vibrato=1.2, vib_rate=14), 0.002, 0.3)
    return mix((bwom, 0.7), (shimmer(0.2, 2000, 4000, seed=104), 0.4), (thump(0.06, 160, 90), 0.4))


@sound
def shield_break():
    drop = env(tone(0.5, sweep(700, 120, 0.45)), 0.002, 0.5)
    return mix((shatter(), 0.8), (drop, 0.4))


@sound
def enchant_on():
    arp = chord(0.6, (659, 880, 1319), 0.3, ((1, 1.0), (2.0, 0.35)), spread=0.06)
    return mix((arp, 0.7), (shimmer(0.5, 2500, 5500, seed=105), 0.35))


@sound
def toggle_off():
    return chord(0.35, (880, 587), 0.18, ((1, 1.0), (2.0, 0.2)), spread=0.08)


#=====Enemies & status=====
@sound
def enemy_jump():
    return env(tone(0.12, sweep(130, 330, 0.1), vibrato=1.5, vib_rate=25), 0.003, 0.12)


@sound
def stun():
    return mix(*[(env(tone(0.1, sweep(2000 + k * 250, 3000 + k * 250, 0.08)), 0.005, 0.1), 0.5, k * 0.11) for k in range(3)])


#=====World=====
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


#=====UI=====
@sound
def ui_hover():
    return mix((bandpass(env(noise(0.03, seed=110), 0.0005, 0.025), 1500, 2), 1.0), (bell(0.04, 620, ((1, 1.0),), 0.02), 0.4))


@sound
def ui_click():
    stone = mix((thump(0.1, 200, 95), 1.0), (lowpass(env(noise(0.05, seed=111), 0.0005, 0.04), 2500), 0.7))
    return echo(stone, 0.07, 0.25, 2, 2000)


@sound
def ui_deny():
    b = lambda: lowpass(env(tone(0.09, 140, 'square'), 0.002, 0.09, hold=0.05, curve='lin'), 800)
    return mix((b(), 0.6), (b(), 0.6, 0.13))


@sound
def ui_start():
    gong = bell(2.0, 98, ((1, 1.0), (2.02, 0.6), (2.76, 0.45), (4.07, 0.3), (5.4, 0.2)), 1.6)
    rush = whoosh(1.2, 150, 1200, 1.0, 0.8, seed=112, color='pink')
    return mix((gong, 0.8), (rush, 0.5), (shimmer(0.8, 1500, 4000, seed=113), 0.25, 0.2))


@sound
def ui_open():
    grind = bandpass(noise(0.4, 'brown', seed=114), 350, 1.2)
    grind = amp(grind, lambda t: (0.6 + 0.4 * math.sin(TAU * 23 * t)) * math.sin(math.pi * t / 0.4))
    return mix((grind, 1.0), (thump(0.12, 120, 60), 0.6, 0.3))


@sound
def ui_close():
    grind = bandpass(noise(0.25, 'brown', seed=115), 280, 1.2)
    grind = amp(grind, lambda t: (0.6 + 0.4 * math.sin(TAU * 19 * t)) * math.sin(math.pi * t / 0.25))
    return mix((grind, 1.0), (thump(0.1, 110, 55), 0.6, 0.18))


@sound
def skill_learn():
    thrum = env(tone(0.9, 110, 'tri'), 0.01, 0.9)
    ch = chord(0.9, (659, 988, 1319), 0.5, ((1, 1.0), (2.0, 0.3)), spread=0.05)
    return mix((thrum, 0.4), (ch, 0.7), (shimmer(0.6, 2500, 6000, seed=116), 0.35, 0.1))


@sound
def ui_slot():
    return mix((ui_click(), 0.6), (bell(0.15, 1568, ((1, 1.0), (2.0, 0.2)), 0.08), 0.35, 0.01))


#=====Ambience (loops)=====
@sound
def amb_abyss():
    dur, xf = 17.0, 1.0
    wind = lowpass(noise(dur, 'pink', seed=120), lambda t: 260 + 160 * math.sin(TAU * t / 8.0) + 80 * math.sin(TAU * t / 3.1))
    wind = amp(wind, lambda t: 0.55 + 0.3 * math.sin(TAU * t / 8.0 + 1) + 0.15 * math.sin(TAU * t / 2.3))
    # drone: a low fifth whose beating slowly breathes; periods divide the loop so it stays seamless
    loop = dur - xf
    drone = mix((tone(dur, 55), 0.5), (tone(dur, 55 + 1 / loop * 3), 0.35), (tone(dur, 82.5), 0.25),
                (lowpass(tone(dur, 110, 'saw'), 300), 0.08))
    rumble = lowpass(noise(dur, 'brown', seed=121), 90)
    return seamless(highpass(mix((wind, 0.9), (drone, 0.5), (rumble, 0.5)), 20), xf)


@sound
def amb_menu():
    dur, xf = 9.0, 0.8
    fire = lowpass(noise(dur, 'pink', seed=122), 500)
    fire = amp(fire, lambda t: 0.6 + 0.25 * math.sin(TAU * t * 0.7) + 0.15 * math.sin(TAU * t * 2.9))
    pops = lowpass(clicks(dur, 9, 0.006, (500, 2500), seed=123), 4000)
    drone = mix((tone(dur, 49), 0.5), (tone(dur, 73.5), 0.25))
    return seamless(highpass(mix((fire, 0.7), (pops, 0.35), (drone, 0.35)), 20), xf)


LOOPS = {'amb_abyss', 'amb_menu', 'crank'}


def main():
    os.makedirs(OUT, exist_ok=True)
    only = set(sys.argv[1:])
    for name, fn in SOUNDS.items():
        if only and name not in only:
            continue
        sig = fn()
        out = write(os.path.join(OUT, name + '.wav'), sig, trim=name not in LOOPS)
        print(f'{name:16s} {len(out) / SR:5.2f}s')


if __name__ == '__main__':
    main()
