"""The demon roster (waves 1-3), status effects and world pickups: dark, throaty, heavy."""
import math
import random

from synth import *  # noqa: F401,F403  (the DSP toolbox: tone, noise, reverb, cavern, lowpass2, sub, toll, ...)
from common import *  # noqa: F401,F403  (sound, loop, whoosh, thump, squelch, chord, shimmer, impact)

PITCH = (1.0, 1.08, 0.92)   # per-variant pitch of the repeating sounds


def _voice(dur, freq, vowel='o', shift=1.0, voices=4, cents=24, seed=None, shape='saw', vibrato=0.0, vib_rate=0.0):
    """A creature throat: a detuned saw stack through a vowel formant (shift < 1 = bigger)."""
    f = freq if callable(freq) else (lambda t, f=freq: f)
    if vibrato:
        src = stack(dur, lambda t: f(t) * 2 ** (vibrato * math.sin(TAU * vib_rate * t) / 12), shape, voices, cents, seed)
    else:
        src = stack(dur, f, shape, voices, cents, seed)
    return normalize(formant(src, vowel, shift), 0.9)


def _clip(sig, dur):
    """Cuts a reverb tail so a timed build-up keeps its length."""
    return fade(sig[:n(dur)], 0.002, 0.02)


def _dark(sig, cutoff=2500):
    return lowpass2(sig, cutoff)


@sound(variants=3)
def enemy_jump(v):
    """A short chesty grunt with a stone scuff as a demon pushes off."""
    p = PITCH[v]
    grunt = _voice(0.16, sweep(85 * p, 140 * p, 0.14), 'o', 0.8, 3, 30, 400 + v)
    scuff = lowpass2(env(noise(0.1, 'pink', 410 + v), 0.003, 0.08), 900)
    body = env(tone(0.12, sweep(110 * p, 55, 0.1)), 0.002, 0.1)
    sig = saturate(mix((env(grunt, 0.015, 0.16), 0.8), (scuff, 0.5), (body, 0.7)), 1.8)
    return cavern(_dark(sig, 1800), 0.12, 1.0)


@sound
def stun():
    """A dull iron clang that wobbles down into a dazed groan."""
    clang = toll(0.7, 190, 0.5)
    groan = env(_voice(0.55, sweep(150, 90, 0.5), 'u', 0.9, 3, 40, 420, vibrato=1.5, vib_rate=7), 0.02, 0.55)
    rings = tremolo(bandpass(noise(0.6, seed=421), 750, 5), 15, 0.8)
    sig = saturate(mix((clang, 0.7), (groan, 0.8), (env(rings, 0.01, 0.5), 0.3), (thump(0.2, 140, 50), 0.7)), 1.6)
    return cavern(_dark(sig, 2200), 0.2, 1.4)


@sound(variants=3)
def imp_leap(v):
    """A shrill throaty chitter, three rising barks, plus chitin clicks as the imp coils to hop."""
    p = PITCH[v]
    parts = []
    for k in range(3):
        bark = _voice(0.09, sweep((260 + k * 40) * p, (420 + k * 60) * p, 0.08), 'e', 1.0, 3, 30, 430 + v * 5 + k)
        parts.append((env(bark, 0.004, 0.09), 0.8, k * 0.075))
    chit = lowpass2(clicks(0.25, 60, 0.004, (700, 1800), seed=440 + v), 3000)
    air = whoosh(0.2, 400, 1800, 1.5, 0.5, seed=441 + v)
    sig = mix(*parts, (chit, 0.5), (air, 0.35, 0.1))
    return cavern(_dark(sig, 2400), 0.15, 1.0)


@sound
def hound_growl():
    """A low rattling, rasping growl in the chest of a big dog: pulsing throat, wet rasp, rising."""
    dur = 0.55
    pulse = lambda t: 24 + 6 * t / dur
    throat = _voice(dur, sweep(62, 80, dur), 'a', 0.75, 4, 40, 450)
    throat = tremolo(throat, pulse, 0.85)
    rasp = tremolo(bandpass(noise(dur, 'pink', 451), sweep(500, 800, dur), 2.0), pulse, 0.9)
    body = tone(dur, sweep(65, 78, dur))
    sig = saturate(mix((throat, 0.9), (rasp, 0.6), (body, 0.4)), 2.2)
    sig = amp(sig, lambda t: min(1.0, t / 0.1) * (1 - 0.3 * t / dur))
    return cavern(_dark(sig, 1800), 0.2, 1.2)


