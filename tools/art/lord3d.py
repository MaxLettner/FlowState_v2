"""Pit Lord 3D redesign: a tiny software renderer that builds the boss out of real 3D primitives and lights it.

Concept stage (2026-11-20): one idle frame of a redesigned Pit Lord, rendered in three styles so the user can pick:
  prerender  smooth light + specular, posterised with ordered dithering (old pre-rendered 3D sprites)
  cel        three tone bands per material, hard specular, hard lava rim from below, outline + inner depth lines
  faceted    normals snapped to facets, flat-shaded low-poly look, crisp outline

The renderer is orthographic, looking down -z (z grows toward the viewer). Primitives write depth, a unit normal and
a material into a z-buffer: spheres, rotated ellipsoids, tapered tubes (sphere sweeps along 3D polylines/beziers)
and bevelled plates. Shading is a separate pass, so the same model renders in every style.

Frame 352x280, anchor (176, 269) like DemonType.LORD; pure Python + PIL (no numpy).

    python tools/art/lord3d.py concepts <out.png>     the style comparison sheet
    python tools/art/lord3d.py generate <dir>         all DemonLord_* strips (style A); generate_all.py calls generate()
"""
import math
import sys

from PIL import Image

W, H = 352, 280
GROUND = 268                    # feet rest on this pixel row (anchor y 269)
MODEL_ANCHOR = (176, GROUND)    # the model's own pivot: built around it, placed at the scene's anchor


# =====================================================================================================
# vector helpers
# =====================================================================================================
def norm(v):
    l = math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]) or 1.0
    return (v[0] / l, v[1] / l, v[2] / l)


def dot(a, b):
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def lerp3(a, b, t):
    return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t)


def bez3(p0, p1, p2, n=12):
    out = []
    for i in range(n + 1):
        t = i / n
        a, b = lerp3(p0, p1, t), lerp3(p1, p2, t)
        out.append(lerp3(a, b, t))
    return out


def clamp(v, lo=0.0, hi=1.0):
    return lo if v < lo else hi if v > hi else v


# =====================================================================================================
# materials
# =====================================================================================================
class Mat:
    def __init__(self, name, base, spec=0.2, shiny=12, emissive=False, metal=False):
        self.name, self.base, self.spec, self.shiny, self.emissive, self.metal = name, base, spec, shiny, emissive, metal


def rgb(h):
    h = h.lstrip('#')
    return (int(h[0:2], 16) / 255, int(h[2:4], 16) / 255, int(h[4:6], 16) / 255)


SKIN = Mat('skin', rgb('#b8382c'), spec=0.25, shiny=10)
SKIN_DARK = Mat('skin_dark', rgb('#7a1e1c'), spec=0.15, shiny=8)
FUR = Mat('fur', rgb('#6a4234'), spec=0.08, shiny=5)
IRON = Mat('iron', rgb('#3a3f4c'), spec=0.9, shiny=28, metal=True)
STEEL = Mat('steel', rgb('#5d6574'), spec=1.0, shiny=40, metal=True)
HORN = Mat('horn', rgb('#d8c49a'), spec=0.45, shiny=18)
HOOF = Mat('hoof', rgb('#1e1a1e'), spec=0.5, shiny=20)
BONE = Mat('bone', rgb('#e6dcc4'), spec=0.3, shiny=14)
WOOD = Mat('wood', rgb('#4a2c1a'), spec=0.1, shiny=6)
LAVA = Mat('lava', rgb('#ffb030'), emissive=True)
EYE = Mat('eye', rgb('#fff2a0'), emissive=True)
FIRE = Mat('fire', rgb('#ff8a20'), emissive=True)


