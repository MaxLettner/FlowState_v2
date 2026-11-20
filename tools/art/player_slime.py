"""Animations for the Tar & Ember player slime (slimes.PLAYER), 64x64 frames.

The jump (takeoff + the 35-frame airflow grid, see anim_airflow) is shaped by hand. Everything else is
driven by physics so it moves the same way:
  - the body is a damped spring (stretch s and shear q) with the same constants as SpriteComponent's
    squash & stretch, stepped through time (Jelly / simulate / periodic); tar is incompressible, so
    w * h stays the same (shape),
  - tar droplets, sparks and smoke fly ballistically under the game's gravity (800 px/s^2, 400 art px/s^2
    at x2) with air drag toward the wind (Drop),
  - the air streaming past bends the body like in the jump frames (ground_air), and every moving
    animation is a grid picked from the real velocity by SpriteComponent.

Strips (frames, playback - SpriteComponent must use the same numbers):
  idle 12 @ 8 fps          fidget 24 @ 10        block 6 @ 8          charge 6 (picked by charge progress)
  walk 3 x 10 (speed column x stride phase, the phase follows the distance walked: WALK_STRIDE)
  run  3 x 10 (same, RUN_STRIDE)
  land 3 x 3 x 8 @ 20 (fall speed row x sideways column x frame)
  dash 6 @ 30 (one 0.2 s dodge)         hurt 6 @ 14     death 14 @ 9     attack 8 @ 18    shoot 5 @ 16
  cast 8 @ 14     slam 10 @ 16
  levitate 3 x 3 x 6 @ 8 (vertical row x sideways column x frame)   glide 3 x 6 @ 8   dive 3 x 4 @ 14
  takeoff 3, airflow 35 (the jump: unchanged)
Base strips: slimes.py writes only Player_air (fallback); idle / walk / land come from here.

Review sheet:  python tools/art/player_slime.py sheet out.png
"""
import math
import sys

from pixel import new, put, ellipse, mix, strip
import slimes
from slimes import PLAYER, TAR, EMBER, SIZE, CX, BASE_Y, draw_slime

FRAME = SIZE


# ---------- frame helper ----------
def frame(w, h, lift=0, lean=0, eye_dy=0, blink=False, phase=0.0, round=0.0, form=None, **mood):
    """One player frame. round (0..1) rounds the bottom off for airborne frames (slimes.half_width),
    form = airflow shape (slimes.draw_slime: drop tails and how far they sweep sideways).
    mood keys: eyes, look, heat, cool (see slimes.eyes_ember / ember_cracks),
    pre = [fn(img, px, info)] drawn before the outline (tar bits), post = [fn(img)] drawn after (glow, sparks)."""
    img = draw_slime(PLAYER, w, h, lift=lift, lean=lean, eye_dy=eye_dy, blink=blink, phase=phase, mood=mood,
                     round=round, form=form)
    for fx in mood.get('post', []):
        fx(img)
    return img


# ---------- tar bits (pre: they get the black outline) ----------
def droplets(*pts):
    """Flying tar droplets at (x, y, r); r >= 1.5 gets a molten core so they read on dark backgrounds."""
    def fx(img, px, info):
        for x, y, r in pts:
            ellipse(px, SIZE, SIZE, x, y, r, r * 1.1, TAR[1])
            put(px, SIZE, SIZE, int(x - r * 0.4), int(y - r * 0.4), TAR[0])
            if r >= 1.5:
                put(px, SIZE, SIZE, int(x + 0.3), int(y + 0.3), EMBER[3])
    return fx


def bubble(r):
    """A tar bubble swelling on top of the head (fidget)."""
    def fx(img, px, info):
        x, y = slimes.rel(info, -0.15, 1.0)
        y += r * 0.6
        ellipse(px, SIZE, SIZE, x, y - r, r, r, TAR[1])
        ellipse(px, SIZE, SIZE, x, y - r * 0.8, r * 0.6, r * 0.5, TAR[2])
        put(px, SIZE, SIZE, int(x - r * 0.5), int(y - r * 1.5), TAR[0])
        if r >= 3:
            put(px, SIZE, SIZE, int(x), int(y - r * 0.7), EMBER[3])   # glow showing through the thin skin
    return fx


# ---------- glow (post: no outline) ----------
def sparks(*pts):
    """Ember sparks: (x, y, size, heat 0 white .. 4 deep red)."""
    def fx(img):
        px = img.load()
        for x, y, size, t in pts:
            c = EMBER[min(4, max(0, int(t)))]
            put(px, SIZE, SIZE, int(x), int(y), c)
            if size >= 2:
                for dx, dy in ((1, 0), (0, 1), (-1, 0), (0, -1)):
                    if px[int(x) + dx, int(y) + dy][3] == 0 if 0 <= int(x) + dx < SIZE and 0 <= int(y) + dy < SIZE else False:
                        put(px, SIZE, SIZE, int(x) + dx, int(y) + dy, EMBER[min(4, int(t) + 2)])
    return fx


def streaks(*lines):
    """Horizontal motion streaks behind a dash: (x0, x1, y, colour index); fades out to the back."""
    def fx(img):
        px = img.load()
        for x0, x1, y, ci in lines:
            for x in range(int(x0), int(x1)):
                if not (0 <= x < SIZE and 0 <= y < SIZE) or px[x, y][3] > 0:
                    continue
                t = (x - x0) / max(1, x1 - x0)          # 0 at the far back .. 1 next to the body
                if t < 0.35 and (x % 2):
                    continue                             # broken up at the tail
                c = EMBER[ci]
                px[x, y] = (c[0], c[1], c[2], int(90 + 165 * t))
    return fx


# ---------- physics ----------
G = 400.0                     # gravity, art px/s^2 (the game's 800 px/s^2 with sprites shown at x2)
MOVE_SPEED = 400.0            # the player's max move speed (game px/s): the hx columns are fractions of it
W0, H0 = 40.0, 38.0           # resting body
AREA = W0 * H0                # incompressible tar: w * h stays about the same
SPRING_K, SPRING_C = 420.0, 17.0     # body spring, same as SpriteComponent's squash & stretch (~3.3 Hz)


def shape(s):
    """(w, h) for vertical stretch s (h = H0 (1 + s)) at constant area."""
    s = max(-0.62, min(0.75, s))
    h = H0 * (1 + s)
    return min(62.0, AREA / h), h