@sound(variants=3)
def hound_lunge(v):
    """A snarl falling into a rush of air and a snap of jaws."""
    p = PITCH[v]
    snarl = _voice(0.3, sweep(210 * p, 95 * p, 0.28), 'a', 0.8, 4, 40, 460 + v)
    snarl = saturate(env(tremolo(snarl, 30, 0.5), 0.01, 0.3), 2.0)
    air = whoosh(0.32, 250, 1500, 1.3, 0.4, seed=461 + v)
    snap = lowpass2(clicks(0.08, 90, 0.003, (500, 1600), seed=462 + v), 3000)
    sig = mix((snarl, 0.9), (air, 0.7), (snap, 0.6, 0.22), (thump(0.15, 120, 50), 0.5, 0.24))
    return cavern(_dark(sig, 2500), 0.2, 1.2)


@sound
def brute_roar():
    """A huge bellowing roar from a barrel chest: sub-heavy stacked throats with a gravelly growl."""
    dur = 0.8
    roar = _voice(dur, lambda t: 52 - 12 * (t / dur), 'o', 0.7, 5, 36, 470, vibrato=1.0, vib_rate=5)
    upper = _voice(dur, lambda t: 78 - 18 * (t / dur), 'a', 0.7, 4, 30, 471)
    grit = tremolo(bandpass(noise(dur, 'pink', 472), lambda t: 330 + 180 * math.sin(math.pi * min(1.0, t / dur)), 1.4), 22, 0.8)
    low = amp(tone(dur, sweep(55, 36, dur)), lambda t: 1.0)
    sig = saturate(mix((roar, 0.9), (upper, 0.5), (grit, 0.6), (low, 0.5)), 2.4)
    sig = amp(sig, lambda t: min(1.0, t / 0.1) * math.exp(-max(0.0, t - 0.4) / 0.25))
    return cavern(_dark(sig, 1700), 0.3, 2.0)


@sound(variants=3)
def brute_slam(v):
    """A colossal fist smashing stone: heavy impact, rolling rubble and a long cavern boom."""
    p = PITCH[v]
    hit = impact(1.0, 1.4, 85 * p, 0.7, seed=480 + v, room=0)
    rng = random.Random(481 + v)
    rubble = mix(*[(lowpass2(env(noise(0.07, seed=490 + v * 20 + k), 0.001, 0.05), rng.uniform(600, 1800)),
                    rng.uniform(0.2, 0.5), 0.07 + k * rng.uniform(0.03, 0.06)) for k in range(10)])
    dust = lowpass2(env(noise(0.7, 'brown', 482 + v), 0.05, 0.6), 500)
    sig = saturate(mix((hit, 1.0), (rubble, 0.6), (dust, 0.4)), 1.5)
    return cavern(sig, 0.35, 2.4)


@sound
def magma_splash():
    """A thick glop of molten rock: slow bubbling, a hiss of steam and low pops."""
    glop = saturate(squelch(0.3, 600, 130, 2.5, seed=500), 2.0)
    bubble = saturate(env(tone(0.25, sweep(160, 70, 0.2)), 0.003, 0.22), 1.5)
    hiss = lowpass2(highpass2(env(noise(0.6, seed=501), 0.003, 0.5), 1500), 5000)
    pops = lowpass2(clicks(0.5, 22, 0.008, (350, 1400), seed=502), 2500)
    sig = mix((glop, 0.9), (bubble, 0.7), (hiss, 0.22), (amp(pops, lambda t: math.exp(-t / 0.2)), 0.6, 0.03),
              (sub(0.3, 90, 40, 0.25), 0.7))
    return cavern(sig, 0.2, 1.4)


@sound(variants=3)
def bat_screech(v):
    """A bat's rattling shriek lowered into the throat: a falling-then-rising formant cry with rasp and a wing flap."""
    p = PITCH[v]
    dur = 0.26
    cry = _voice(dur, lambda t: (700 + 500 * math.sin(math.pi * t / dur)) * p, 'a', 0.9, 3, 50, 510 + v,
                 vibrato=2.5, vib_rate=32)
    rasp = highpass2(env(tremolo(noise(dur, seed=511 + v), 40, 0.8), 0.01, dur), 800)
    flap = lowpass2(env(noise(0.1, 'pink', 512 + v), 0.01, 0.1), 600)
    sig = mix((env(cry, 0.01, dur), 0.9), (_dark(rasp, 1800), 0.1), (flap, 0.5))
    return cavern(_dark(saturate(sig, 1.6), 2000), 0.2, 1.2)