# =====================================================================================================
# z-buffer scene
# =====================================================================================================
class Scene:
    def __init__(self, w=W, h=H, scale=1.0, anchor=(176, GROUND)):
        self.w, self.h = w, h
        self.S, self.ax, self.ay = scale, anchor[0], anchor[1]
        n = w * h
        self.z = [-1e9] * n
        self.n = [None] * n
        self.m = [None] * n
        self.oid = [0] * n
        self.heat = [0.0] * n          # emissive brightness for emissive materials
        self._next = 1

    def T(self, p):
        """Model -> frame coordinates: the model is scaled about the anchor (feet stay on the ground row)."""
        return (self.ax + (p[0] - MODEL_ANCHOR[0]) * self.S, self.ay + (p[1] - MODEL_ANCHOR[1]) * self.S, p[2] * self.S)

    def new_id(self):
        self._next += 1
        return self._next

    def _put(self, x, y, z, nrm, mat, oid, heat=0.0):
        i = y * self.w + x
        if z > self.z[i]:
            self.z[i], self.n[i], self.m[i], self.oid[i], self.heat[i] = z, nrm, mat, oid, heat

    def _box(self, cx, cy, r):
        return (max(0, int(cx - r - 1)), min(self.w - 1, int(cx + r + 1)),
                max(0, int(cy - r - 1)), min(self.h - 1, int(cy + r + 1)))

    def sphere(self, c, r, mat, oid=None, heat=1.0, raw=False):
        oid = oid or self.new_id()
        if not raw:
            c, r = self.T(c), r * self.S
        cx, cy, cz = c
        x0, x1, y0, y1 = self._box(cx, cy, r)
        rr = r * r
        for y in range(y0, y1 + 1):
            dy = y + 0.5 - cy
            for x in range(x0, x1 + 1):
                dx = x + 0.5 - cx
                d2 = dx * dx + dy * dy
                if d2 > rr:
                    continue
                dz = math.sqrt(rr - d2)
                self._put(x, y, cz + dz, (dx / r, dy / r, dz / r), mat, oid, heat)
        return oid

    def ellipsoid(self, c, rx, ry, rz, ang, mat, oid=None, heat=1.0):
        """Ellipsoid with radii (rx, ry, rz), rotated by ang in the screen plane."""
        oid = oid or self.new_id()
        c = self.T(c)
        rx, ry, rz = rx * self.S, ry * self.S, rz * self.S
        cx, cy, cz = c
        ca, sa = math.cos(ang), math.sin(ang)
        x0, x1, y0, y1 = self._box(cx, cy, max(rx, ry))
        for y in range(y0, y1 + 1):
            dy = y + 0.5 - cy
            for x in range(x0, x1 + 1):
                dx = x + 0.5 - cx
                u = dx * ca + dy * sa
                v = -dx * sa + dy * ca
                q = (u / rx) ** 2 + (v / ry) ** 2
                if q > 1:
                    continue
                w = rz * math.sqrt(1 - q)
                nu, nv, nw = u / (rx * rx), v / (ry * ry), w / (rz * rz)
                nx, ny = nu * ca - nv * sa, nu * sa + nv * ca
                self._put(x, y, cz + w, norm((nx, ny, nw)), mat, oid, heat)
        return oid

    def capsule(self, a, b, ra, rb, mat, oid, heat=1.0):
        """Tapered capsule between frame-space points a and b: a smooth round tube with domed ends."""
        ax, ay, az = a
        bx, by, bz = b
        vx, vy = bx - ax, by - ay
        l2 = vx * vx + vy * vy
        r = max(ra, rb)
        x0, x1 = max(0, int(min(ax, bx) - r - 1)), min(self.w - 1, int(max(ax, bx) + r + 1))
        y0, y1 = max(0, int(min(ay, by) - r - 1)), min(self.h - 1, int(max(ay, by) + r + 1))
        for y in range(y0, y1 + 1):
            py = y + 0.5
            for x in range(x0, x1 + 1):
                px = x + 0.5
                t = clamp(((px - ax) * vx + (py - ay) * vy) / l2) if l2 > 0 else 0.0
                cx, cy = ax + vx * t, ay + vy * t
                rr = ra + (rb - ra) * t
                dx, dy = px - cx, py - cy
                d2 = dx * dx + dy * dy
                if d2 > rr * rr:
                    continue
                dz = math.sqrt(rr * rr - d2)
                self._put(x, y, az + (bz - az) * t + dz, (dx / rr, dy / rr, dz / rr), mat, oid, heat)

    def tube(self, pts, r0, r1, mat, oid=None, heat=1.0):
        """A smooth tube along a 3D polyline (model space), radius r0 -> r1 along its length."""
        oid = oid or self.new_id()
        segs = [math.dist(pts[i], pts[i + 1]) for i in range(len(pts) - 1)]
        total = sum(segs) or 1.0
        done = 0.0
        for i, sl in enumerate(segs):
            ra = (r0 + (r1 - r0) * done / total) * self.S
            done += sl
            rb = (r0 + (r1 - r0) * done / total) * self.S
            self.capsule(self.T(pts[i]), self.T(pts[i + 1]), ra, rb, mat, oid, heat)
        return oid

    def plate(self, poly, z, mat, nrm=(0.0, 0.0, 1.0), bevel=3.0, thick=4.0, oid=None, heat=1.0):
        """A flat plate (blade) given by a 2D polygon at depth z, facing nrm, with bevelled edges."""
        oid = oid or self.new_id()
        poly = [self.T((p[0], p[1], 0))[:2] for p in poly]
        z, bevel, thick = z * self.S, bevel * self.S, thick * self.S
        xs, ys = [p[0] for p in poly], [p[1] for p in poly]
        n0 = norm(nrm)
        edges = list(zip(poly, poly[1:] + poly[:1]))
        # outward edge normals (polygon winding independent)
        cxp, cyp = sum(xs) / len(xs), sum(ys) / len(ys)
        eout = []
        for (ax, ay), (bx, by) in edges:
            ex, ey = by - ay, -(bx - ax)
            l = math.hypot(ex, ey) or 1
            ex, ey = ex / l, ey / l
            mx, my = (ax + bx) / 2, (ay + by) / 2
            if (mx - cxp) * ex + (my - cyp) * ey < 0:
                ex, ey = -ex, -ey
            eout.append((ex, ey))
        for y in range(max(0, int(min(ys))), min(self.h - 1, int(max(ys)) + 1) + 1):
            py = y + 0.5
            for x in range(max(0, int(min(xs))), min(self.w - 1, int(max(xs)) + 1) + 1):
                px = x + 0.5
                inside = False
                j = len(poly) - 1
                for i in range(len(poly)):
                    xi, yi = poly[i]
                    xj, yj = poly[j]
                    if (yi > py) != (yj > py) and px < (xj - xi) * (py - yi) / (yj - yi) + xi:
                        inside = not inside
                    j = i
                if not inside:
                    continue
                best, bi = 1e9, 0
                for k, ((ax, ay), (bx, by)) in enumerate(edges):
                    vx, vy = bx - ax, by - ay
                    l2 = vx * vx + vy * vy or 1
                    t = clamp(((px - ax) * vx + (py - ay) * vy) / l2)
                    d = math.hypot(px - (ax + vx * t), py - (ay + vy * t))
                    if d < best:
                        best, bi = d, k
                if best < bevel:
                    k = 1 - best / bevel
                    ex, ey = eout[bi]
                    nn = norm((n0[0] * (1 - k) + ex * k * 1.2, n0[1] * (1 - k) + ey * k * 1.2, n0[2] * (1 - k * 0.6)))
                    zz = z + thick / 2 - k * thick / 2
                else:
                    nn, zz = n0, z + thick / 2
                self._put(x, y, zz, nn, mat, oid, heat)
        return oid


    def plate3d(self, pts, mat, bevel=3.0, oid=None, heat=1.0):
        """A flat polygon given by 3D model-space points (planar): depth from its plane, bevelled edges.
        Seen edge-on it thins to a sliver, so a blade can turn toward and away from the camera."""
        oid = oid or self.new_id()
        P = [self.T(p) for p in pts]
        bevel *= self.S
        # plane normal (Newell), facing the camera
        nx = ny = nz = 0.0
        for i in range(len(P)):
            a, b = P[i], P[(i + 1) % len(P)]
            nx += (a[1] - b[1]) * (a[2] + b[2])
            ny += (a[2] - b[2]) * (a[0] + b[0])
            nz += (a[0] - b[0]) * (a[1] + b[1])
        n0 = norm((nx, ny, nz))
        if n0[2] < 0:
            n0 = (-n0[0], -n0[1], -n0[2])
        poly = [(p[0], p[1]) for p in P]
        x0p, y0p, z0p = P[0]
        flat = abs(n0[2]) < 0.08                     # edge-on: no usable plane depth
        zmax = max(p[2] for p in P)
        xs, ys = [p[0] for p in poly], [p[1] for p in poly]
        edges = list(zip(poly, poly[1:] + poly[:1]))
        cxp, cyp = sum(xs) / len(xs), sum(ys) / len(ys)
        eout = []
        for (ax, ay), (bx, by) in edges:
            ex, ey = by - ay, -(bx - ax)
            l = math.hypot(ex, ey) or 1
            ex, ey = ex / l, ey / l
            if ((ax + bx) / 2 - cxp) * ex + ((ay + by) / 2 - cyp) * ey < 0:
                ex, ey = -ex, -ey
            eout.append((ex, ey))
        for y in range(max(0, int(min(ys))), min(self.h - 1, int(max(ys)) + 1) + 1):
            py = y + 0.5
            for x in range(max(0, int(min(xs))), min(self.w - 1, int(max(xs)) + 1) + 1):
                px = x + 0.5
                inside = False
                j = len(poly) - 1
                for i in range(len(poly)):
                    xi, yi = poly[i]
                    xj, yj = poly[j]
                    if (yi > py) != (yj > py) and px < (xj - xi) * (py - yi) / (yj - yi) + xi:
                        inside = not inside
                    j = i
                if not inside:
                    continue
                z = zmax if flat else z0p - (n0[0] * (px - x0p) + n0[1] * (py - y0p)) / n0[2]
                best, bi = 1e9, 0
                for k, ((ax, ay), (bx, by)) in enumerate(edges):
                    vx, vy = bx - ax, by - ay
                    l2 = vx * vx + vy * vy or 1
                    t = clamp(((px - ax) * vx + (py - ay) * vy) / l2)
                    d = math.hypot(px - (ax + vx * t), py - (ay + vy * t))
                    if d < best:
                        best, bi = d, k
                nn = n0
                if best < bevel:
                    k = 1 - best / bevel
                    ex, ey = eout[bi]
                    nn = norm((n0[0] * (1 - k) + ex * k * 1.2, n0[1] * (1 - k) + ey * k * 1.2, n0[2] * (1 - k * 0.6) + 0.05))
                self._put(x, y, z + 1.5, nn, mat, oid, heat)
        return oid


