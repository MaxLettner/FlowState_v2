"""Menus, the skill tree, and the background loops: stone, iron and a dark cavern."""
import math
import random

from synth import (
    TAU, amp, bandpass, bell, cavern, clicks, env, formant, highpass2, lowpass2, mix, n, noise, reverb,
    saturate, seamless, stack, sub, sweep, swell, toll, tone)
from common import impact, loop, sound, thump


def cyc(freq, loop_len):
    """Rounds a frequency to whole cycles per loop so a tone repeats exactly."""
    return max(1, round(freq * loop_len)) / loop_len


def periodic(sig, warm, loop_len):
    """Drops the filter warm-up of a loop-periodic signal and keeps exactly one period."""
    return sig[n(warm):n(warm) + n(loop_len)]


def loop_highpass(sig, cutoff):
    """High-passes a loop as if it had always been looping, so the filter's start-up doesn't click at the seam."""
    return highpass2(sig + sig, cutoff)[len(sig):]


def place(buf, ev, t, gain=1.0):
    """Adds an event into a loop buffer at t seconds, wrapping its tail around the loop end."""
    size = len(buf)
    off = n(t) if t else 0
    for i, x in enumerate(ev):
        buf[(off + i) % size] += x * gain


def choir(dur, freq, loop_len, vowel='o', shift=0.8, voices=4, cents=18):
    """A distant low choir: loop-periodic detuned saws shaped into a vowel."""
    f = [cyc(freq * 2 ** ((cents * (k / (voices - 1) - 0.5)) / 1200), loop_len) for k in range(voices)]
    saw = mix(*[(tone(dur, fk, 'saw', phase=0.17 * k), 1 / voices) for k, fk in enumerate(f)])
    return lowpass2(formant(saw, vowel, shift), 900)


@sound
def ui_hover():
    """A soft, very short dark tick: a muffled noise tap over a low sine blip, no reverb."""
    tap = lowpass2(env(noise(0.04, seed=110), 0.0008, 0.03), 1100)
    blip = env(tone(0.05, sweep(230, 150, 0.04)), 0.001, 0.035)
    return mix((tap, 0.8), (blip, 0.6))


@sound
def ui_click():
    """A heavy stone and iron latch: a bolt tick, then a saturated low clunk with a short dull iron ring."""
    tick = highpass2(lowpass2(env(noise(0.02, seed=111), 0.0003, 0.015), 3500), 900)
    clunk = saturate(mix((thump(0.16, 170, 62, 0.14), 1.0), (sub(0.2, 90, 40, 0.15), 0.7),
                         (lowpass2(env(noise(0.1, 'brown', seed=112), 0.001, 0.08), 600), 0.8)), 2.0)
    ring = lowpass2(bell(0.25, 310, ((1, 1.0), (2.4, 0.5), (3.9, 0.25)), 0.06), 1400)
    return cavern(mix((tick, 0.5), (clunk, 1.0, 0.045), (ring, 0.3, 0.045)), 0.14, 1.2)


@sound
def ui_deny():
    """A dull low iron clunk with a muffled recoil: sub and body thud plus a damped low bell, no ring."""
    body = saturate(mix((sub(0.3, 100, 38, 0.22), 1.0), (thump(0.14, 120, 70, 0.12), 0.9),
                        (lowpass2(env(noise(0.1, 'brown', seed=113), 0.001, 0.07), 400), 0.7)), 2.0)
    iron = lowpass2(bell(0.2, 150, ((1, 1.0), (2.3, 0.5), (3.7, 0.3)), 0.05), 600)
    recoil = lowpass2(thump(0.1, 90, 55, 0.08), 400)
    return cavern(mix((body, 1.0), (iron, 0.5), (recoil, 0.45, 0.13)), 0.1, 1.0)


def slab(dur, centre, rate, seed):
    """A grinding stone slab: brown noise shaped by a jittery scrape with a quick swell in and out."""
    scrape = bandpass(noise(dur, 'brown', seed=seed), centre, 1.4)
    scrape = lowpass2(scrape, 900)
    return amp(scrape, lambda t: (0.55 + 0.3 * math.sin(TAU * rate * t) + 0.15 * math.sin(TAU * rate * 1.9 * t + 1))
               * math.sin(math.pi * min(1.0, t / dur)) ** 0.6)