@sound
def cultist_cast():
    """A whispered occult chant that swells upward as hellfire gathers, a low choir rising in pitch."""
    dur = 0.85
    choir = mix(*[(_voice(dur, sweep(f, f * 1.5, dur), 'o', 0.9, 3, 26, 520 + i), 0.5) for i, f in enumerate((98, 117, 147))])
    vowel = mix((_voice(dur, sweep(196, 294, dur), 'a', 1.0, 3, 30, 523), 0.35),
                (tremolo(bandpass(noise(dur, 'pink', 524), sweep(700, 1700, dur), 2.5), 11, 0.7), 0.5))
    sig = amp(mix((choir, 1.0), (vowel, 1.0), (sub(dur, 50, 70, dur * 2), 0.3)), lambda t: (t / dur) ** 1.2)
    return _clip(cavern(_dark(saturate(sig, 1.5), 2400), 0.25, 1.2), dur)


@sound
def hellfire_launch():
    """A furnace roar bursting out of a hot throat, rushing air and a low crackle."""
    roar = lowpass2(env(noise(0.5, 'pink', 530), 0.01, 0.45), sweep(500, 2200, 0.3))
    throat = env(_voice(0.4, sweep(120, 70, 0.35), 'a', 0.8, 4, 40, 531), 0.01, 0.4)
    crackle = lowpass2(clicks(0.45, 40, 0.005, (500, 1800), seed=532), 3000)
    sig = saturate(mix((roar, 0.9), (throat, 0.7), (whoosh(0.35, 250, 1500, 1.4, 0.3, seed=533, color='pink'), 0.7),
                       (amp(crackle, lambda t: math.exp(-t / 0.25)), 0.4), (sub(0.4, 100, 40, 0.35), 0.7)), 1.8)
    return cavern(_dark(sig, 2800), 0.25, 1.6)


@sound
def teleport():
    """The cultist vanishing: an inward whisper (reversed swell) collapsing into a hollow pop."""
    inward = reverse(env(bandpass(noise(0.4, 'pink', 540), sweep(300, 1400, 0.4), 2.0), 0.01, 0.4))
    moan = reverse(env(_voice(0.4, sweep(110, 180, 0.4), 'u', 0.9, 3, 30, 541), 0.01, 0.4))
    pop = mix((sub(0.35, 130, 40, 0.3), 1.0), (env(tone(0.15, sweep(300, 90, 0.1)), 0.001, 0.15), 0.6),
              (lowpass2(env(noise(0.1, seed=542), 0.001, 0.06), 1500), 0.3))
    sig = saturate(mix((inward, 0.9), (moan, 0.8), (pop, 1.0, 0.4)), 1.6)
    return cavern(_dark(sig, 2500), 0.3, 1.6)


@sound(variants=3)
def flail_swing(v):
    """A heavy iron chain whirled through the air: low whoosh, rattling links and a dull clank."""
    p = PITCH[v]
    air = whoosh(0.45, 120 * p, 800 * p, 1.2, 0.55, seed=550 + v, color='pink')
    links = lowpass2(highpass2(clicks(0.45, 40, 0.005, (500, 2200), seed=551 + v), 400), 3200)
    clank = bell(0.5, 230 * p, ((1, 1.0), (2.4, 0.5), (3.9, 0.3)), 0.15)
    sig = mix((air, 1.0), (amp(links, lambda t: math.sin(math.pi * min(1.0, t / 0.45))), 0.6), (clank, 0.25, 0.2),
              (sub(0.3, 70, 40, 0.25), 0.5, 0.1))
    return cavern(_dark(saturate(sig, 1.4), 3000), 0.15, 1.0)