# =====================================================================================================
# the redesigned Pit Lord
# =====================================================================================================
def build_lord(s, phase=0.0, breathe=0.0):
    """Idle pose. A towering goat-legged demon lord turned slightly toward the viewer: barrel chest with a molten
    core, iron pauldrons with spikes, ram-and-bull horns, a crown of fire, a war belt with a skull buckle and a
    molten-edged cleaver held low in the near hand. Near-side limbs at +z, far-side at -z."""
    b = breathe
    # --- far side first doesn't matter for a z-buffer, but ids per part help the outline pass
    # tail
    s.tube(bez3((136, 168, -14), (96, 196, -10), (86, 150, -6), 14), 7, 3, SKIN_DARK)
    s.ellipsoid((84, 146, -6), 7, 5, 4, -0.6, SKIN_DARK)

    # legs (goat): thigh forward to the knee, shin back to the hock, pastern forward to the hoof
    def leg(side):
        z = 20 * side
        hip = (172 + (6 if side > 0 else -8), 172, z)
        knee = (hip[0] + 28, 204, z + 2 * side)
        hock = (hip[0] - 2, 238, z)
        hoof = (hip[0] + 12, 262, z + 2 * side)
        s.tube([hip, lerp3(hip, knee, 0.5), knee], 21, 14, FUR)                  # shaggy thigh
        s.ellipsoid(lerp3(hip, knee, 0.45), 20, 15, 18, 0.7, FUR)               # haunch
        s.tube([knee, hock], 13, 8.5, FUR)
        s.tube([hock, (hoof[0] - 2, hoof[1] - 6, hoof[2])], 8.5, 7, FUR)
        s.ellipsoid((hoof[0] + 2, hoof[1], hoof[2]), 13, 7, 11, 0, HOOF)
        s.ellipsoid((hock[0] - 5, hock[1] - 4, z - 4 * side), 6, 9, 6, 0.3, FUR)   # fetlock tuft
    leg(-1)
    leg(1)

    # far arm (hanging, slightly back)
    sh_f = (168, 92 + b, -40)
    el_f = (162, 140 + b, -44)
    wr_f = (176, 180 + b, -40)
    s.tube([sh_f, el_f], 17, 13, SKIN)
    s.tube([el_f, wr_f], 13, 11, SKIN)
    s.tube([lerp3(el_f, wr_f, 0.35), lerp3(el_f, wr_f, 0.8)], 13.5, 12.5, IRON)   # bracer
    s.sphere((wr_f[0] + 2, wr_f[1] + 8, wr_f[2]), 12, SKIN)

    # hips, belly, chest (turned a little toward the viewer)
    s.ellipsoid((170, 160 + b * 0.5, 0), 36, 24, 34, 0, FUR)
    s.ellipsoid((180, 138 + b, 6), 32, 26, 32, -0.15, SKIN)
    s.ellipsoid((192, 104 + b, 4), 46, 40, 40, -0.5, SKIN)
    s.ellipsoid((212, 98 + b, 26), 24, 21, 20, -0.5, SKIN)                        # near pec
    s.ellipsoid((176, 90 + b, -22), 22, 20, 18, -0.4, SKIN)                       # far pec / lat
    s.ellipsoid((200, 132 + b, 30), 16, 20, 10, -0.1, SKIN)                       # abs block
    # molten core (emissive), set into the chest, with cracks running out of it
    core = (210, 110 + b, 44)
    s.sphere(core, 9, LAVA, heat=1.0)
    for k in range(7):
        a = k * 0.9 + 0.4
        ln = 14 + (k % 3) * 6
        p0 = (core[0] + math.cos(a) * 7, core[1] + math.sin(a) * 7, 47)
        p1 = (core[0] + math.cos(a + 0.15) * ln, core[1] + math.sin(a + 0.15) * ln, 44 - ln * 0.25)
        s.tube([p0, p1], 1.8, 0.9, LAVA, heat=0.55)

    # war belt: a ring of iron plates around the hips with a skull buckle in front
    ring = []
    for i in range(25):
        t = i / 24 * 2 * math.pi
        ring.append((172 + 38 * math.cos(t), 152 + b * 0.5 + 4 * math.sin(t) * 0.0 + 2 * math.cos(t), 34 * math.sin(t)))
    s.tube(ring, 5.5, 5.5, IRON)
    for k in range(-2, 3):                                                         # hanging plates (front)
        x = 182 + k * 13
        zf = 34 * math.sqrt(max(0.0, 1 - ((x - 172) / 38) ** 2))
        s.plate([(x - 6, 156 + b * 0.5), (x + 6, 156 + b * 0.5), (x + 5, 178 + b * 0.5 - abs(k) * 3),
                 (x - 5, 178 + b * 0.5 - abs(k) * 3)], zf + 2, IRON, nrm=(0, 0.25, 1), bevel=2.5, thick=3)
    s.sphere((196, 154 + b * 0.5, 38), 9, BONE)                                    # skull buckle
    s.sphere((193, 152 + b * 0.5, 45), 2.6, FUR)
    s.sphere((200, 152 + b * 0.5, 45), 2.6, FUR)

    # neck / traps
    s.ellipsoid((210, 72 + b, 0), 30, 20, 30, -0.55, SKIN)

    # head: skull, brow, snout, jaw with fangs
    hx, hy, hz = 242, 62 + b, 10
    s.ellipsoid((hx, hy, hz), 26, 27, 24, 0, SKIN)
    s.ellipsoid((hx + 20, hy + 14, hz + 2), 17, 13, 16, -0.2, SKIN)               # snout
    s.ellipsoid((hx + 12, hy + 24, hz + 2), 15, 7, 13, 0.1, SKIN_DARK)            # jaw
    s.ellipsoid((hx + 8, hy - 11, hz + 10), 18, 6, 16, -0.15, SKIN_DARK)          # heavy brow
    s.ellipsoid((hx + 20, hy + 21, hz + 12), 13, 3, 6, 0.05, LAVA, heat=0.45)     # glowing maw between snout and jaw
    for dx in (12, 24):                                                             # fangs
        s.tube([(hx + dx, hy + 17, hz + 16), (hx + dx + 1, hy + 25, hz + 16)], 2.4, 0.7, BONE)
    s.sphere((hx + 34, hy + 10, hz + 12), 2.5, SKIN_DARK)                          # nostril

    def on_head(dx, dy):                                                            # depth of the skull's surface
        q = 1 - (dx / 26) ** 2 - (dy / 27) ** 2
        return hz + 24 * math.sqrt(max(0.0, q))
    for dx, dy in ((14, -4), (2, -5)):                                              # eyes, deep-set under the brow
        s.ellipsoid((hx + dx, hy + dy, on_head(dx, dy) - 0.5), 5, 2.6, 3, -0.2, EYE, heat=1.0)

    # horns: one sweeping up and forward (near), one up and back (far); ridged by alternating radius
    def horn(base, ctrl, end, r0):
        pts = bez3(base, ctrl, end, 18)
        oid = s.new_id()
        for i in range(len(pts) - 1):
            f = i / (len(pts) - 1)
            r = r0 * (1 - f) + 1.2 * f
            ridge = 1.0 + (0.12 if i % 2 == 0 else 0.0)
            s.tube([pts[i], pts[i + 1]], r * ridge, r * ridge, HORN, oid)
    horn((hx - 6, hy - 16, hz - 14), (hx - 40, hy - 22, hz - 20), (hx - 34, hy - 58, hz - 22), 9.5)
    horn((hx + 4, hy - 18, hz + 16), (hx + 12, hy - 56, hz + 20), (hx + 42, hy - 60, hz + 18), 10)

    # crown of fire (emissive teardrops) between the horns
    for k in range(5):
        fx = hx - 12 + k * 6
        h = 14 + 8 * math.sin(phase * 6.28 + k * 1.3) ** 2 + (2 - abs(k - 2)) * 6
        sway = math.sin(phase * 6.28 + k) * 3
        pts = bez3((fx, hy - 22, hz + 2), (fx - 1, hy - 22 - h * 0.6, hz + 2), (fx - 4 + sway, hy - 22 - h, hz + 2), 6)
        s.tube(pts, 5 * min(1.3, max(0.4, P['crown'])), 0.8, FIRE, heat=0.75)

    # pauldrons with spikes
    def pauldron(c, side):
        s.ellipsoid(c, 24, 16, 22, -0.35, IRON)
        s.ellipsoid((c[0] - 2, c[1] + 8, c[2] + side * 2), 23, 6, 21, -0.3, STEEL)  # rim
        for dx, ln in ((-12, 16), (0, 22), (11, 14)):
            base = (c[0] + dx, c[1] - 10, c[2] + 4 * side)
            s.tube([base, (base[0] - 4 - ln * 0.2, base[1] - ln, base[2])], 5.5, 0.8, STEEL)
    pauldron((170, 80 + b, -34), -1)

    # near arm: hangs forward, the cleaver held low
    sh_n = (216, 92 + b, 40)
    el_n = (234, 138 + b, 46)
    wr_n = (246, 178 + b, 46)
    s.tube([sh_n, el_n], 18, 14, SKIN)
    s.ellipsoid(lerp3(sh_n, el_n, 0.5), 19, 14, 17, 1.2, SKIN)                    # biceps
    s.tube([el_n, wr_n], 14, 12, SKIN)
    s.ellipsoid(lerp3(el_n, wr_n, 0.2), 15, 11, 14, 1.2, SKIN)                    # forearm
    s.tube([lerp3(el_n, wr_n, 0.35), lerp3(el_n, wr_n, 0.82)], 14.5, 13.5, IRON)
    for k in (0.45, 0.7):                                                           # rivets
        p = lerp3(el_n, wr_n, k)
        s.sphere((p[0] + 6, p[1], p[2] + 12), 2.2, STEEL)
    fist = (248, 190 + b, 48)
    pauldron((212, 82 + b, 36), 1)

    # cleaver: grip in the fist, blade sweeping down and forward, molten cutting edge
    ang = 1.05                                     # blade direction (radians, screen space)
    ca, sa = math.cos(ang), math.sin(ang)
    nx, ny = -sa, ca                               # perpendicular: the cutting edge side
    def at(d, w):
        return (fist[0] + ca * d + nx * w, fist[1] + sa * d + ny * w)
    blade = [at(14, -9), at(56, -11), at(80, -7), at(86, 4), at(76, 16), at(46, 17), at(14, 11)]
    s.plate(blade, 50, IRON, nrm=(0.25, -0.2, 1.0), bevel=4, thick=5)
    edge = [at(16, 10), at(46, 15), at(74, 14), at(83, 4)]
    for i in range(len(edge) - 1):
        a, c = edge[i], edge[i + 1]
        s.tube([(a[0], a[1], 56), (c[0], c[1], 56)], 1.8, 1.8, LAVA, heat=0.9)
    for k in range(4):                                                              # runes on the fuller
        p = at(26 + k * 13, 0)
        s.sphere((p[0], p[1], 56), 2.0, LAVA, heat=0.6 + 0.2 * (k % 2))
    g0, g1 = at(6, -14), at(6, 16)                                                  # horned guard
    s.tube([(g0[0], g0[1], 52), (g1[0], g1[1], 52)], 4, 4, HORN)
    s.tube([(at(-14, 0)[0], at(-14, 0)[1], 50), (at(6, 0)[0], at(6, 0)[1], 50)], 4, 4, WOOD)   # grip
    s.sphere((at(-17, 0)[0], at(-17, 0)[1], 50), 5, STEEL)                          # pommel
    # the fist wraps the grip (drawn last so the fingers are in front)
    s.sphere((fist[0], fist[1], 54), 13, SKIN)
    for k in range(4):
        s.sphere((fist[0] + 7, fist[1] - 8 + k * 5.5, 64), 4.2, SKIN)