class Jelly:
    """The body as two damped springs: s = vertical stretch, q = how far the top sits ahead of the foot (px).
    Shear is softer than stretch: the tar flows sideways more easily than it compresses."""

    def __init__(self, s=0.0, q=0.0, vs=0.0, vq=0.0, k=SPRING_K, c=SPRING_C, kq=None, cq=None):
        self.s, self.q, self.vs, self.vq = s, q, vs, vq
        self.k, self.c = k, c
        self.kq = k * 0.55 if kq is None else kq
        self.cq = c * 0.75 if cq is None else cq

    def step(self, dt, s_rest=0.0, q_rest=0.0, fs=0.0, fq=0.0):
        self.vs += (-self.k * (self.s - s_rest) - self.c * self.vs + fs) * dt
        self.s += self.vs * dt
        self.vq += (-self.kq * (self.q - q_rest) - self.cq * self.vq + fq) * dt
        self.q += self.vq * dt

    def state(self, t, p=0.0):
        return dict(t=t, p=p, s=self.s, q=self.q, vs=self.vs, vq=self.vq)


def simulate(n, fps, jelly=None, drive=None, kicks=(), sub=32):
    """n frames of the body at fps. drive(t) -> {s_rest, q_rest, fs, fq} (any of them),
    kicks = [(t, dvs, dvq)] sudden velocity changes (hits, impacts). Returns the state at each frame time."""
    j = jelly or Jelly()
    dt = 1.0 / fps / sub
    todo = sorted(kicks)
    out, t = [], 0.0
    for _ in range(n):
        out.append(j.state(t))
        for _ in range(sub):
            while todo and todo[0][0] <= t + 1e-9:
                _, dvs, dvq = todo.pop(0)
                j.vs += dvs
                j.vq += dvq
            j.step(dt, **(drive(t) if drive else {}))
            t += dt
    return out


def periodic(n, period, drive=None, kicks=(), cycles=10, sub=32, jelly=None):
    """Steady state of a periodic motion (a crawl or a bound): runs `cycles` periods of drive(p) (p = phase
    0..1) with kicks [(p, dvs, dvq)] every period, and returns the n frames of the last one, so the loop
    is seamless and the wobble has settled into the rhythm."""
    j = jelly or Jelly()
    steps = n * sub
    dt = period / steps
    out = []
    for c in range(cycles):
        for k in range(steps):
            p = k / steps
            if c == cycles - 1 and k % sub == 0:
                out.append(j.state(p * period, p))
            for kp, dvs, dvq in kicks:
                if p <= kp < p + 1 / steps:
                    j.vs += dvs
                    j.vq += dvq
            j.step(dt, **(drive(p) if drive else {}))
    return out


class Drop:
    """A thrown particle: tar droplet (g = G), spark (light, g small) or smoke (buoyant, g < 0).
    Starts at (x, y) at time t0 with velocity (vx, vy) in art px/s (y down); drag pulls its velocity toward
    the wind (air streaming past the body); a heavy one splats flat on the floor. Gone after `life` s."""

    def __init__(self, x, y, vx, vy, r=1.2, t0=0.0, g=G, drag=0.0, wind=(0.0, 0.0), life=9.0, floor=True):
        self.x, self.y, self.vx, self.vy, self.r, self.t0 = x, y, vx, vy, r, t0
        self.g, self.drag, self.wind, self.life, self.floor = g, drag, wind, life, floor

    def at(self, t):
        """(x, y, vx, vy, flat) at time t, None before it is thrown or after its life."""
        tau = t - self.t0
        if tau < 0 or tau > self.life:
            return None
        x, y, vx, vy = self.x, self.y, self.vx, self.vy
        n = max(1, int(tau / 0.002))
        h = tau / n
        for _ in range(n):
            vx += (self.wind[0] - vx) * self.drag * h
            vy += ((self.wind[1] - vy) * self.drag + self.g) * h
            x += vx * h
            y += vy * h
            if self.floor and y >= BASE_Y - 0.5 and vy > 0:
                return x, BASE_Y - 0.5, 0.0, 0.0, True
        return x, y, vx, vy, False


def tar_drops(drops, t):
    """pre fx: droplets drawn in flight (stretched along their motion, like real drops) or as flat splats."""
    def fx(img, px, info):
        for d in drops:
            st = d.at(t)
            if st is None:
                continue
            x, y, vx, vy, flat = st
            r = d.r
            if flat:
                ellipse(px, SIZE, SIZE, x, BASE_Y - 1, r * 1.7, 0.6, TAR[2])
                put(px, SIZE, SIZE, int(x - r * 0.6), BASE_Y - 1, TAR[1])
                continue
            sp = math.hypot(vx, vy)
            tail = int(min(3, sp / 75))
            if tail:
                ux, uy = vx / sp, vy / sp
                for k in range(tail, 0, -1):
                    rr = max(0.5, r * (1 - k / (tail + 1.5)))
                    ellipse(px, SIZE, SIZE, x - ux * k * r * 0.9, y - uy * k * r * 0.9, rr, rr, TAR[2])
            ellipse(px, SIZE, SIZE, x, y, r, r * 1.05, TAR[1])
            put(px, SIZE, SIZE, int(x - r * 0.4), int(y - r * 0.4), TAR[0])
            if r >= 1.5:
                put(px, SIZE, SIZE, int(x + 0.3), int(y + 0.3), EMBER[3])
    return fx