@sound(variants=3)
def stone_crack(v):
    """Stone splitting: sharp cracks over a grinding rumble and a heavy thud, with dust falling."""
    p = PITCH[v]
    rng = random.Random(560 + v)
    cracks = mix(*[(lowpass2(highpass2(env(noise(0.04, seed=570 + v * 20 + k), 0.0005, 0.03), 900), rng.uniform(2500, 4500)),
                    rng.uniform(0.4, 1.0), k * rng.uniform(0.02, 0.05)) for k in range(8)])
    grind = lowpass2(env(noise(0.6, 'brown', 561 + v), 0.01, 0.5), 350)
    dust = bandpass(env(noise(0.4, 'pink', 562 + v), 0.05, 0.35), 1000, 1.2)
    sig = saturate(mix((cracks, 1.6), (grind, 0.7), (dust, 0.15), (thump(0.25, 130 * p, 50), 0.6),
                       (sub(0.4, 70, 35, 0.35), 0.4)), 1.6)
    return cavern(sig, 0.25, 1.6)


@sound
def watcher_charge():
    """An eye locking on: a rising, pulsing hum with an inward whine, the pulses speeding up toward the shot."""
    dur = 1.5
    rate = lambda t: 3 + 20 * (t / dur) ** 2
    whine = tone(dur, sweep(280, 1300, dur), 'sine', vibrato=1.5, vib_rate=6)
    beat = mix((tone(dur, sweep(150, 640, dur), 'saw'), 0.4), (tone(dur, sweep(155, 660, dur), 'saw'), 0.4))
    hum = tremolo(_dark(beat, 1800), rate, 0.75)
    air = tremolo(bandpass(noise(dur, 'pink', 580), sweep(400, 1800, dur), 3.0), rate, 0.6)
    sig = mix((whine, 0.5), (hum, 0.8), (air, 0.4), (sub(dur, 45, 70, dur * 3), 0.5))
    sig = amp(saturate(sig, 1.5), lambda t: 0.1 + 0.9 * (t / dur) ** 1.5)
    return _clip(cavern(_dark(sig, 2500), 0.18, 1.0), dur)


@sound
def beam_fire():
    """A sustained furnace beam: a snarling saw stack falling in pitch, roaring noise and a deep thump."""
    dur = 0.45
    buzz = saturate(env(_voice(dur, sweep(240, 90, dur), 'a', 0.9, 5, 35, 590), 0.003, 0.42), 2.5)
    roar = lowpass2(env(noise(dur, 'pink', 591), 0.003, 0.4), sweep(2500, 600, dur))
    sig = mix((buzz, 1.0), (roar, 0.7), (impact(0.5, 0.9, 100, 0.3, seed=592, room=0), 0.8))
    return cavern(_dark(sig, 3000), 0.25, 1.6)


@sound
def portal_open():
    """A rift tearing open: a low choir drone, swirling wind and a slow inward swell, then a hollow boom."""
    dur = 1.0
    swirl = tremolo(bandpass(noise(dur, 'pink', 600), lambda t: 250 + 1200 * math.sin(math.pi * t / dur), 2.0), 8, 0.6)
    choir = _voice(dur, sweep(70, 110, dur), 'o', 0.8, 5, 40, 601)
    drone = lowpass2(tone(dur, sweep(55, 85, dur), 'saw'), 400)
    inward = reverse(env(bandpass(noise(0.5, 'pink', 602), sweep(200, 900, 0.5), 1.5), 0.01, 0.5))
    sig = amp(mix((swirl, 0.8), (choir, 0.7), (drone, 0.7), (inward, 0.6)), lambda t: math.sin(math.pi * min(1.0, t / dur)) ** 0.8)
    sig = mix((saturate(sig, 1.6), 1.0), (sub(0.6, 70, 32, 0.5), 0.9, 0.45))
    return cavern(_dark(sig, 2400), 0.4, 2.2)


@sound
def hex_cast():
    """A syllabic whispered incantation: a low three-note chant (minor third, tritone) with pulsing whispers."""
    dur = 0.6
    syl = lambda t: 0.45 + 0.55 * abs(math.sin(math.pi * 3.5 * t / dur))
    chant = mix(*[(_voice(dur, f, 'o', 0.85, 3, 22, 610 + i), 0.5) for i, f in enumerate((110, 131, 156))])
    chant = amp(chant, syl)
    whisper = amp(bandpass(noise(dur, 'pink', 613), 1500, 3.0), lambda t: syl(t + 0.05))
    sig = amp(mix((chant, 1.0), (whisper, 0.6), (sub(dur, 55, 45, dur), 0.3)), lambda t: min(1.0, t / 0.06) * (1 - 0.4 * t / dur))
    return _clip(cavern(_dark(saturate(sig, 1.5), 2200), 0.25, 1.4), dur + 0.3)