# =====================================================================================================
# shading
# =====================================================================================================
KEY = norm((-0.5, -0.75, 0.55))       # toward the key light: upper left, in front
BOUNCE = norm((0.15, 1.0, 0.25))      # lava below
RIM = norm((0.85, -0.25, -0.45))      # behind, to the right
KEY_C = (1.0, 0.9, 0.82)
BOUNCE_C = (1.0, 0.42, 0.12)
RIM_C = (1.0, 0.62, 0.3)
AMB = 0.2
VIEW = (0.0, 0.0, 1.0)
HALF = norm((KEY[0] + VIEW[0], KEY[1] + VIEW[1], KEY[2] + VIEW[2]))
OUTLINE = (26, 8, 12)
BAYER = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]


def ambient_occlusion(s, x, y, i):
    """Screen-space AO: how many nearby pixels sit clearly in front of this one."""
    z = s.z[i]
    occ = 0
    for rad in (4, 9):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1), (0.7, 0.7), (-0.7, 0.7), (0.7, -0.7), (-0.7, -0.7)):
            xx, yy = int(x + dx * rad), int(y + dy * rad)
            if 0 <= xx < s.w and 0 <= yy < s.h:
                zz = s.z[yy * s.w + xx]
                if zz > z + rad * 0.9:
                    occ += 1
    return 1.0 - min(0.6, occ / 16 * 0.9)


def snap_normal(n, oid, step=0.42):
    """Low-poly facets: snap the normal's yaw/pitch to a coarse grid (offset per object so facets don't line up)."""
    off = (oid * 0.37) % step
    yaw = math.atan2(n[0], n[2])
    pitch = math.asin(clamp(n[1], -1, 1))
    yaw = round((yaw - off) / step) * step + off
    pitch = round((pitch - off * 0.5) / step) * step + off * 0.5
    cp = math.cos(pitch)
    return (math.sin(yaw) * cp, math.sin(pitch), math.cos(yaw) * cp)


def emissive(mat, heat, n):
    hot = (1.0, 0.95, 0.65)
    base = mat.base
    k = clamp(heat * (0.55 + 0.45 * max(0.0, n[2])))
    return tuple(base[c] * (1 - k) + hot[c] * k for c in range(3))


def shade_continuous(mat, n, ao):
    key = max(0.0, dot(n, KEY))
    bounce = max(0.0, dot(n, BOUNCE))
    spec = max(0.0, dot(n, HALF)) ** mat.shiny * mat.spec
    rim = (1 - max(0.0, n[2])) ** 2.2 * max(0.0, n[0] * RIM[0] + n[1] * RIM[1])
    base = mat.base
    out = []
    for c in range(3):
        v = base[c] * (AMB * ao + key * KEY_C[c] * 0.95 * (0.55 + 0.45 * ao))
        v += base[c] * bounce * BOUNCE_C[c] * 0.55 + bounce * BOUNCE_C[c] * 0.08
        v += spec * (0.9 if mat.metal else 0.7) * (base[c] * 0.5 + 0.5 if mat.metal else 1.0) * KEY_C[c]
        v += rim * RIM_C[c] * 0.55
        out.append(v)
    return out


def shade_cel(mat, n, ao):
    key = max(0.0, dot(n, KEY)) * (0.6 + 0.4 * ao)
    bounce = max(0.0, dot(n, BOUNCE))
    spec = max(0.0, dot(n, HALF)) ** mat.shiny * mat.spec
    rim = (1 - max(0.0, n[2])) ** 2 * max(0.0, n[0] * RIM[0] + n[1] * RIM[1])
    base = mat.base
    band = 0.32 if key < 0.22 else 0.62 if key < 0.6 else 0.95
    col = [base[c] * band for c in range(3)]
    if bounce > 0.55 and key < 0.6:                       # hard orange lava light from below
        col = [col[c] * 0.6 + BOUNCE_C[c] * 0.35 for c in range(3)]
    if spec > 0.35:
        col = [min(1.0, col[c] + 0.45) for c in range(3)]
    if rim > 0.4:
        col = [col[c] * 0.4 + RIM_C[c] * 0.6 for c in range(3)]
    return col


def render(s, style):
    img = Image.new('RGBA', (s.w, s.h), (0, 0, 0, 0))
    px = img.load()
    for y in range(s.h):
        for x in range(s.w):
            i = y * s.w + x
            m = s.m[i]
            if m is None:
                continue
            n = s.n[i]
            if m.emissive:
                col = emissive(m, s.heat[i], n)
            else:
                ao = ambient_occlusion(s, x, y, i)
                if style == 'faceted':
                    col = shade_continuous(m, snap_normal(n, s.oid[i]), 0.85 + 0.15 * ao)
                elif style == 'cel':
                    col = shade_cel(m, n, ao)
                else:
                    col = shade_continuous(m, n, ao)
            if style == 'prerender':
                # posterise to 9 levels per channel with ordered dithering
                t = (BAYER[y % 4][x % 4] + 0.5) / 16 - 0.5
                col = [round(clamp(v) * 8 + t) / 8 for v in col]
            elif style == 'faceted':
                col = [round(clamp(v) * 14) / 14 for v in col]
            px[x, y] = tuple(int(clamp(v) * 255) for v in col) + (255,)

    if style in ('cel', 'faceted'):
        lines = []
        for y in range(s.h):
            for x in range(s.w):
                i = y * s.w + x
                if s.m[i] is None:
                    continue
                edge = False
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    xx, yy = x + dx, y + dy
                    if not (0 <= xx < s.w and 0 <= yy < s.h) or s.m[yy * s.w + xx] is None:
                        edge = True
                        break
                    j = yy * s.w + xx
                    if style == 'cel' and s.oid[j] != s.oid[i] and s.z[j] > s.z[i] + 9:
                        edge = True       # inner line where a part sits well in front of another
                        break
                if edge and not s.m[i].emissive:
                    lines.append((x, y))
        for x, y in lines:
            px[x, y] = OUTLINE + (255,)

    # glow from emissive parts onto nearby body pixels
    glow = {}
    for y in range(s.h):
        for x in range(s.w):
            i = y * s.w + x
            m = s.m[i]
            if m is None or not m.emissive or s.heat[i] < 0.5:
                continue
            for dy in range(-6, 7, 2):
                for dx in range(-6, 7, 2):
                    d = math.hypot(dx, dy)
                    if d > 6:
                        continue
                    k = (x + dx, y + dy)
                    glow[k] = glow.get(k, 0) + (1 - d / 6) * 0.05
    for (x, y), g in glow.items():
        if 0 <= x < s.w and 0 <= y < s.h and s.m[y * s.w + x] is not None and not s.m[y * s.w + x].emissive:
            r_, g_, b_, a_ = px[x, y]
            g = min(0.45, g)
            px[x, y] = (int(r_ + (255 - r_) * g), int(g_ + (170 - g_) * g * 0.8) if g_ < 170 else g_, b_, a_)

    # contact shadow under the hooves
    shadow = Image.new('RGBA', (s.w, s.h), (0, 0, 0, 0))
    sp = shadow.load()
    for y in range(GROUND - 6, min(s.h, GROUND + 6)):
        for x in range(100, 280):
            q = ((x - 186) / 76) ** 2 + ((y - GROUND) / 6) ** 2
            if q <= 1:
                sp[x, y] = (10, 2, 4, int(110 * (1 - q)))
    shadow.alpha_composite(img)
    return shadow


MODEL_SCALE = 0.745           # the model is built big for easy numbers, then shrunk to fit the 352x280 frame
FRAME_ANCHOR = (166, GROUND)  # where the model's pivot lands: 10 px left of the frame centre, so the forward-jutting
                              # head and a blade swept forward still fit, and the torso sits over the hitbox


def concept_frame(style):
    s = Scene(scale=MODEL_SCALE, anchor=FRAME_ANCHOR)
    build_lord(s, phase=0.15, breathe=0.0)
    return render(s, style)


def concepts_sheet(path, old_frame=None):
    """The current Lord next to the three styles, at x2 on a dark arena-ish backdrop, labelled."""
    from PIL import ImageDraw
    styles = ['prerender', 'cel', 'faceted']
    frames = ([('current', old_frame)] if old_frame is not None else []) + [(st, concept_frame(st)) for st in styles]
    scale = 2
    pad, label_h = 16, 28
    fw, fh = W * scale, H * scale
    sheet = Image.new('RGBA', (pad + len(frames) * (fw + pad), label_h + fh + pad * 2), (22, 8, 12, 255))
    d = ImageDraw.Draw(sheet)
    names = {'current': 'current', 'prerender': 'A  pre-rendered', 'cel': 'B  cel-shaded', 'faceted': 'C  low-poly'}
    for k, (name, fr) in enumerate(frames):
        x = pad + k * (fw + pad)
        bg = Image.new('RGBA', (fw, fh), (48, 16, 20, 255))
        bd = ImageDraw.Draw(bg)
        bd.rectangle([0, (GROUND + 1) * scale, fw, fh], fill=(34, 20, 22, 255))      # ground
        bd.line([0, (GROUND + 1) * scale, fw, (GROUND + 1) * scale], fill=(120, 50, 30, 255), width=2)
        big = fr.resize((fw, fh), Image.NEAREST)
        bg.alpha_composite(big)
        sheet.alpha_composite(bg, (x, label_h + pad))
        d.text((x + 6, 8), names[name], fill=(255, 220, 160, 255))
    sheet.save(path)