@sound
def ui_open():
    """A heavy stone slab dragged open: low jittery grinding that ends in a saturated stone thud."""
    thud = impact(0.4, 0.7, 85, 0.2, seed=114, room=0)
    dry = mix((slab(0.5, 240, 21, 115), 1.0), (thud, 0.9, 0.38))
    return cavern(dry, 0.2, 1.6)


@sound
def ui_close():
    """A stone slab pushed shut: shorter, lower grinding, then a deep thud."""
    thud = impact(0.4, 0.8, 70, 0.15, seed=116, room=0)
    dry = mix((slab(0.32, 190, 17, 117), 1.0), (thud, 1.0, 0.24))
    return cavern(dry, 0.2, 1.6)


@sound
def ui_slot():
    """An iron socket locking in: a sliding scrape, a low iron seat and a short dull ring."""
    slide = lowpass2(env(noise(0.07, seed=118), 0.01, 0.06), 1800)
    seat = saturate(mix((thump(0.14, 150, 60, 0.12), 1.0), (sub(0.18, 80, 40, 0.14), 0.6)), 2.0)
    ring = lowpass2(bell(0.35, 260, ((1, 1.0), (2.5, 0.6), (4.1, 0.3)), 0.1), 1500)
    return cavern(mix((slide, 0.35), (seat, 1.0, 0.06), (ring, 0.45, 0.06)), 0.14, 1.2)


@sound
def skill_learn():
    """A dark ritual: sub hit and low toll under a swelling minor choir (stack through formant)."""
    dur = 2.2
    notes = (73.4, 110.0, 146.8, 174.6)     # d minor with the root doubled up
    voices = mix(*[(stack(dur, f, 'saw', 5, 22, seed=119 + i), 1.0) for i, f in enumerate(notes)])
    choir_sig = swell(lowpass2(formant(voices, 'o', 0.85), 1100), 0.55)
    choir_sig = mix((choir_sig, 1.0), (swell(lowpass2(formant(voices, 'a', 0.7), 900), 0.6), 0.5))
    bong = lowpass2(toll(2.5, 73.4, 1.6), 1200)
    hit = saturate(mix((sub(0.6, 70, 34, 0.45), 1.0), (thump(0.2, 130, 60, 0.15), 0.8)), 2.0)
    return cavern(mix((choir_sig, 0.9, 0.1), (bong, 0.8), (hit, 0.8)), 0.4, 3.0)


@sound
def ui_start():
    """The start of a run: a huge funeral bell toll and a deep rush down into the abyss, long cavern tail."""
    rush = lowpass2(noise(2.2, 'brown', seed=120), lambda t: 1400 * math.exp(-t * 1.4) + 90)
    rush = swell(rush, 0.3)
    howl = swell(bandpass(noise(2.2, 'pink', seed=121), sweep(500, 90, 2.2), 2.5), 0.25)
    bong = lowpass2(mix((toll(5.0, 55, 3.2), 1.0), (toll(5.0, 82.5, 2.4), 0.35, 0.0)), 1800)
    drop = saturate(mix((sub(1.6, 60, 27, 1.2), 1.0), (thump(0.3, 110, 50, 0.25), 0.9)), 2.2)
    return cavern(mix((bong, 1.0), (drop, 0.9), (rush, 1.4, 0.1), (howl, 0.35, 0.1)), 0.5, 3.0)


@sound
def heartbeat():
    """A muffled lub-dub heard from inside the body: two low sine thuds with sub, almost no highs."""
    def beat(f0, f1, dur):
        body = mix((sub(dur, f0 * 0.8, f1, dur * 0.8), 1.0), (thump(dur, f0 * 1.9, f0, dur * 0.5), 0.6))
        return lowpass2(saturate(body, 1.8), 300)
    dry = mix((beat(100, 48, 0.3), 1.0), (beat(88, 44, 0.26), 0.65, 0.27))
    return reverb(dry, decay=0.18, size=0.35, wet=0.15, dark=450)