@sound
def curse():
    """A ghostly groan sinking into the ground: a descending 'oo' moan, cold whispers and a low swell."""
    dur = 0.9
    moan = _voice(dur, sweep(240, 85, dur), 'u', 0.85, 4, 30, 620, vibrato=1.5, vib_rate=5)
    whisper = tremolo(bandpass(noise(dur, 'pink', 621), sweep(1400, 600, dur), 3.0), 9, 0.7)
    sig = mix((env(moan, 0.06, dur, curve='lin'), 1.0), (env(whisper, 0.1, dur), 0.5), (sub(dur, 60, 40, dur), 0.4))
    return cavern(_dark(saturate(sig, 1.5), 2200), 0.4, 2.0)


@sound
def drake_screech():
    """A raspy wyvern cry from high above: a falling harsh formant scream with a throat growl and a long echo."""
    dur = 0.75
    cry = _voice(dur, sweep(640, 260, dur), 'a', 0.9, 5, 60, 630, vibrato=2.0, vib_rate=19)
    rasp = tremolo(bandpass(noise(dur, seed=631), sweep(1800, 800, dur), 1.6), 36, 0.7)
    growl = tremolo(_voice(dur, sweep(110, 70, dur), 'o', 0.8, 3, 30, 632), 28, 0.6)
    sig = saturate(mix((cry, 0.9), (rasp, 0.5), (growl, 0.6)), 2.2)
    sig = amp(sig, lambda t: min(1.0, t / 0.05) * math.exp(-max(0.0, t - 0.25) / 0.3))
    return cavern(_dark(sig, 3200), 0.4, 2.0)


@sound
def bomb_drop():
    """A falling whistle in the middle range with a hollow release thunk as the bomb leaves the drake."""
    dur = 0.5
    whistle = env(tone(dur, sweep(1100, 420, dur), 'tri', vibrato=0.5, vib_rate=8), 0.02, dur)
    voice = env(_voice(dur, sweep(420, 190, dur), 'o', 1.0, 3, 20, 640), 0.02, dur)
    air = whoosh(dur, 300, 1500, 1.4, 0.3, seed=641)
    thunk = mix((thump(0.2, 200, 70), 0.9), (lowpass2(env(noise(0.06, seed=642), 0.001, 0.05), 1200), 0.4))
    sig = mix((whistle, 0.5), (voice, 0.5), (air, 0.4), (thunk, 0.8))
    return cavern(_dark(sig, 2600), 0.15, 1.2)


@sound
def void_cast():
    """Void gathering: a deep throbbing drone, a whispered inward rush converging downward and a swelling hollow choir."""
    dur = 1.15
    drone = lowpass2(mix((tone(dur, sweep(50, 75, dur), 'saw'), 0.6), (tone(dur, sweep(100, 150, dur)), 0.5)), 500)
    suck = tremolo(bandpass(noise(dur, 'pink', 650), lambda t: 1900 - 1500 * (t / dur), 2.5), 6, 0.6)
    choir = tremolo(_voice(dur, sweep(98, 130, dur), 'u', 0.8, 4, 40, 651), 5, 0.4)
    whisper = tremolo(bandpass(noise(dur, seed=652), 1100, 4.0), 13, 0.8)
    sig = mix((drone, 0.8), (suck, 0.9), (choir, 0.8), (whisper, 0.3))
    sig = amp(saturate(sig, 1.6), lambda t: (t / dur) ** 1.3)
    return _clip(cavern(_dark(sig, 2300), 0.25, 1.4), dur)


@sound
def well_open():
    """The gravity well snapping open: a heavy hollow thud and a rushing inhale dragged downward."""
    thud = impact(0.6, 1.0, 90, 0.3, seed=660, room=0)
    rush = bandpass(noise(0.8, 'pink', 661), lambda t: 2200 * math.exp(-t * 4) + 180, 1.8)
    choir = env(_voice(0.7, sweep(130, 60, 0.7), 'u', 0.8, 3, 30, 662), 0.02, 0.7)
    sig = mix((thud, 1.0), (env(rush, 0.01, 0.75), 0.7), (choir, 0.5, 0.05))
    return cavern(_dark(saturate(sig, 1.4), 2500), 0.35, 2.0)