# =====================================================================================================
# pose-driven Pit Lord (style A, chosen 2026-11-20): every animation is a set of key poses sampled at twice
# the old frame count, so the in-between frames are real in-between poses
# =====================================================================================================
U = 2.0 / MODEL_SCALE           # one design unit of the old 2D rig (demons2.draw_lord, git history) in model units
L_UP, L_FORE = 48, 42           # near arm segment lengths (the far arm is a little shorter)
BACK_ANG = 1.98                 # the sheathed cleaver's angle standing upright

DEFAULT = dict(bob=0.0, lean=0.0, hunch=0.0, twist=0.0, legs=(0.0, 0.0), lift=(0.0, 0.0),
               near=(1.15, 0.85), near_out=(0.0, 0.0), far=(1.35, 1.6), far_out=(0.0, 0.0),
               sword=1.02, blade=None, edge=None, grip2=0.0, sheathed=0.0,
               mouth=0.0, head_up=0.0, horn_tilt=0.0, crown_lean=-0.2, fire=0.0, heat=0.4, phase=0.0,
               kneel=0.0, drop=0.0, cracks=0.0, crown=1.0)
# death poses: kneel folds the near leg onto the ground; drop leaves the cleaver stuck in the ground in front;
# cracks spreads glowing lava cracks over the body (0..1); heat above 1 swells the core; crown scales the fire


def pose(**kw):
    d = dict(DEFAULT)
    for k in kw:
        assert k in d, k
    d.update(kw)
    return d


def _blade(P):
    if P['blade'] is not None:
        return norm(P['blade'])
    return (math.cos(P['sword']), math.sin(P['sword']), 0.0)


def _edge(P, D):
    """The cutting edge's side: given, or the old rig's rule (perpendicular in the picture, facing down/forward)."""
    E = P['edge']
    if E is None:
        E = (-D[1], D[0], 0.0) if D[0] >= 0 else (D[1], -D[0], 0.0)
        if abs(D[0]) < 1e-3 and abs(D[1]) < 1e-3:
            E = (1.0, 0.0, 0.0)
    E = norm(E)
    k = dot(E, D)                                   # make it perpendicular to the blade
    return norm((E[0] - D[0] * k, E[1] - D[1] * k, E[2] - D[2] * k))


def lerp_pose(a, b, t):
    out = {}
    for k in a:
        if k in ('blade', 'edge', 'sword'):
            continue
        va, vb = a[k], b[k]
        if isinstance(va, tuple):
            out[k] = tuple(va[i] + (vb[i] - va[i]) * t for i in range(len(va)))
        else:
            out[k] = va + (vb - va) * t
    if a['blade'] is None and b['blade'] is None and a['edge'] is None and b['edge'] is None:
        # both in the picture plane: turn the cleaver the short way round (over the top for a slam, back over
        # the shoulder for the sheath), never through a zero-length vector
        d = (b['sword'] - a['sword'] + math.pi) % (2 * math.pi) - math.pi
        out['sword'], out['blade'], out['edge'] = a['sword'] + d * t, None, None
        return out
    Da, Db = _blade(a), _blade(b)
    D = norm(lerp3(Da, Db, t))
    Ea, Eb = _edge(a, Da), _edge(b, Db)
    out['blade'], out['edge'], out['sword'] = D, lerp3(Ea, Eb, t), 0.0
    return out


def add(p, q):
    return (p[0] + q[0], p[1] + q[1], p[2] + q[2])


def mul(p, k):
    return (p[0] * k, p[1] * k, p[2] * k)


def seg(a, el, ln):
    """A limb segment: xy angle a, out-of-plane elevation el (toward the camera), length ln."""
    c = math.cos(el)
    return (math.cos(a) * c * ln, math.sin(a) * c * ln, math.sin(el) * ln)


def cleaver3d(s, G, D, E, heat):
    """Molten-edged cleaver held at grip point G: blade along D, cutting edge toward E."""
    n = (D[1] * E[2] - D[2] * E[1], D[2] * E[0] - D[0] * E[2], D[0] * E[1] - D[1] * E[0])
    if n[2] < 0:
        n = mul(n, -1)
    at = lambda d, w, lift=0.0: add(add(add(G, mul(D, d)), mul(E, w)), mul(n, lift))
    blade = [(14, -9), (56, -11), (80, -7), (86, 4), (76, 16), (46, 17), (14, 11)]
    s.plate3d([at(d, w) for d, w in blade], IRON, bevel=4)
    edge = [at(16, 10, 2.5), at(46, 15, 2.5), at(74, 14, 2.5), at(83, 4, 2.5)]
    for i in range(len(edge) - 1):
        s.tube([edge[i], edge[i + 1]], 1.8, 1.8, LAVA, heat=0.6 + 0.3 * heat)
    for k in range(4):
        s.sphere(at(26 + k * 13, 0, 3), 2.0, LAVA, heat=0.5 + 0.3 * heat * (k % 2))
    s.tube([at(6, -14, 1), at(6, 16, 1)], 4, 4, HORN)                              # horned guard
    s.tube([at(-14, 0), at(6, 0)], 4, 4, WOOD)                                      # grip
    s.sphere(at(-17, 0), 5, STEEL)                                                  # pommel


STUCK_AT = (292, 204)            # the dropped cleaver's grip (model units): fixed, so the prop matches the last frame


def stuck_cleaver(s, hip=None):
    """The cleaver, dropped and stuck point-first in the ground in front of him (also left behind as a prop)."""
    G = (STUCK_AT[0], STUCK_AT[1], 30)
    D = norm((0.22, 1.0, -0.05))
    cleaver3d(s, G, D, _edge(dict(edge=None), D), 0.3)


