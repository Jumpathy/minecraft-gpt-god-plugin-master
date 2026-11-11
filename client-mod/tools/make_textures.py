"""Writes the mod's procedural GUI textures (pure Python, no libraries): a soft radial vignette and a
glowing ring for the pulse. Run from client-mod/: python tools/make_textures.py"""
import math, struct, zlib, os

def png(path, w, h, px):
    raw = b''.join(b'\x00' + bytes(px[y * w * 4:(y + 1) * w * 4]) for y in range(h))
    def chunk(t, d):
        c = struct.pack('>I', len(d)) + t + d
        return c + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    data = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)) \
        + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')
    with open(path, 'wb') as f:
        f.write(data)

out = 'src/main/resources/assets/gptgod/textures/gui'
N = 256
v = []
for y in range(N):
    for x in range(N):
        dx, dy = (x + 0.5) / N * 2 - 1, (y + 0.5) / N * 2 - 1
        d = math.sqrt(dx * dx + dy * dy) / math.sqrt(2)
        a = max(0.0, min(1.0, (d - 0.32) / 0.68)) ** 1.6
        v += [255, 255, 255, int(round(a * 255))]
png(os.path.join(out, 'vignette.png'), N, N, v)

r = []
for y in range(N):
    for x in range(N):
        dx, dy = (x + 0.5) / N * 2 - 1, (y + 0.5) / N * 2 - 1
        d = math.sqrt(dx * dx + dy * dy)
        ring = math.exp(-((d - 0.82) / 0.07) ** 2)
        glow = math.exp(-((d - 0.82) / 0.22) ** 2) * 0.45
        inner = max(0.0, 1 - d / 0.82) ** 2 * 0.18
        a = min(1.0, ring + glow + inner) if d < 1 else 0
        r += [255, 255, 255, int(round(a * 255))]
png(os.path.join(out, 'pulse.png'), N, N, r)

# A soft round dot for HUD bars' glowing ends.
M = 16
g = []
for y in range(M):
    for x in range(M):
        dx, dy = (x + 0.5) / M * 2 - 1, (y + 0.5) / M * 2 - 1
        a = max(0.0, 1 - math.sqrt(dx * dx + dy * dy)) ** 1.5
        g += [255, 255, 255, int(round(a * 255))]
png(os.path.join(out, 'glow.png'), M, M, g)
print('ok')

# ---- The particle sheet: 8 sprites of 64 px in a 4x2 grid (order = Scene.Sprite) ----------------
import random
S = 64
def blank(): return [[0.0] * S for _ in range(S)]
def norm(x, y): return (x + 0.5) / S * 2 - 1, (y + 0.5) / S * 2 - 1
def mote():
    a = blank()
    for y in range(S):
        for x in range(S):
            dx, dy = norm(x, y); d = math.hypot(dx, dy)
            a[y][x] = math.exp(-(d / 0.42) ** 2) * (1 if d < 1 else 0) * max(0, 1 - d) ** 0.5
    return a
def spark():
    a = blank()
    for y in range(S):
        for x in range(S):
            dx, dy = norm(x, y); d = math.hypot(dx, dy)
            cross = math.exp(-(dx / 0.05) ** 2) * math.exp(-(dy / 0.55) ** 2) + math.exp(-(dy / 0.05) ** 2) * math.exp(-(dx / 0.55) ** 2)
            a[y][x] = min(1, cross + math.exp(-(d / 0.16) ** 2))
    return a
def ember():
    a = blank()
    for y in range(S):
        for x in range(S):
            dx, dy = norm(x, y); d = math.hypot(dx, dy)
            a[y][x] = min(1, (1 if d < 0.18 else 0) + math.exp(-(d / 0.3) ** 2) * 0.7) * (1 if d < 1 else 0)
    return a
def rune():
    a = blank()
    rnd = random.Random(7)
    segs = []
    pts = [(math.cos(i * math.tau / 6) * 0.5, math.sin(i * math.tau / 6) * 0.5) for i in range(6)]
    for i in range(6):
        if rnd.random() < 0.75: segs.append((pts[i], pts[(i + 2) % 6]))
    segs.append(((0, -0.6), (0, 0.6)))
    def segd(px, py, p, q):
        vx, vy = q[0] - p[0], q[1] - p[1]; wx, wy = px - p[0], py - p[1]
        t = max(0, min(1, (wx * vx + wy * vy) / (vx * vx + vy * vy)))
        return math.hypot(px - (p[0] + vx * t), py - (p[1] + vy * t))
    for y in range(S):
        for x in range(S):
            dx, dy = norm(x, y); d = math.hypot(dx, dy)
            v = math.exp(-((d - 0.78) / 0.05) ** 2)
            for p, q in segs: v += math.exp(-(segd(dx, dy, p, q) / 0.045) ** 2)
            a[y][x] = min(1, v + math.exp(-((d - 0.78) / 0.18) ** 2) * 0.25)
    return a
def smoke():
    a = blank()
    rnd = random.Random(3)
    blobs = [(rnd.uniform(-0.35, 0.35), rnd.uniform(-0.35, 0.35), rnd.uniform(0.25, 0.45)) for _ in range(9)]
    for y in range(S):
        for x in range(S):
            dx, dy = norm(x, y)
            v = sum(math.exp(-(math.hypot(dx - bx, dy - by) / br) ** 2) for bx, by, br in blobs) / 3.2
            a[y][x] = min(1, v) * max(0, 1 - math.hypot(dx, dy)) ** 0.6 * 0.8
    return a
def star():
    a = blank()
    for y in range(S):
        for x in range(S):
            dx, dy = norm(x, y); d = math.hypot(dx, dy); ang = math.atan2(dy, dx)
            ray = abs(math.cos(ang * 3)) ** 14
            a[y][x] = min(1, math.exp(-(d / 0.2) ** 2) + ray * math.exp(-(d / 0.5) ** 2) * 0.9)
    return a
def shard():
    a = blank()
    for y in range(S):
        for x in range(S):
            dx, dy = norm(x, y)
            e = abs(dx) / 0.32 + abs(dy) / 0.85
            a[y][x] = (0.75 + 0.25 * (dx > 0)) if e < 1 else math.exp(-((e - 1) / 0.18) ** 2) * 0.5
    return a
def ray():
    a = blank()
    for y in range(S):
        for x in range(S):
            dx, dy = norm(x, y)
            a[y][x] = math.exp(-(dx / 0.22) ** 2) * (0.55 + 0.45 * math.exp(-(dx / 0.07) ** 2)) * (1 - abs(dy) ** 6)
    return a
sheet = [0] * (S * 4 * S * 2 * 4)
for idx, f in enumerate([mote, spark, ember, rune, smoke, star, shard, ray]):
    a = f(); ox, oy = (idx % 4) * S, (idx // 4) * S
    for y in range(S):
        for x in range(S):
            edge = min(x, y, S - 1 - x, S - 1 - y)
            v = a[y][x] * (min(1, edge / 2.0) if f is not ray else min(1, min(x, S - 1 - x) / 2.0))
            i = ((oy + y) * S * 4 + ox + x) * 4
            sheet[i:i + 4] = [255, 255, 255, int(round(max(0, min(1, v)) * 255))]
os.makedirs('src/main/resources/assets/gptgod/textures/particle', exist_ok=True)
png('src/main/resources/assets/gptgod/textures/particle/sheet.png', S * 4, S * 2, sheet)
print('sheet ok')