@sound
def well_hum():
    """One 0.95 s beat of the well's low throbbing hum: saturated sub-fifth drone with a faint mid whisper."""
    dur = 0.95
    hum = mix((tone(dur, 46, 'saw'), 0.3), (tone(dur, 69, 'sine'), 0.5), (tone(dur, 92.5, 'sine'), 0.3))
    hum = saturate(lowpass2(hum, 350), 2.2)
    mid = tremolo(bandpass(noise(dur, 'pink', 670), 520, 4.0), 6, 0.6)
    voice = _voice(dur, 92.5, 'u', 0.9, 3, 12, 671)
    sig = mix((hum, 1.0), (mid, 0.6), (voice, 0.5))
    return amp(sig, lambda t: math.sin(math.pi * t / dur) ** 2)


@sound
def mimic_chomp():
    """A heavy lid slamming shut on a mouthful of teeth: iron-bound knock, bone crunch and a wet snarl."""
    knock = mix((impact(0.4, 0.9, 150, 0.6, seed=680, room=0), 1.0), (toll(0.5, 260, 0.3), 0.3))
    crunch = lowpass2(clicks(0.14, 120, 0.004, (500, 2200), seed=681), 3500)
    wet = squelch(0.14, 1200, 350, 3, seed=682)
    snarl = env(_voice(0.2, sweep(150, 80, 0.2), 'a', 0.8, 3, 40, 683), 0.01, 0.2)
    sig = mix((knock, 1.0), (amp(crunch, lambda t: math.exp(-t / 0.06)), 0.6, 0.02), (wet, 0.4, 0.03), (snarl, 0.6, 0.03))
    return cavern(_dark(saturate(sig, 1.4), 3200), 0.2, 1.4)


@sound
def chest_open():
    """A rusty iron-bound lid groaning open and thudding back, then a low toll and a dark choir swell: ominous treasure."""
    creak = bandpass(stack(0.6, sweep(55, 85, 0.6), 'saw', 3, 80, 690), lambda t: 350 + 450 * t / 0.6, 6)
    creak = env(tremolo(creak, 17, 0.5), 0.05, 0.6, hold=0.3, curve='lin')
    lid = impact(0.5, 0.9, 120, 0.5, seed=691, room=0)
    chime = toll(1.4, 147, 1.2)
    choir = mix(*[(_voice(1.4, f, 'o', 0.9, 3, 25, 692 + i), 0.5) for i, f in enumerate((73.4, 87.3, 110))])
    choir = amp(choir, lambda t: math.sin(math.pi * min(1.0, t / 1.4)) ** 1.5)
    sig = mix((normalize(creak, 0.7), 0.6), (lid, 1.0, 0.55), (chime, 0.5, 0.6), (choir, 0.8, 0.5), (sub(1.0, 60, 36, 0.9), 0.6, 0.55))
    return cavern(_dark(saturate(sig, 1.4), 2800), 0.4, 1.8)


@sound
def level_up():
    """A ritual surge: a reversed swell and rising choir into a heavy toll with a minor-third fifth chord and deep sub."""
    swell_in = reverse(env(mix((bandpass(noise(0.6, 'pink', 700), sweep(200, 1400, 0.6), 2.0), 0.7),
                               (_voice(0.6, sweep(73, 110, 0.6), 'o', 0.85, 3, 30, 701), 0.9)), 0.01, 0.6))
    hit = impact(1.2, 1.2, 80, 0.4, seed=702, room=0)
    bells = mix((toll(2.0, 98, 1.6), 1.0), (toll(2.0, 117, 1.5), 0.55, 0.05), (toll(2.0, 147, 1.3), 0.45, 0.1))
    choir = mix(*[(_voice(2.0, f, 'o', 0.9, 4, 26, 710 + i), 0.5) for i, f in enumerate((98, 117, 147, 196))])
    choir = amp(choir, lambda t: math.sin(math.pi * min(1.0, t / 2.0)) ** 1.5)
    sig = mix((swell_in, 0.8), (hit, 0.9, 0.6), (bells, 0.7, 0.6), (choir, 0.7, 0.55))
    return cavern(_dark(saturate(sig, 1.3), 2800), 0.4, 2.0)