def build_pose(s, P):
    bob, lean, hunch, tw, ph = P['bob'] * U, P['lean'], P['hunch'], P['twist'], P['phase']
    hip = (172 + lean * 2 * U, 172 + bob, 0)

    # tail, swishing with the phase
    wag = math.sin(ph * 6.28) * 6
    tb = (hip[0] - 36, hip[1] - 4, -14)
    s.tube(bez3(tb, (tb[0] - 40, tb[1] + 26 + wag, -10), (tb[0] - 50, tb[1] - 20 + wag * 0.5, -6), 14), 7, 3, SKIN_DARK)
    s.ellipsoid((tb[0] - 52, tb[1] - 24 + wag * 0.5, -6), 7, 5, 4, -0.6, SKIN_DARK)

    # goat legs: thigh forward to the knee, shin back to the hock, pastern forward to the hoof
    for i, side in ((1, -1), (0, 1)):
        a, L = P['legs'][i], P['lift'][i] * U
        z = 20 * side
        h = (hip[0] + (6 if side > 0 else -8), hip[1], z)
        hoof = (h[0] + 12 + math.sin(a) * 26, 262 - L, z + 2 * side)
        knee = (h[0] + 26 + math.sin(a) * 12, (h[1] + hoof[1]) / 2 - 14 - L * 0.2, z + 2 * side)
        hock = (hoof[0] - 12 - math.sin(a) * 4, hoof[1] - 22, z)
        kn = P['kneel'] if side > 0 else P['kneel'] * 0.35
        if kn > 0:                                     # knee down on the ground, shin lying back along it
            knee = lerp3(knee, (h[0] + 30, 250, z + 2 * side), kn)
            hock = lerp3(hock, (h[0] - 18, 256, z), kn)
            hoof = lerp3(hoof, (h[0] - 34, 260, z + 2 * side), kn)
        s.tube([h, lerp3(h, knee, 0.5), knee], 21, 14, FUR)
        s.ellipsoid(lerp3(h, knee, 0.45), 20, 15, 18, 0.7, FUR)
        s.tube([knee, hock], 13, 8.5, FUR)
        s.tube([hock, (hoof[0] - 2, hoof[1] - 6, hoof[2])], 8.5, 7, FUR)
        s.ellipsoid((hoof[0] + 2, hoof[1], hoof[2]), 13, 7, 11, 0, HOOF)
        s.ellipsoid((hock[0] - 5, hock[1] - 4, z - 4 * side), 6, 9, 6, 0.3, FUR)

    # torso, pitched forward by the hunch
    chest = (192 + lean * 5 * U + hunch * 10, 104 + bob + hunch * 16, 4)
    belly = (180 + lean * 3 * U + hunch * 4, 138 + bob + hunch * 6, 6)
    sh_f = (168 + lean * 5 * U + hunch * 12 - tw * 8, 92 + bob + hunch * 18, -40 - tw * 4)
    sh_n = (216 + lean * 6 * U + hunch * 14 + tw * 10, 92 + bob + hunch * 20, 40 + tw * 6)
    s.ellipsoid((hip[0] - 2, hip[1] - 12, 0), 36, 24, 34, 0, FUR)
    torso = [(belly, 32, 26, 32, -0.15 - hunch * 0.2), (chest, 46, 40, 40, -0.5 - hunch * 0.45),
             (add(chest, (20, -6, 22)), 24, 21, 20, -0.5 - hunch * 0.4),              # near pec
             (add(chest, (-16, -14, -26)), 22, 20, 18, -0.4),                         # far pec / lat
             (add(belly, (20, -6, 24)), 16, 20, 10, -0.1)]                            # abs
    for c, rx, ry, rz, ang in torso:
        s.ellipsoid(c, rx, ry, rz, ang, SKIN)

    def on_torso(x, y):
        """Depth of the torso's outer surface at model point (x, y): decals (cracks) sit just on top of it."""
        best = None
        for (cx, cy, cz), rx, ry, rz, ang in torso:
            dx, dy = x - cx, y - cy
            u = dx * math.cos(ang) + dy * math.sin(ang)
            v = -dx * math.sin(ang) + dy * math.cos(ang)
            q = 1 - (u / rx) ** 2 - (v / ry) ** 2
            if q > 0:
                z = cz + rz * math.sqrt(q)
                best = z if best is None else max(best, z)
        return best
    core = add(chest, (18, 6, 40))
    s.sphere(core, 9 * (1 + max(0.0, P['heat'] - 1) * 0.7), LAVA, heat=min(1.0, 0.6 + 0.4 * P['heat']))
    for k in range(7):
        a = k * 0.9 + 0.4
        ln = 14 + (k % 3) * 6
        p0 = (core[0] + math.cos(a) * 7, core[1] + math.sin(a) * 7, core[2] + 3)
        p1 = (core[0] + math.cos(a + 0.15) * ln, core[1] + math.sin(a + 0.15) * ln, core[2] - ln * 0.25)
        s.tube([p0, p1], 1.8, 0.9, LAVA, heat=min(1.0, 0.3 + 0.4 * P['heat']))
    # dying: the cracks spread from the core over the chest, belly and shoulders, one after another
    if P['cracks'] > 0:
        spread = [((18, 6), (52, -30), 40), ((18, 6), (-30, -26), 40), ((18, 6), (6, 48), 44),
                  ((18, 6), (44, 30), 30), ((18, 6), (-34, 22), 36), ((-20, -18), (-44, -40), 28),
                  ((30, -20), (50, -46), 26), ((0, 30), (-20, 60), 28), ((40, 20), (66, 34), 22)]
        for k, ((ax, ay), (bx, by), _) in enumerate(spread):
            f = clamp(P['cracks'] * len(spread) - k)
            if f <= 0:
                continue
            pts = []
            for j in range(9):                         # a jagged line following the skin
                t = j / 8 * f
                x = chest[0] + ax + (bx - ax) * t + (3 if j % 2 else -3) * (k % 2 * 2 - 1)
                y = chest[1] + ay + (by - ay) * t
                z = on_torso(x, y)
                if z is None:
                    break
                pts.append((x, y, z + 0.5))
            if len(pts) >= 2:
                s.tube(pts, 2.2, 1.0, LAVA, heat=0.55 + 0.35 * f)
    # war belt with hanging plates and a skull buckle
    bc = (hip[0], hip[1] - 20, 0)
    ring = [(bc[0] + 38 * math.cos(t), bc[1] + 2 * math.cos(t), 34 * math.sin(t))
            for t in (i / 24 * 2 * math.pi for i in range(25))]
    s.tube(ring, 5.5, 5.5, IRON)
    for k in range(-2, 3):
        x = bc[0] + 10 + k * 13
        zf = 34 * math.sqrt(max(0.0, 1 - ((x - bc[0]) / 38) ** 2))
        y0 = bc[1] + 4
        s.plate([(x - 6, y0), (x + 6, y0), (x + 5, y0 + 22 - abs(k) * 3), (x - 5, y0 + 22 - abs(k) * 3)],
                zf + 2, IRON, nrm=(0, 0.25, 1), bevel=2.5, thick=3)
    s.sphere((bc[0] + 24, bc[1] + 2, 38), 9, BONE)
    s.sphere((bc[0] + 21, bc[1], 45), 2.6, FUR)
    s.sphere((bc[0] + 28, bc[1], 45), 2.6, FUR)

    # neck and head
    s.ellipsoid((210 + lean * 7 * U + hunch * 18, 72 + bob + hunch * 30, 0), 30, 20, 30, -0.55 - hunch * 0.3, SKIN)
    hx = 242 + lean * 8 * U + hunch * 22
    hy = 62 + bob - P['head_up'] * 7.5 + hunch * 42
    hz = 10
    mouth = P['mouth']
    s.ellipsoid((hx, hy, hz), 26, 27, 24, 0, SKIN)
    s.ellipsoid((hx + 20, hy + 14, hz + 2), 17, 13, 16, -0.2, SKIN)                # snout
    s.ellipsoid((hx + 12, hy + 24 + mouth * 8, hz + 2), 15, 7, 13, 0.1 + mouth * 0.15, SKIN_DARK)   # jaw
    s.ellipsoid((hx + 20, hy + 21 + mouth * 4, hz + 12), 13, 3 + mouth * 5, 6, 0.05, LAVA, heat=0.35 + mouth * 0.55)
    for dx in (12, 24):
        s.tube([(hx + dx, hy + 17, hz + 16), (hx + dx + 1, hy + 25, hz + 16)], 2.4, 0.7, BONE)
    for dx in (14, 26):                                                             # lower fangs when roaring
        if mouth > 0.4:
            s.tube([(hx + dx, hy + 28 + mouth * 8, hz + 15), (hx + dx, hy + 22 + mouth * 6, hz + 15)], 2.0, 0.6, BONE)
    s.ellipsoid((hx + 8, hy - 11, hz + 10), 18, 6, 16, -0.15, SKIN_DARK)            # brow
    s.sphere((hx + 34, hy + 10, hz + 12), 2.5, SKIN_DARK)

    def on_head(dx, dy):
        q = 1 - (dx / 26) ** 2 - (dy / 27) ** 2
        return hz + 24 * math.sqrt(max(0.0, q))
    for dx, dy in ((14, -4), (2, -5)):
        s.ellipsoid((hx + dx, hy + dy, on_head(dx, dy) - 0.5), 5, 2.6, 3, -0.2, EYE, heat=1.0)

    def horn(base, ctrl, end, r0):
        tilt = P['horn_tilt']
        if tilt:
            ca, sa = math.cos(tilt), math.sin(tilt)
            rot = lambda q: (base[0] + (q[0] - base[0]) * ca - (q[1] - base[1]) * sa,
                             base[1] + (q[0] - base[0]) * sa + (q[1] - base[1]) * ca, q[2])
            ctrl, end = rot(ctrl), rot(end)
        pts = bez3(base, ctrl, end, 18)
        oid = s.new_id()
        for i in range(len(pts) - 1):
            f = i / (len(pts) - 1)
            r = (r0 * (1 - f) + 1.2 * f) * (1.12 if i % 2 == 0 else 1.0)
            s.tube([pts[i], pts[i + 1]], r, r, HORN, oid)
    horn((hx - 6, hy - 16, hz - 14), (hx - 40, hy - 22, hz - 20), (hx - 34, hy - 58, hz - 22), 9.5)
    horn((hx + 4, hy - 18, hz + 16), (hx + 12, hy - 56, hz + 20), (hx + 42, hy - 60, hz + 18), 10)

    for k in range(5):                                                              # crown of fire
        fx = hx - 12 + k * 6
        h = (14 + 8 * math.sin(ph * 6.28 + k * 1.3) ** 2 + (2 - abs(k - 2)) * 6) * P['crown']
        sway = math.sin(ph * 6.28 + k) * 3 + P['crown_lean'] * 14
        pts = bez3((fx, hy - 22, hz + 2), (fx - 1 + sway * 0.4, hy - 22 - h * 0.6, hz + 2), (fx - 4 + sway, hy - 22 - h, hz + 2), 6)
        s.tube(pts, 5, 0.8, FIRE, heat=0.75)

    def pauldron(c, side):
        s.ellipsoid(c, 24, 16, 22, -0.35 - hunch * 0.3, IRON)
        s.ellipsoid((c[0] - 2, c[1] + 8, c[2] + side * 2), 23, 6, 21, -0.3 - hunch * 0.3, STEEL)
        for dx, ln in ((-12, 16), (0, 22), (11, 14)):
            base = (c[0] + dx, c[1] - 10, c[2] + 4 * side)
            s.tube([base, (base[0] - 4 - ln * 0.2, base[1] - ln, base[2])], 5.5, 0.8, STEEL)

    def arm(sh, ang, out, l1, l2, r):
        el = add(sh, seg(ang[0], out[0], l1))
        wr = add(el, seg(ang[1], out[1], l2))
        s.tube([sh, el], r, r * 0.78, SKIN)
        s.ellipsoid(lerp3(sh, el, 0.5), r * 1.05, r * 0.78, r * 0.95, ang[0], SKIN)
        s.tube([el, wr], r * 0.78, r * 0.66, SKIN)
        s.tube([lerp3(el, wr, 0.35), lerp3(el, wr, 0.82)], r * 0.8, r * 0.75, IRON)
        fist = add(wr, seg(ang[1], out[1], 8))
        return el, wr, fist

    # far side: arm (with a fireball when casting), pauldron, the sheathed cleaver on the back
    el_f, wr_f, fist_f = arm(sh_f, P['far'], P['far_out'], 44, 40, 17)
    s.sphere(fist_f, 12, SKIN)
    if P['fire'] > 0.2:
        r = P['fire'] * U * 0.85
        fb = add(fist_f, (math.cos(P['far'][1] - 0.6) * (4 + r), math.sin(P['far'][1] - 0.6) * (4 + r), 8))
        s.sphere(fb, r, LAVA, heat=1.0)
        s.tube(bez3(add(fb, (0, -r * 0.5, 0)), add(fb, (-2, -r * 1.6, 0)), add(fb, (-5, -r * 2.6, 0)), 6), r * 0.7, 0.8, FIRE, heat=0.8)
    pauldron(add(sh_f, (2, -12, 6)), -1)
    if P['sheathed'] > 0.5:
        ang = BACK_ANG + hunch * 0.55 - lean * 0.15
        D = norm((math.cos(ang), math.sin(ang), -0.1))
        G = (sh_f[0] - 8, sh_f[1] - 44 + hunch * 8, -30)
        cleaver3d(s, G, D, _edge(dict(edge=None), D), P['heat'])
        sb = add(G, mul(D, 34))
        s.tube([add(sb, (-8, -8, 34)), add(sb, (12, 10, 34))], 3, 3, FUR)          # strap across the chest

    # near side: pauldron, arm, cleaver, fist (and the second hand on the hilt for two-handed blows)
    pauldron(add(sh_n, (-4, -10, -4)), 1)
    el_n, wr_n, fist = arm(sh_n, P['near'], P['near_out'], L_UP, L_FORE, 18)
    for k in (0.45, 0.7):
        q = lerp3(el_n, wr_n, k)
        s.sphere((q[0] + 6, q[1], q[2] + 12), 2.2, STEEL)
    if P['drop'] > 0.5:
        stuck_cleaver(s, hip)
    elif P['sheathed'] <= 0.5:
        D = _blade(P)
        E = _edge(P, D)
        cleaver3d(s, add(fist, mul(D, 4)), D, E, P['heat'])
        if P['grip2'] > 0.5:
            s.sphere(add(add(fist, mul(D, -12)), (0, 0, -4)), 12, SKIN)
    s.sphere(fist, 13, SKIN)
    fd = seg(P['near'][1], P['near_out'][1], 1)
    for k in range(4):                                                              # knuckles
        s.sphere(add(fist, (fd[0] * 7 - fd[1] * (-8 + k * 5.5), fd[1] * 7 + fd[0] * (-8 + k * 5.5), 10)), 4.2, SKIN)


