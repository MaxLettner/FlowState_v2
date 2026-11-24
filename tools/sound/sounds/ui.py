"""Menus, the skill tree, and the background loops."""
import math
import random

from synth import *  # noqa: F401,F403  (the DSP toolbox: tone, noise, reverb, cavern, lowpass2, sub, toll, ...)
from common import *  # noqa: F401,F403  (sound, loop, whoosh, thump, squelch, chord, shimmer, impact)


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


@loop
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


@loop
def amb_menu():
    dur, xf = 9.0, 0.8
    fire = lowpass(noise(dur, 'pink', seed=122), 500)
    fire = amp(fire, lambda t: 0.6 + 0.25 * math.sin(TAU * t * 0.7) + 0.15 * math.sin(TAU * t * 2.9))
    pops = lowpass(clicks(dur, 9, 0.006, (500, 2500), seed=123), 4000)
    drone = mix((tone(dur, 49), 0.5), (tone(dur, 73.5), 0.25))
    return seamless(highpass(mix((fire, 0.7), (pops, 0.35), (drone, 0.35)), 20), xf)