@loop
def amb_abyss():
    """The abyss all game long: minor drone, far choir, wind howl and rumble, sparse distant events (16 s loop)."""
    loop_len, xf, warm = 16.0, 1.0, 2.0
    dur = loop_len + xf
    # noise beds are plain noise shaped by whole-loop modulations, crossfaded at the seam
    wander = lambda t: 330 + 170 * math.sin(TAU * t / loop_len) + 90 * math.sin(TAU * 3 * t / loop_len + 1)
    gust = lambda t: 0.5 + 0.3 * math.sin(TAU * 2 * t / loop_len + 0.5) + 0.2 * math.sin(TAU * 5 * t / loop_len)
    howl = amp(bandpass(noise(dur, 'pink', seed=130), wander, 5.0), gust)
    wind = amp(lowpass2(noise(dur, 'pink', seed=131), 420), gust)
    rumble = lowpass2(noise(dur, 'brown', seed=132), 110)
    beds = seamless(mix((howl, 0.9), (wind, 0.7), (rumble, 0.45)), xf)
    # drone: root and fifth with slow beats, a minor third choir above it
    span = loop_len + warm
    breath = lambda t: 0.75 + 0.25 * math.sin(TAU * 2 * t / loop_len)
    drone = mix((tone(span, cyc(55, loop_len)), 0.6), (tone(span, cyc(55, loop_len) + 3 / loop_len), 0.45),
                (tone(span, cyc(82.5, loop_len)), 0.3), (lowpass2(tone(span, cyc(110, loop_len), 'saw'), 220), 0.15))
    voices = mix((choir(span, 110, loop_len), 0.6), (choir(span, 130.8, loop_len, 'a', 0.75), 0.4),
                 (choir(span, 164.8, loop_len, 'u', 0.9), 0.3))
    bed = periodic(mix((drone, 0.25), (amp(voices, breath), 1.6)), warm, loop_len)
    # sparse faraway events, low in the mix, wrapped around the loop
    ev = [0.0] * n(loop_len)
    rattle = highpass2(lowpass2(clicks(1.4, 11, 0.012, (500, 1600), 0.6, seed=133, accel=-0.4), 1800), 300)
    groan = lowpass2(formant(stack(2.6, sweep(62, 46, 2.6), 'saw', 4, 30, seed=134), 'o', 0.6), 700)
    groan = swell(groan, 0.4)
    drip = lowpass2(bell(0.5, 520, ((1, 1.0), (1.5, 0.3)), 0.07), 900)
    place(ev, cavern(rattle, 0.7, 3.2), 3.2, 0.5)
    place(ev, cavern(groan, 0.5, 3.4), 7.3, 0.9)
    place(ev, cavern(drip, 0.8, 3.0), 11.6, 0.5)
    place(ev, cavern(lowpass2(toll(5.0, 65, 3.0), 500), 0.7, 3.8), 13.0, 0.7)
    place(ev, cavern(drip, 0.8, 3.0), 4.9, 0.3)
    return loop_highpass(mix((beds, 0.9), (bed, 0.8), (ev, 0.9)), 22)


@loop
def amb_menu():
    """The title screen: smoldering embers over a slow dark drone, one distant bell toll per loop (10 s loop)."""
    loop_len, xf, warm = 10.0, 0.8, 2.0
    dur = loop_len + xf
    glow = lambda t: 0.65 + 0.25 * math.sin(TAU * 2 * t / loop_len) + 0.1 * math.sin(TAU * 7 * t / loop_len)
    embers = amp(lowpass2(noise(dur, 'pink', seed=140), 650), glow)
    crackle = lowpass2(clicks(dur, 7, 0.008, (400, 1800), 0.8, seed=141), 2200)
    pops = lowpass2(clicks(dur, 1.5, 0.02, (150, 400), 0.9, seed=142), 500)
    beds = seamless(mix((embers, 1.0), (crackle, 0.5), (pops, 0.5)), xf)
    span = loop_len + warm
    breath = lambda t: 0.7 + 0.3 * math.sin(TAU * 1 * t / loop_len)
    drone = mix((tone(span, cyc(49, loop_len)), 0.35), (tone(span, cyc(49, loop_len) + 2 / loop_len), 0.25),
                (tone(span, cyc(58.3, loop_len)), 0.25), (lowpass2(tone(span, cyc(98, loop_len), 'saw'), 200), 0.12),
                (choir(span, 98, loop_len, 'o', 0.8), 0.8))
    drone = periodic(amp(drone, breath), warm, loop_len)
    ev = [0.0] * n(loop_len)
    place(ev, cavern(lowpass2(toll(5.0, 98, 3.0), 700), 0.7, 3.8), 3.5, 0.9)
    return loop_highpass(mix((beds, 1.0), (drone, 0.9), (ev, 0.9)), 22)