def lord_frame(P, shadow=False):
    s = Scene(scale=MODEL_SCALE, anchor=FRAME_ANCHOR)
    build_pose(s, P)
    img = render(s, 'prerender')
    if not shadow:
        # render() puts a contact shadow under the hooves; in the game the sprite leaves the ground (leap), so drop it
        px = img.load()
        for y in range(GROUND - 6, min(H, GROUND + 6)):
            for x in range(W):
                c = px[x, y]
                if c[3] < 255 and c[:3] == (10, 2, 4):
                    px[x, y] = (0, 0, 0, 0)
    return img


# ---- animations: the old 2D rig's poses (demons2.lord_anims, now in git history) as keys, at twice the frames --
def keys_strip(keys, frames):
    """A one-shot: `frames` samples evenly from the first key to the last."""
    n = len(keys)
    out = []
    for i in range(frames):
        kp = i * (n - 1) / (frames - 1)
        k = min(n - 2, int(kp))
        out.append(lerp_pose(keys[k], keys[k + 1], kp - k))
    return out


def loop_strip(fn, frames):
    return [fn(i / frames) for i in range(frames)]


def lord_poses():
    tau = 2 * math.pi

    def idle(t):
        sn = math.sin(t * tau)
        return pose(bob=sn * 0.8, near=(1.15, 0.85), far=(1.35 + sn * 0.04, 1.6), sword=1.02, phase=t,
                    heat=0.4 + 0.2 * (sn + 1) / 2)

    def walk(t):
        p = t * tau
        a = math.sin(p) * 0.45
        return pose(bob=-abs(math.cos(p)) * 1.8 + 0.9, lean=0.05, legs=(a, -a),
                    lift=(max(0, -math.cos(p)) * 3.5, max(0, math.cos(p)) * 3.5),
                    near=(1.15 - a * 0.2, 0.85), far=(1.35 + a * 0.4, 1.6 + a * 0.3), sword=1.0 + a * 0.08, phase=t)

    slam = [
        pose(lean=-0.15, near=(-0.8, -1.4), far=(-0.6, -1.2), sword=-2.2, mouth=0.4, heat=0.7, phase=0.1, grip2=1),
        pose(lean=-0.3, bob=-1, near=(-1.55, -2.1), far=(-1.45, -2.0), sword=-2.55, mouth=1.0, head_up=0.5, heat=1.0, phase=0.3, grip2=1),
        pose(lean=0.25, bob=1.5, near=(-0.4, 0.15), far=(-0.5, 0.05), sword=0.15, mouth=1.0, heat=1.0, phase=0.5, grip2=1),
        pose(lean=0.45, bob=6, near=(0.8, 0.6), far=(0.7, 0.5), sword=0.92, legs=(0.3, -0.35), mouth=0.8, heat=1.0, phase=0.6, grip2=1),
        pose(lean=0.42, bob=5.5, near=(0.82, 0.62), far=(0.72, 0.52), sword=0.94, legs=(0.3, -0.35), mouth=0.5, heat=0.9, phase=0.8, grip2=1),
        pose(lean=0.15, bob=2, near=(1.0, 0.75), far=(1.1, 1.2), sword=1.0, legs=(0.15, -0.15), mouth=0.2, heat=0.6, phase=0.9),
    ]
    # the far hand goes up behind the horns (where it isn't hidden by the head), the fireball grows, then it's thrown
    cast = [
        pose(far=(-0.7, -1.1), far_out=(0.25, 0.3), fire=2.0, heat=0.6, phase=0.1),
        pose(lean=-0.08, far=(-1.35, -1.75), far_out=(0.25, 0.3), fire=4.0, mouth=0.4, heat=0.8, phase=0.3),
        pose(lean=-0.15, head_up=0.3, far=(-1.7, -2.05), far_out=(0.25, 0.3), fire=5.5, mouth=0.7, heat=1.0, phase=0.5),
        pose(lean=0.3, far=(-0.15, -0.25), far_out=(0.35, 0.3), fire=0, mouth=0.6, heat=0.8, phase=0.7),
    ]

    def roar(t):
        return pose(lean=-0.2, head_up=1.0, mouth=0.85 + 0.15 * math.sin(t * tau), near=(0.5, 0.3), far=(-0.2, -0.5),
                    sword=0.5, heat=1.0, phase=t, bob=math.sin(t * tau * 2) * 0.4)

    sheathe = [
        pose(lean=-0.1, near=(-0.6, -1.3), far=(1.3, 1.5), sword=-2.1, heat=0.6, phase=0.1),
        pose(lean=-0.15, head_up=0.3, near=(-1.9, -2.6), far=(1.2, 1.4), sword=BACK_ANG, heat=0.5, phase=0.3),
        pose(lean=0.1, near=(0.2, -0.5), far=(1.1, 1.0), sheathed=1, mouth=0.3, heat=0.6, phase=0.5),
        pose(lean=0.3, hunch=0.55, bob=3, legs=(0.25, -0.3), near=(1.0, 0.55), far=(1.5, 1.2), sheathed=1,
             mouth=0.6, horn_tilt=0.45, crown_lean=-0.5, heat=0.8, phase=0.7),
    ]

    def charge(t):
        p = t * tau
        a = math.sin(p) * 1.1
        return pose(lean=0.45, hunch=1.0, bob=-abs(math.cos(p)) * 3 + 2.5, legs=(a, -a),
                    lift=(max(0, -math.cos(p)) * 9, max(0, math.cos(p)) * 9),
                    near=(1.0 + math.sin(p) * 0.9, 0.5 + math.sin(p) * 0.9), far=(1.0 - math.sin(p) * 0.9, 0.5 - math.sin(p) * 0.9),
                    sheathed=1, horn_tilt=0.95, mouth=0.7, crown_lean=-1.0, heat=1.0, phase=t)

    crash = [
        pose(lean=0.6, hunch=1.1, bob=4, legs=(0.6, -0.5), near=(0.6, 0.0), far=(0.4, -0.2), sheathed=1,
             horn_tilt=1.0, mouth=1.0, crown_lean=0.6, heat=1.0, phase=0.2),
        pose(lean=-0.35, hunch=0.3, bob=2, legs=(-0.3, 0.35), near=(1.9, 2.3), far=(2.1, 2.5), sheathed=1,
             horn_tilt=0.3, head_up=0.6, mouth=0.8, heat=0.7, phase=0.5),
    ]

    def dazed(t):
        sn, cs = math.sin(t * tau), math.cos(t * tau)
        return pose(lean=sn * 0.15, hunch=0.35 + cs * 0.1, bob=1.5 + sn, near=(1.45, 1.7), far=(1.6, 1.85), sheathed=1,
                    horn_tilt=sn * 0.18, mouth=0.35, heat=0.25, phase=t)

    draw = [
        pose(lean=-0.05, near=(-1.7, -2.4), far=(1.3, 1.5), sheathed=1, heat=0.4, phase=0.1),
        pose(lean=-0.15, head_up=0.3, near=(-1.9, -2.6), far=(1.2, 1.4), sword=BACK_ANG, heat=0.6, phase=0.3),
        pose(lean=-0.25, bob=-1, near=(-1.4, -2.0), far=(-0.2, -0.6), sword=-2.5, mouth=0.8, head_up=0.4, heat=0.9, phase=0.5),
        pose(lean=0.2, near=(0.6, 0.2), far=(0.9, 1.2), sword=0.35, mouth=1.0, head_up=0.6, heat=1.0, phase=0.7),
    ]
    leap = [
        pose(lean=0.1, hunch=0.35, bob=7, legs=(0.35, -0.4), near=(-0.9, -1.5), far=(-0.7, -1.3), sword=-2.3,
             mouth=0.5, heat=0.8, phase=0.1, grip2=1),
        pose(lean=-0.25, bob=-3, lift=(10, 7), legs=(-0.35, 0.45), near=(-1.55, -2.1), far=(-1.45, -2.0),
             sword=-2.55, mouth=1.0, head_up=0.5, heat=1.0, phase=0.4, grip2=1),
        pose(lean=0.0, bob=-1, lift=(3, 2), legs=(0.25, -0.15), near=(-1.3, -1.85), far=(-1.2, -1.75),
             sword=-2.35, mouth=0.9, heat=1.0, phase=0.7, grip2=1),
    ]
    stab = [
        pose(lean=-0.1, bob=1, near=(-0.95, -1.25), far=(-0.85, -1.15), sword=-1.45, mouth=0.5, head_up=0.3, heat=0.7, phase=0.1, grip2=1),
        pose(lean=0.4, hunch=0.35, bob=5, legs=(0.3, -0.35), near=(0.95, 1.3), far=(0.85, 1.25), sword=1.5,
             mouth=1.0, heat=1.0, phase=0.4, grip2=1),
        pose(lean=0.4, hunch=0.35, bob=5.5, legs=(0.3, -0.35), near=(0.95, 1.3), far=(0.85, 1.25), sword=1.5,
             mouth=0.6, heat=0.75, phase=0.8, grip2=1),
    ]
    # the sideways swing (new): cleaver drawn back behind him, swept around past the camera at waist height,
    # through the front and on behind his far side
    ready = pose(legs=(0.3, -0.3), near=(1.0, 0.6), far=(1.3, 1.5), sword=0.9, heat=0.6, phase=0.0)
    swing = [
        ready,
        pose(legs=(0.35, -0.35), lean=-0.2, twist=-1.0, bob=1, near=(2.7, 2.95), near_out=(-0.35, -0.3),
             far=(0.6, 0.2), far_out=(0.3, 0.2), blade=(-0.95, -0.1, -0.3), edge=(0.0, 1.0, 0.55),
             mouth=0.5, heat=0.8, phase=0.2),
        pose(legs=(0.4, -0.35), lean=0.05, twist=-0.2, bob=2, near=(1.2, 0.5), near_out=(0.55, 0.95),
             far=(1.5, 1.8), blade=(0.25, 0.08, 1.0), edge=(0.8, 0.8, 0.0), mouth=1.0, heat=1.0, phase=0.35),
        pose(legs=(0.45, -0.4), lean=0.3, twist=1.0, bob=3, near=(0.45, 0.1), near_out=(0.25, 0.05),
             far=(1.7, 2.0), blade=(1.0, 0.12, 0.1), edge=(0.0, 1.0, -0.55), mouth=1.0, heat=1.0, phase=0.45),
        pose(legs=(0.45, -0.4), lean=0.32, twist=1.3, bob=3, near=(0.3, -0.2), near_out=(-0.35, -0.9),
             far=(1.7, 2.0), blade=(0.25, 0.1, -1.0), edge=(-0.8, 0.8, 0.0), mouth=0.6, heat=0.9, phase=0.6),
        ready,
    ]
    # death: staggered by the killing blow, drops the cleaver and clutches the core, the knees give, he slumps while
    # the cracks spread, then throws his head back in a last roar as the core flares (the game makes him burst)
    death = [
        pose(lean=-0.35, head_up=0.9, mouth=1.0, near=(1.9, 2.3), far=(2.1, 2.5), sword=2.4, heat=1.0, phase=0.1,
             legs=(-0.2, 0.3)),
        pose(lean=-0.15, hunch=0.2, near=(1.95, -0.55), far=(1.7, 2.0), drop=1, mouth=0.7, head_up=0.3, heat=1.1,
             cracks=0.2, phase=0.3),
        pose(hunch=0.4, bob=10, kneel=0.6, near=(1.9, -0.4), far=(1.6, 1.9), drop=1, mouth=0.5, head_up=-0.2,
             heat=1.25, cracks=0.45, crown=0.8, phase=0.5),
        pose(hunch=0.7, bob=16, kneel=1.0, near=(1.6, 1.75), far=(1.65, 1.85), drop=1, mouth=0.3, head_up=-0.8,
             heat=1.5, cracks=0.75, crown=0.55, phase=0.7),
        pose(lean=-0.2, hunch=0.35, bob=15, kneel=1.0, near=(1.75, 2.2), far=(1.9, 2.4), drop=1, mouth=1.0,
             head_up=1.0, heat=2.3, cracks=1.0, crown=1.6, crown_lean=0.2, phase=0.9),
    ]
    return dict(
        death=keys_strip(death, 10),
        idle=loop_strip(idle, 8), walk=loop_strip(walk, 12), slam=keys_strip(slam, 12), cast=keys_strip(cast, 8),
        roar=loop_strip(roar, 4), sheathe=keys_strip(sheathe, 8), charge=loop_strip(charge, 12),
        crash=keys_strip(crash, 4), dazed=loop_strip(dazed, 8), draw=keys_strip(draw, 8), leap=keys_strip(leap, 6),
        stab=keys_strip(stab, 6), swing=keys_strip(swing, 12))