def glow(parts, t, behind=True, cool_rate=7.0, smoke=False):
    """post fx: sparks (Drop, start heat 0 white .. 4 deep red) cooling as they fly; smoke = grey wisps.
    behind: only over empty pixels (they pass behind the body)."""
    def fx(img):
        px = img.load()
        for d, h0 in parts:
            st = d.at(t)
            if st is None or st[4]:
                continue
            x, y = int(st[0]), int(st[1])
            if not (0 <= x < SIZE and 0 <= y < SIZE) or (behind and px[x, y][3] > 0):
                continue
            age = t - d.t0
            if smoke:
                a = int(170 * max(0.0, 1 - age / d.life))
                if a > 20:
                    px[x, y] = (96, 84, 80, a)
                    if d.r >= 1.5 and x + 1 < SIZE and px[x + 1, y][3] == 0:
                        px[x + 1, y] = (80, 70, 68, a // 2)
                continue
            ci = min(4, int(h0 + age * cool_rate))
            px[x, y] = EMBER[ci]
            if d.r >= 1.5 and ci <= 2:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    xx, yy = x + dx, y + dy
                    if 0 <= xx < SIZE and 0 <= yy < SIZE and px[xx, yy][3] == 0:
                        px[xx, yy] = EMBER[min(4, ci + 2)]
    return fx


def ground_air(hx, k=1.0):
    """The air streaming past a body moving along the ground at hx (x MOVE_SPEED, + = the way it faces):
    the same drag as in the jump frames bends the dome back and draws its top out a little, but the foot
    is stuck to the ground, so only the upper body gives."""
    side = min(1.9, abs(hx))
    return dict(tip_top=min(0.4, 0.2 * side * k), tip_from=0.55, sweep_top=-hx * 13 * 0.45 * k)


def balance_lean(hx):
    """px the body leans into its motion: the floor pushes the foot forward while the air drags the top back,
    so to stay upright (no net torque) the mass has to sit ahead of the foot, like a runner into a head wind."""
    return 3.6 * hx


def merge(*forms):
    out = {}
    for f in forms:
        for key, val in (f or {}).items():
            if key in ('sweep_top', 'sweep_bot', 'bulge', 'cup', 'tilt'):
                out[key] = out.get(key, 0.0) + val
            elif key in ('tip_top', 'tip_bot'):
                out[key] = max(out.get(key, 0.0), val)
            else:
                out[key] = val
    return out


def body(st, lift=0.0, form=None, s_add=0.0, q_add=0.0, w_add=0.0, **mood):
    """A frame from a simulated state: stretch -> size at constant area, shear -> lean."""
    w, h = shape(st['s'] + s_add)
    return frame(w + w_add, h, lift=lift, lean=st['q'] + q_add, form=form, **mood)


def strands_in_flow(items, force, sway=0.0):
    """pre fx: tar strands hanging off the bottom of a floating body, lying along the net force on them
    (gravity + drag of the air streaming past, art px/s^2): (nx, base length, wobble phase). A strong force
    stretches them long and straight, a weak one leaves short limp stubs."""
    fx_, fy_ = force
    mag = math.hypot(fx_, fy_)
    ux, uy = fx_ / mag, fy_ / mag
    stretch = math.sqrt(mag / G)

    def fx(img, px, info):
        for nx, base, ph in items:
            x0, _ = slimes.rel(info, nx, 0.12)
            x0 = int(x0)
            y0 = int(info['bottom'])
            while y0 > 0 and px[x0, y0 - 1][3] == 0:
                y0 -= 1
            length = max(1.0, base * stretch)
            px_, py_ = x0 + 0.5, float(y0)
            for k in range(int(length)):
                u = (k + 1) / length
                wob = math.sin(ph + u * 2.4) * sway * u          # flutters more toward the free end
                px_ += ux - uy * wob * 0.35
                py_ += uy + ux * wob * 0.35
                put(px, SIZE, SIZE, int(px_), int(py_), TAR[2])
                if u < 0.45:                                     # thick where it leaves the body, thinning out
                    put(px, SIZE, SIZE, int(px_ + (1 if ux <= 0 else -1)), int(py_), TAR[3])
            if length >= 3:                                      # a bead gathering at the tip
                ellipse(px, SIZE, SIZE, px_, py_ + 0.4 * uy, 0.9, 1.1, TAR[1])
    return fx


def flow_lines(lines):
    """post fx: slanted speed lines (x, y, length, dx per px down, colour index); fade toward the far end."""
    def fx(img):
        px = img.load()
        for x0, y0, length, slope, ci in lines:
            for k in range(int(length)):
                x, y = int(x0 + slope * k), int(y0 + k)
                if 0 <= x < SIZE and 0 <= y < SIZE and px[x, y][3] == 0:
                    if k < length * 0.35 and k % 2:
                        continue
                    c = EMBER[ci]
                    px[x, y] = (c[0], c[1], c[2], int(60 + 180 * k / max(1, length)))
    return fx


# ---------- the jump (shaped by hand, not part of the physics overhaul) ----------
def anim_takeoff():
    """Jump launch (played once when leaving the ground): stretched, tar splashing off the base."""
    return [
        frame(34, 46, eyes='wide', heat=0.5, pre=[droplets((CX - 15, 59, 1.6), (CX + 15, 59, 1.4), (CX - 8, 61, 1))]),
        frame(32, 48, heat=0.4, round=0.5, pre=[droplets((CX - 19, 58, 1.4), (CX + 19, 57, 1.6), (CX - 11, 62, 1))]),
        frame(33, 47, heat=0.3, round=0.9, pre=[droplets((CX - 22, 60, 1.1), (CX + 22, 60, 1.1))]),
    ]


# airflow grid: rows = vertical speed (rising fast .. falling fast), columns = sideways speed relative to the
# way the face looks (backwards fast .. forwards fast). SpriteComponent picks the frame from the velocity
# with the same numbers, so keep them in sync (AIRFLOW_VY / AIRFLOW_VX there).
AIRFLOW_VY = [-620, -400, -180, 0, 180, 400, 620]
AIRFLOW_VX = [-1.0, -0.5, 0.0, 0.5, 1.0]          # x the max move speed (400)


def airflow_frame(vy, hx):
    """The body pulled into a water drop by the air streaming past: the leading end stays round and the
    trailing end draws out into a tail (falling: tail up, rising: tail down), sideways motion sweeps the
    tail back and, when not rising, pulls a tail out of the top like a flame."""
    d = max(-1.0, min(1.0, vy / 620))
    rise, fall = max(0.0, -d), max(0.0, d)
    side = abs(hx)
    # kept soft (a full drop tail looked too pointy): at most ~2/3 of the way from the egg to a drop
    tip_top = min(0.65, 0.6 * fall + 0.4 * side * (1 - rise))
    tip_bot = min(0.65, 0.55 * rise)
    sweep = -hx * 13                                  # the tail trails opposite to the motion (~30 deg at full speed)
    form = dict(tip_top=tip_top, tip_bot=tip_bot,
                sweep_top=sweep * (1 - rise * 0.7), sweep_bot=sweep * rise)
    w = 41 - 8 * abs(d) - 2 * side
    h = 40 + 9 * abs(d) + 3 * side * (1 - abs(d))
    eye_dy = 3 * rise - 4 * fall                     # eyes stay in the round bulb, away from the tail
    return frame(w, h, lean=hx * 1.5, eye_dy=eye_dy, phase=(vy + 620) / 1240, round=1.0, form=form,
                 look=(0, 0.8 * fall - 0.6 * rise), heat=0.3 + 0.25 * abs(d))


def anim_airflow():
    return [airflow_frame(vy, hx) for vy in AIRFLOW_VY for hx in AIRFLOW_VX]



# ---------- grids picked by velocity (SpriteComponent keeps the same tables) ----------
WALK_HX, WALK_N, WALK_STRIDE = [0.6, 0.75, 0.9], 10, 96.0      # stride: game px moved per crawl wave
RUN_HX, RUN_N, RUN_STRIDE = [0.85, 1.0, 1.2], 10, 88.0         # game px per bound
LAND_VY, LAND_HX, LAND_N, LAND_FPS = [250, 480, 750], [0.0, 0.5, 1.0], 8, 20
LEV_VY, LEV_HX, LEV_N = [-300, 0, 300], [0.0, 0.5, 1.0], 6
GLIDE_HX, GLIDE_N = [0.0, 0.5, 1.0], 6
DIVE_HX, DIVE_N = [0.0, 0.5, 1.0], 4


def splats(pts):
    """pre fx: flat flecks of tar lying on the floor: (x, r)."""
    def fx(img, px, info):
        for x, r in pts:
            ellipse(px, SIZE, SIZE, x, BASE_Y - 1, r * 1.7, 0.6, TAR[2])
            put(px, SIZE, SIZE, int(x - r * 0.6), BASE_Y - 1, TAR[1])
    return fx


# ---------- on the ground ----------
def anim_idle(n=12):
    """Breathing: the tar swells up and settles (area kept, so it slims as it rises) while the mass sloshes
    from side to side a beat behind, so the top drifts over the planted foot and the weight shifts in it."""
    frames = []
    for i in range(n):
        p = i / n
        a = 2 * math.pi * p
        st = dict(s=0.035 * math.sin(a), q=0.7 * math.sin(a - 1.1) + 0.25 * math.sin(2 * a + 0.4))
        frames.append(body(st, form=dict(bulge=0.035 * math.sin(a - 2.2)), phase=p, blink=(i == 7),
                           heat=0.3 + 0.12 * (1 + math.sin(a)) / 2))
    return frames


def crawl_cycle(hx, n=WALK_N):
    """One crawl wave at hx x MOVE_SPEED. A slime this size doesn't really leave the ground at walking pace:
    the foot surges forward and back under it (sweep_bot), the upper body is thrown the other way by its
    own inertia through the shear spring (close to resonance, so it swings), the muscles pump it up and down,
    the front fills as the foot reaches out, and the air bends the dome back. A fleck of tar peels off the
    heel and stays on the floor (sliding away at the walking speed)."""
    period = WALK_STRIDE / (hx * MOVE_SPEED)
    om = 2 * math.pi / period
    amp = 1.6 + 2.4 * (hx - 0.6) / 0.3                 # foot surge, art px
    foot = lambda p: amp * math.sin(2 * math.pi * p)
    # q'' = -kq q - cq q' - foot'': the surge of the foot is a pseudo-force on the top
    drive = lambda p: dict(fq=amp * om * om * math.sin(2 * math.pi * p),
                           fs=16 * (0.8 + hx) * math.sin(2 * math.pi * p - 0.6))
    states = periodic(n, period, drive)
    slide = hx * MOVE_SPEED / 2                        # art px/s the floor moves past
    heel_p = 0.72
    frames = []
    for st in states:
        p = st['p']
        b = foot(p)
        flecks = []
        for back in (0, 1):                             # the latest fleck and the one before
            age = ((p - heel_p) % 1 + back) * period
            x = CX - W0 / 2 + 1 + foot(heel_p) - slide * age
            if x > -3:
                flecks.append((x, 1.0 + 0.2 * hx))
        form = merge(ground_air(hx), dict(sweep_bot=b, bulge=0.12 * amp / 3 * math.sin(2 * math.pi * p + 0.3)))
        frames.append(body(st, form=form, q_add=b + balance_lean(hx), phase=p, look=(0.4, 0),
                           heat=0.32 + 0.12 * hx + 0.08 * math.sin(2 * math.pi * p),
                           pre=[splats(flecks)]))
    return frames


def anim_walk():
    return [f for hx in WALK_HX for f in crawl_cycle(hx)]


def bound_cycle(hx, n=RUN_N):
    """One bound at full pace: the body lands on the front of its foot (squashed, the top carried on over
    the stuck foot), loads up and pushes off (stretch, the top lags as the foot shoots forward), then is
    off the ground for a moment, its bottom rounding as it unloads; the hop height is what the game's
    gravity allows in that time (under a px), the rest is the body stretching. Sparks are kicked back off
    the heel at the push and fly on ballistically."""
    period = RUN_STRIDE / (hx * MOVE_SPEED)
    contact = 0.5
    tf = (1 - contact) * period
    vy0 = G * tf / 2
    reach = 2.6
    foot = lambda p: reach * math.cos(2 * math.pi * p)
    push = lambda p: math.sin(math.pi * p / contact) if p < contact else 0.0
    drive = lambda p: dict(fs=60 * push(p) * hx, fq=-330 * push(p) * hx)
    states = periodic(n, period, drive, kicks=[(0.0, -3.4 * hx, 46 * hx)])
    sparks_ = []
    for k, (vx, vy, r, h0) in enumerate([(-95, -70, 1.6, 1), (-140, -40, 1, 2), (-70, -105, 1, 2), (-120, -85, 1, 3)]):
        t0 = (0.32 + 0.05 * k) * period
        for back in (0, 1):                             # last stride's sparks are still flying
            sparks_.append((Drop(CX - W0 / 2 - 1, BASE_Y - 2, vx * hx, vy, r, t0=t0 - back * period, g=G * 0.5,
                                 life=period * 1.3), h0))
    frames = []
    for st in states:
        p = st['p']
        b = foot(p)
        lift, rnd = 0.0, 0.0
        if p >= contact:
            tau = (p - contact) * period
            lift = vy0 * tau - G * tau * tau / 2
            rnd = 0.7 * math.sin(math.pi * (p - contact) / (1 - contact))
        form = merge(ground_air(hx, 1.15), dict(sweep_bot=b, bulge=0.15 * math.cos(2 * math.pi * p),
                                                round=rnd))
        frames.append(body(st, lift=lift, form=form, q_add=b + balance_lean(hx * 1.2), round=rnd, phase=p, eyes='fierce', look=(0.6, 0),
                           heat=0.45 + 0.3 * max(0.0, -st['s']) * 3,
                           post=[glow(sparks_, st['t'])]))
    return frames


def anim_run():
    return [f for hx in RUN_HX for f in bound_cycle(hx)]


def land_variant(vy, hx, n=LAND_N, fps=LAND_FPS):
    """Touch-down at fall speed vy (game px/s) moving hx forward. It arrives in the jump's airflow shape
    (tall, round bottom, tail up and back); the bottom flattens at once, the fall drives the spring down
    (vs ~ fall speed) and the forward motion shears the top on over the stuck foot. The drop tail snaps back
    in a few hundredths of a second, a ripple runs up the skin and, on a hard landing, tar splashes off the
    edges and flies ballistically before splatting on the floor."""
    d = min(1.4, vy / 620)
    tip0 = min(0.65, 0.6 * d + 0.4 * hx)
    j = Jelly(s=0.1 * d + 0.03, q=hx * 1.5, vs=-(2.5 + 10.5 * d), vq=hx * 55)
    states = simulate(n, fps, j)
    drops = []
    if vy > 300:
        sp = 40 + 90 * d
        for side, r, a in [(-1, 1.5, 0.75), (1, 1.4, 0.7), (-1, 1.0, 1.0), (1, 1.1, 1.05)][:2 if vy < 600 else 4]:
            drops.append(Drop(CX + side * 21, BASE_Y - 2, (side * math.cos(a) + 0.5 * hx) * sp, -math.sin(a) * sp, r))
    sparks_ = [(Drop(CX + dx, BASE_Y - 3, vx, -vy_, 1.6 if k == 0 else 1, g=G * 0.4, life=0.4), k % 2)
               for k, (dx, vx, vy_) in enumerate([(-10, -50, 90), (12, 60, 80), (2, 10, 120)])] if vy > 600 else []
    frames = []
    for i, st in enumerate(states):
        t = st['t']
        snap = math.exp(-t / 0.035)
        form = merge(ground_air(hx * (1 - snap)),
                     dict(tip_top=tip0 * snap, sweep_top=-hx * 13 * snap,
                          ripple=(0.07 * d * math.exp(-t / 0.15), 1.4, t * 38)))
        rnd = 0.35 if i == 0 else 0.0
        frames.append(body(st, form=form, round=rnd, phase=t, eyes='squint' if d > 0.6 and t < 0.1 else 'normal',
                           heat=0.3 + 0.7 * d * math.exp(-t / 0.12),
                           pre=[tar_drops(drops, t)], post=[glow(sparks_, t)]))
    return frames


def anim_land():
    return [f for vy in LAND_VY for hx in LAND_HX for f in land_variant(vy, hx)]


def anim_dash(n=6, fps=30):
    """Leather Gloves dodge (0.2 s at 750 px/s, almost twice the run): the foot shoots off and the top is
    left behind for a moment, then the body rolls forward into the head wind (balance_lean at that speed)
    and the wind presses it into a drop lying on its side - round head in front, the back tapering low
    behind, its top rippling as the air sheds off it - and tar torn off the back is blown away."""
    j = Jelly(s=-0.1, q=-4, vq=-30)
    states = simulate(n, fps, j, drive=lambda t: dict(s_rest=-0.24, q_rest=balance_lean(750 / MOVE_SPEED)))
    wind = (-375, 0)
    drops = [Drop(CX - 18, BASE_Y - 14, -40, -25, 1.3, t0=0.02, drag=14, wind=wind),
             Drop(CX - 22, BASE_Y - 8, -30, -10, 1.0, t0=0.09, drag=14, wind=wind)]
    frames = []
    for i, st in enumerate(states):
        t = st['t']
        flutter = 2.5 * math.sin(2 * math.pi * 14 * t)
        back = CX - 27
        lines = [(back - 22 + 3 * (i % 2), back + 2, 44, 3), (back - 28 + 2 * (i % 3), back + 4, 50, 2),
                 (back - 18 - 2 * (i % 2), back + 4, 56, 3), (back - 12, back + 2, 60, 4)]
        form = dict(tip_top=0.25, tip_from=0.4, sweep_top=-4 + flutter, bulge=0.22, sweep_bot=-2)
        frames.append(body(st, lift=1, form=form, round=0.25, phase=t, w_add=1, eyes='squint', look=(0.8, 0), heat=0.7,
                           pre=[tar_drops(drops, t)],
                           post=[streaks(*lines), sparks((back - 4 - 3 * i, 47 + i % 3, 1, 1))]))
    return frames


# ---------- one-shots ----------
def anim_hurt(n=6, fps=14):
    """A hit from the front: the impulse squeezes the body and knocks the top back, it rings back on its
    springs; tar splashes off the struck face and the whiplash throws more off the back."""
    states = simulate(n, fps, Jelly(vs=-5.5, vq=-95))
    drops = [Drop(CX + 16, BASE_Y - 26, 45, -95, 1.3), Drop(CX + 12, BASE_Y - 32, 15, -120, 1.0),
             Drop(CX - 18, BASE_Y - 30, -110, -60, 1.6), Drop(CX - 14, BASE_Y - 36, -80, -95, 1.1)]
    sparks_ = [(Drop(CX + 10, BASE_Y - 28, 70, -60, 2, g=G * 0.3, life=0.3), 0),
               (Drop(CX + 6, BASE_Y - 32, 30, -90, 1, g=G * 0.3, life=0.3), 1)]
    frames = []
    for i, st in enumerate(states):
        t = st['t']
        eyes = 'shut' if i < 3 else 'squint' if i < 4 else 'normal'
        frames.append(body(st, form=dict(ripple=(0.08 * math.exp(-t / 0.2), 1.5, t * 35),
                                         sweep_top=-st['vq'] * 0.04),
                           phase=t, eyes=eyes, heat=0.3 + 0.7 * math.exp(-t / 0.18),
                           pre=[tar_drops(drops, t)], post=[glow(sparks_, t, behind=False)]))
    return frames


def anim_death(n=14, fps=9):
    """Knocked out, then the tar gives up holding its shape and flows out under its own weight like a
    viscous puddle (fast at first, then creeping - the spread slows as it thins), cooling from molten to
    dead stone while trapped gas bubbles up and pops and smoke drifts off."""
    states = simulate(n, fps, Jelly(vs=-6.5, vq=-110))
    t1 = 2 / fps
    drops = [Drop(CX - 18, BASE_Y - 30, -110, -60, 1.6), Drop(CX + 14, BASE_Y - 28, 40, -100, 1.2)]
    smoke = [(Drop(CX + dx, BASE_Y - 8 - dy, wx, -14, 1.6 if k % 2 else 1, t0=t0, g=-30, drag=1.5,
                   wind=(wx, -20), life=0.9, floor=False), 0)
             for k, (dx, dy, wx, t0) in enumerate([(-6, 4, 6, 0.5), (8, 2, -5, 0.7), (0, 0, 8, 0.95),
                                                   (-12, 0, 4, 1.1), (10, 0, -6, 1.25)])]
    embers_ = [(Drop(CX + dx, BASE_Y - 12, vx, -40, 1, t0=t0, g=-20, drag=1, wind=(vx, -25), life=0.6,
                     floor=False), 2)
               for dx, vx, t0 in [(-4, 8, 0.4), (6, -6, 0.55), (2, 4, 0.75)]]
    frames = []
    for i, st in enumerate(states):
        t = st['t']
        w, h = shape(st['s'])
        u = 0.0 if t < t1 else 1 - (1 + (t - t1) / 0.22) ** -1.6
        w, h = w + (61 - w) * u, h + (6 - h) * u
        heat_ = max(0.0, 1 - t * 1.4)
        cool = min(1.0, max(0.0, (t - 0.25) * 0.9))
        eyes = 'shut' if i < 2 else 'dim' if i < 4 else 'off'
        pre = [tar_drops(drops, t)]
        if i in (6, 7, 9):
            pre.append(bubble({6: 1.0, 7: 1.8, 9: 1.4}[i]))
        frames.append(frame(w, h, lean=st['q'] * (1 - u),
                            form=dict(ripple=(0.06 * math.exp(-t / 0.4) * (1 - u), 1.3, t * 30)),
                            phase=t, eyes=eyes, heat=heat_, cool=cool, pre=pre,
                            post=[glow(embers_, t), glow(smoke, t, smoke=True)]))
    return frames


def anim_attack(n=8, fps=18):
    """Melee swing: coil back and down (loading the springs), then let go - the top is thrown forward past
    its rest, its own speed through the air bending the dome back against the motion - and ring out.
    Tar flies off the front at the strike."""
    def drive(t):
        if t < 0.12:
            return dict(s_rest=-0.14, q_rest=-6)
        if t < 0.24:
            return dict(s_rest=0.1, q_rest=7)
        return {}
    states = simulate(n, fps, drive=drive, kicks=[(0.12, 2.5, 190)])
    drops = [Drop(CX + 18, BASE_Y - 30, 150, -70, 1.2, t0=0.16), Drop(CX + 14, BASE_Y - 36, 110, -100, 1.0, t0=0.17)]
    frames = []
    for i, st in enumerate(states):
        t = st['t']
        drag = max(-8.0, min(8.0, -st['vq'] * 0.05))
        form = dict(sweep_top=drag, tip_top=min(0.3, abs(st['vq']) / 600), tip_from=0.5)
        frames.append(body(st, form=form, phase=t, eyes='fierce' if i < 6 else 'normal',
                           heat=0.45 + 0.55 * math.exp(-((t - 0.15) / 0.07) ** 2),
                           pre=[tar_drops(drops, t)]))
    return frames


def anim_shoot(n=5, fps=16):
    """Bow / crossbow / trident: the recoil kicks the top back and the body rings back on its springs."""
    states = simulate(n, fps, Jelly(vs=-2.0, vq=-60))
    return [body(st, form=dict(sweep_top=-st['vq'] * 0.04), phase=st['t'], eyes='squint' if i == 0 else 'normal',
                 heat=0.3 + 0.25 * math.exp(-st['t'] / 0.1)) for i, st in enumerate(states)]


def anim_cast(n=8, fps=14):
    """Spell: the heat rising in the tar makes it swell up (thermal expansion), embers boil off the top and
    rise on the hot air, swirling; on the release the body drops back and a ripple runs over it."""
    states = simulate(n, fps, drive=lambda t: dict(s_rest=0.17 if t < 0.36 else 0.0), kicks=[(0.36, -3.0, 0)])
    embers_ = [(Drop(CX + dx, BASE_Y - 44, vx, -30, r, t0=t0, g=-170, drag=2.0, wind=(vx * 0.3, -40), life=0.45,
                     floor=False), h0)
               for dx, vx, r, t0, h0 in [(-5, -10, 1, 0.07, 1), (6, 12, 2, 0.12, 0), (0, 3, 1, 0.18, 1),
                                         (-10, -18, 1, 0.22, 2), (9, 16, 1, 0.27, 1), (-2, -4, 2, 0.32, 0),
                                         (-13, -30, 1, 0.36, 0), (13, 30, 1, 0.36, 0)]]
    frames = []
    for i, st in enumerate(states):
        t = st['t']
        eyes = 'squint' if i == 0 else 'wide' if i < 5 else 'normal'
        rip = (0.06 * math.exp(-(t - 0.36) / 0.15), 1.5, (t - 0.36) * 36) if t >= 0.36 else None
        frames.append(body(st, form=dict(ripple=rip) if rip else None, phase=t, eyes=eyes,
                           heat=min(1.0, 0.5 + t * 2.2) if t < 0.36 else 0.4 + 0.6 * math.exp(-(t - 0.36) / 0.1),
                           post=[glow(embers_, t, behind=False, cool_rate=5)]))
    return frames


def anim_slam(n=10, fps=16):
    """Hammer slam landing at 900 px/s: a huge splat - the impact flattens the body to the limit and
    throws a crown of tar out at low angles that lands and splats - then it rebounds tall and wobbles out."""
    states = simulate(n, fps, Jelly(s=-0.25, vs=-11.0))
    drops = []
    for side in (-1, 1):
        for r, a, sp, x in [(1.7, 1.2, 150, 15), (1.3, 1.35, 165, 11), (1.0, 1.05, 125, 19), (1.2, 1.45, 135, 7)]:
            drops.append(Drop(CX + side * x, BASE_Y - 10, side * math.cos(a) * sp, -math.sin(a) * sp, r))
    sparks_ = [(Drop(CX + dx, BASE_Y - 4, vx, -vy, 2 if k < 2 else 1, g=G * 0.35, life=0.45), k % 2)
               for k, (dx, vx, vy) in enumerate([(-12, -60, 120), (14, 70, 110), (0, 5, 150), (-20, -110, 60),
                                                (22, 120, 55)])]
    frames = []
    for i, st in enumerate(states):
        t = st['t']
        frames.append(body(st, form=dict(ripple=(0.09 * math.exp(-t / 0.22), 1.6, t * 34)), phase=t,
                           eyes='squint' if i < 3 else 'wide' if i < 4 else 'normal',
                           heat=0.3 + 0.7 * math.exp(-t / 0.18), pre=[tar_drops(drops, t)],
                           post=[glow(sparks_, t, behind=False)]))
    return frames


# ---------- held ----------
def anim_block(n=6):
    """Metal Gloves block: the tar hardened to stone - braced back, weight in the rear, and so stiff only a
    fine tremble runs through it (high and fast, a stiff spring)."""
    frames = []
    for i in range(n):
        p = i / n
        st = dict(s=-0.09 + 0.008 * math.sin(2 * math.pi * 3 * p), q=-2 + 0.3 * math.sin(2 * math.pi * 2 * p))
        frames.append(body(st, form=dict(bulge=-0.06), phase=p, eyes='squint', heat=0.0, cool=0.75))
    return frames


def anim_charge(n=6):
    """Charging (bow draw, zweihander, fireball), picked by progress: the body loads like a spring - lower,
    wider, leaning back - and trembles more as the strain grows, while embers spiral in on it."""
    frames = []
    for i in range(n):
        p = i / (n - 1)
        st = dict(s=-0.16 * p, q=-3.5 * p + 0.45 * p * p * (-1) ** i)
        ring = []
        for k in range(int(2 + p * 7)):
            a = k / 9 * 2 * math.pi + p * 4.0
            rad = 27 - 11 * p
            for tr in range(3 if p > 0.3 else 1):           # short trail behind each, curving in
                aa, rr = a - tr * 0.18, rad + tr * 1.6
                ring.append((CX + math.cos(aa) * rr, BASE_Y - 20 + math.sin(aa) * rr * 0.8,
                             2 if tr == 0 and k % 3 == 0 and p > 0.8 else 1,
                             min(4, (0 if p > 0.8 else 1) + tr + k % 2)))
        frames.append(body(st, form=dict(bulge=-0.06 * p), phase=0.25, eyes='squint' if p < 0.5 else 'fierce',
                           heat=0.2 + 0.8 * p, post=[sparks(*ring)]))
    return frames


def anim_fidget(n=24, fps=10):
    """Idle variant after standing still: glances back (the weight shifts back with the look) and forward,
    then looks up as gas swells a bubble on its head, which pops - the skin snaps back, a ripple runs down,
    droplets are thrown up and fall back."""
    def drive(t):
        f = t * fps
        if 2 <= f < 7:
            return dict(q_rest=-2.2)
        if 7 <= f < 10:
            return dict(q_rest=1.4)
        if 10 <= f < 17:
            return dict(s_rest=0.03)
        return {}
    states = simulate(n, fps, drive=drive, kicks=[(17 / fps, -2.5, 0)])
    frames = []
    t_pop = 17 / fps
    for i, st in enumerate(states):
        t = st['t']
        w, h = shape(st['s'])
        mood = dict(heat=0.3)
        if 2 <= i < 7:
            mood['look'] = (-1.3, 0)
        elif 7 <= i < 10:
            mood['look'] = (1.0, 0)
        elif 10 <= i < 17:
            mood['look'] = (0, -1)
            mood['pre'] = [bubble(1.0 + 2.8 * ((i - 10) / 6) ** 1.3)]
        top_x, top_y = CX - 3 + st['q'] * 0.6, BASE_Y - h - 2
        drops = [Drop(top_x - 2, top_y, -35, -95, 1.0, t0=t_pop), Drop(top_x + 2, top_y, 40, -80, 1.0, t0=t_pop),
                 Drop(top_x, top_y - 1, 5, -120, 0.9, t0=t_pop)]
        if i >= 17:
            mood['pre'] = [tar_drops(drops, t)]
            mood['form'] = dict(ripple=(0.07 * math.exp(-(t - t_pop) / 0.25), 1.5, (t - t_pop) * 30))
            if i < 19:
                mood['eyes'] = 'wide'
                mood['heat'] = 0.6
        if i == 17:
            mood['post'] = [sparks((top_x, top_y - 2, 2, 0))]
        elif i == 18:
            mood['post'] = [sparks((top_x + 1, top_y - 7, 1, 2), (top_x - 3, top_y - 5, 1, 3))]
        frames.append(body(st, phase=i / n, blink=(i == 21), **mood))
    return frames


# ---------- in the air (not the jump) ----------
def levitate_frame(vy, hx, i, n=LEV_N):
    """Levitating at vy (game px/s, - = up) moving hx forward. The body takes the jump's airflow shape at
    this speed; the hanging strands lie along gravity + the drag of the air streaming past (long and taut
    rising, short and limp sinking, trailing back moving sideways) and drifting embers are carried off by
    the same flow."""
    p = i / n
    a = 2 * math.pi * p
    d = vy / 620
    rise, fall = max(0.0, -d), max(0.0, d)
    side = abs(hx)
    st = dict(s=0.12 * abs(d) + 0.025 * math.sin(a), q=hx * 1.2)
    form = dict(tip_top=min(0.6, 0.6 * fall + 0.35 * side * (1 - rise)), tip_bot=min(0.6, 0.55 * rise),
                sweep_top=-hx * 13 * (1 - rise * 0.7), sweep_bot=-hx * 13 * rise)
    air_x, air_y = -hx * MOVE_SPEED / 2, -vy / 2          # air moving past the body, art px/s
    force = (air_x * 2, G + air_y * 2)
    strands = strands_in_flow([(-0.45, 6, a), (0.05, 4, a + 2), (0.5, 5.2, a + 4)], force, sway=1.4)
    t = p * n / 8
    embers_ = []
    for k, (dx, off) in enumerate([(-7, 0.0), (8, 0.35), (1, 0.7)]):
        age = ((off + p) % 1) * n / 8                   # loops with the strip
        vx, vy_ = air_x * 0.6, air_y * 0.6 + 25
        embers_.append((Drop(CX + dx, BASE_Y - 9 - 1.5 * math.sin(a), vx, vy_, 1, t0=t - age, g=0,
                             floor=False), 3 + k % 2))
    lift = 9 + 1.5 * math.sin(a)
    return body(st, lift=lift, form=form, round=0.85, phase=p, eye_dy=3 * rise - 4 * fall,
                look=(0, 0.8 * fall - 0.6 * rise), heat=0.4 + 0.15 * math.sin(a),
                pre=[strands], post=[glow([(e, h) for e, h in embers_], t + 0.0, cool_rate=0)])


def anim_levitate():
    return [levitate_frame(vy, hx, i) for vy in LEV_VY for hx in LEV_HX for i in range(LEV_N)]


def glide_frame(hx, i, n=GLIDE_N):
    """Slow fall (120 px/s), spread wide like a canopy: the updraft lifts the soft edges into a cup that
    flutters, moving sideways it tilts its front edge up into the wind like a wing and the dome is pushed
    back, and the edge strands stream up and back with the air."""
    p = i / n
    a = 2 * math.pi * p
    w, h = 54 + math.sin(a), 24 - 0.8 * math.sin(a)
    form = dict(cup=8.5 + 1.5 * math.sin(a), tilt=2.6 * hx + 0.6 * math.sin(a + 1.3) * (0.3 + hx),
                sweep_top=-hx * 8)
    force = (-hx * MOVE_SPEED / 2 * 2, G - 60 * 2)
    strands = strands_in_flow([(-0.8, 5, a), (-0.2, 3, a + 3), (0.75, 4.2, a + 1.7)], force, sway=1.2)
    return frame(w, h, lift=8, lean=-hx * 1.5, phase=p, round=0.6, form=form, look=(0.3 * hx, 0.6), heat=0.3,
                 pre=[strands])


def anim_glide():
    return [glide_frame(hx, i) for hx in GLIDE_HX for i in range(GLIDE_N)]


def dive_frame(hx, i, n=DIVE_N):
    """Hammer slam fall at 900 px/s - past the fastest jump frame: drawn out into a long falling drop whose
    tail flutters as the air sheds off it (vortex shedding), swept back by any sideways speed, with tar
    stripped off the tail tip and speed lines slanted along the flow."""
    p = i / n
    a = 2 * math.pi * p
    flutter = 1.6 * math.sin(a)
    form = dict(tip_top=0.72, sweep_top=-hx * 13 * 1.2 + flutter)
    top = BASE_Y - 54
    slope = hx * 0.45                                    # the air comes up and from the front
    lines = [(CX - 7 - slope * 14, max(0, top - 14 + 3 * (i % 2)), 13, slope, 3),
             (CX + 6 - slope * 12, max(0, top - 10 - 3 * (i % 2)), 11, slope, 2),
             (CX - 1 - slope * 8, max(0, top - 6), 5, slope, 1),
             (CX - 13, top + 8 + 4 * (i % 2), 9, slope, 4), (CX + 12, top + 6 - 2 * (i % 2), 10, slope, 3)]
    tip_x = CX - hx * 15 + flutter
    t = p * n / 14
    drops = [Drop(tip_x + 1, top + 2 - 9 * ((p + off) % 1) * 2, -hx * 30, 0, r, g=0, floor=False)
             for off, r in ((0.0, 1.0), (0.5, 0.8))]
    return frame(27 - 2 * hx, 54, lean=hx * 1.5, eyes='squint', look=(0, 1), round=1.0, heat=0.85, phase=p,
                 form=form, pre=[tar_drops(drops, t)], post=[flow_lines(lines)])


def anim_dive():
    return [dive_frame(hx, i) for hx in DIVE_HX for i in range(DIVE_N)]


ANIMS = {
    'idle': anim_idle, 'walk': anim_walk, 'land': anim_land,
    'takeoff': anim_takeoff, 'run': anim_run, 'dash': anim_dash, 'hurt': anim_hurt, 'death': anim_death,
    'attack': anim_attack, 'shoot': anim_shoot, 'cast': anim_cast, 'levitate': anim_levitate,
    'glide': anim_glide, 'dive': anim_dive, 'slam': anim_slam, 'block': anim_block, 'charge': anim_charge,
    'fidget': anim_fidget, 'airflow': anim_airflow,
}


def generate(out_dir):
    for name, fn in ANIMS.items():
        strip(fn()).save(f'{out_dir}/Player_{name}.png')


def sheet(out, names=None, cols=10):
    """Player strips on a dark backdrop, x3, one block per animation (grids: one row per column/row)."""
    from PIL import Image, ImageDraw, ImageFont
    rows = []
    for name, fn in ANIMS.items():
        if names and name not in names:
            continue
        frames = fn()
        per = {'walk': WALK_N, 'run': RUN_N, 'land': LAND_N, 'levitate': LEV_N, 'glide': GLIDE_N,
               'dive': DIVE_N, 'airflow': 5}.get(name, cols)
        for k in range(0, len(frames), per):
            rows.append((name if k == 0 else '', frames[k:k + per], len(frames)))
    s = 3
    cell = FRAME * s
    label = 150
    width = max(len(f) for _, f, _ in rows)
    img = Image.new('RGBA', (label + cell * width, cell * len(rows)), (24, 10, 14, 255))
    d = ImageDraw.Draw(img)
    font = ImageFont.load_default(size=20)
    for r, (name, frames, total) in enumerate(rows):
        y = r * cell
        if name:
            d.text((12, y + cell // 2 - 10), f'{name} ({total})', font=font, fill=(240, 232, 220))
            d.line([(0, y), (img.width, y)], fill=(90, 40, 30), width=2)
        for i, f in enumerate(frames):
            x0 = label + i * cell
            d.rectangle([x0, y, x0 + cell - 1, y + cell - 1], fill=(48, 18, 22) if (i + r) % 2 else (40, 14, 18))
            img.alpha_composite(f.resize((cell, cell), Image.NEAREST), (x0, y))
    img.save(out)


if __name__ == '__main__':
    if len(sys.argv) >= 3 and sys.argv[1] == 'sheet':
        sheet(sys.argv[2], sys.argv[3].split(',') if len(sys.argv) > 3 else None)
    else:
        print(__doc__)