def cleaver_prop():
    """The dropped cleaver alone, in a full Lord frame (same anchor), so the game can leave it exactly where it
    stuck after he bursts."""
    s = Scene(scale=MODEL_SCALE, anchor=FRAME_ANCHOR)
    stuck_cleaver(s)
    return render(s, 'prerender')


def lord_strips():
    out = {f'DemonLord_{name}': [lord_frame(P) for P in poses] for name, poses in lord_poses().items()}
    out['DemonLord_cleaver'] = [cleaver_prop()]
    return out


def strip_image(frames):
    out = Image.new('RGBA', (W * len(frames), H), (0, 0, 0, 0))
    for i, f in enumerate(frames):
        out.paste(f, (i * W, 0))
    return out


def generate(out_dir):
    for name, frames in lord_strips().items():
        strip_image(frames).save(f'{out_dir}/{name}.png')

if __name__ == '__main__':
    if len(sys.argv) >= 3 and sys.argv[1] == 'generate':
        generate(sys.argv[2])
    elif len(sys.argv) >= 3 and sys.argv[1] == 'concepts':
        old = None
        try:
            old_strip = Image.open('src/main/resources/assets/textures/DemonLord_idle.png').convert('RGBA')
            old = old_strip.crop((0, 0, W, H))
        except OSError:
            pass
        concepts_sheet(sys.argv[2], old)
