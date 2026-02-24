"""
Generates an animated conduit_flow.png spritesheet for OmnIO.

The texture is a 16x16 plasma/energy flow pattern in grayscale,
stacked vertically as 32 frames. A matching .mcmeta file is also generated.

The grayscale design lets the renderer tint it per conduit type:
  Energy  -> warm orange/yellow
  Fluid   -> blue
  Item    -> green
  Redstone -> red

Output:
  common/src/main/resources/assets/omnio/textures/block/conduit_flow.png
  common/src/main/resources/assets/omnio/textures/block/conduit_flow.png.mcmeta
"""

import json
import math
import os
import struct
import zlib
import random

# ----------------------------------------------------------------
# Configuration
# ----------------------------------------------------------------
SIZE = 16          # pixels per frame (width & height)
FRAMES = 32       # total animation frames
SPEED = 2          # mcmeta "frametime" in ticks (2 = 10fps)

# Noise / plasma parameters
OCTAVES = 4
PERSISTENCE = 0.5
LACUNARITY = 2.0

OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
                       "common", "src", "main", "resources", "assets",
                       "omnio", "textures", "block")

# ----------------------------------------------------------------
# Minimal Perlin noise (no external deps)
# ----------------------------------------------------------------
class PerlinNoise:
    def __init__(self, seed=42):
        rng = random.Random(seed)
        self.p = list(range(256))
        rng.shuffle(self.p)
        self.p *= 2  # duplicate for overflow

    @staticmethod
    def _fade(t):
        return t * t * t * (t * (t * 6 - 15) + 10)

    @staticmethod
    def _lerp(a, b, t):
        return a + t * (b - a)

    @staticmethod
    def _grad(h, x, y, z):
        h &= 15
        u = x if h < 8 else y
        v = y if h < 4 else (x if h in (12, 14) else z)
        return (u if (h & 1) == 0 else -u) + (v if (h & 2) == 0 else -v)

    def noise3(self, x, y, z):
        X = int(math.floor(x)) & 255
        Y = int(math.floor(y)) & 255
        Z = int(math.floor(z)) & 255
        x -= math.floor(x)
        y -= math.floor(y)
        z -= math.floor(z)
        u = self._fade(x)
        v = self._fade(y)
        w = self._fade(z)
        p = self.p
        A  = p[X] + Y;     AA = p[A] + Z;     AB = p[A + 1] + Z
        B  = p[X + 1] + Y; BA = p[B] + Z;     BB = p[B + 1] + Z
        g = self._grad
        L = self._lerp
        return L(
            L(L(g(p[AA],   x,   y,   z),   g(p[BA],   x-1, y,   z),   u),
              L(g(p[AB],   x,   y-1, z),   g(p[BB],   x-1, y-1, z),   u), v),
            L(L(g(p[AA+1], x,   y,   z-1), g(p[BA+1], x-1, y,   z-1), u),
              L(g(p[AB+1], x,   y-1, z-1), g(p[BB+1], x-1, y-1, z-1), u), v), w)

    def fbm(self, x, y, z, octaves=4, persistence=0.5, lacunarity=2.0):
        val = 0.0
        amp = 1.0
        freq = 1.0
        for _ in range(octaves):
            val += self.noise3(x * freq, y * freq, z * freq) * amp
            amp *= persistence
            freq *= lacunarity
        return val


# ----------------------------------------------------------------
# PNG writer (pure Python, no Pillow needed)
# ----------------------------------------------------------------
def write_png(filepath, width, height, pixels_rgba):
    """Write an RGBA PNG. pixels_rgba is a flat list of (r,g,b,a) tuples."""

    def chunk(chunk_type, data):
        c = chunk_type + data
        crc = struct.pack(">I", zlib.crc32(c) & 0xFFFFFFFF)
        return struct.pack(">I", len(data)) + c + crc

    sig = b"\x89PNG\r\n\x1a\n"
    ihdr = chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))

    raw = bytearray()
    idx = 0
    for _y in range(height):
        raw.append(0)  # filter: None
        for _x in range(width):
            r, g, b, a = pixels_rgba[idx]
            raw.extend((r, g, b, a))
            idx += 1

    compressed = zlib.compress(bytes(raw), 9)
    idat = chunk(b"IDAT", compressed)
    iend = chunk(b"IEND", b"")

    with open(filepath, "wb") as f:
        f.write(sig + ihdr + idat + iend)


# ----------------------------------------------------------------
# Generate animated plasma frames
# ----------------------------------------------------------------
def generate():
    os.makedirs(OUT_DIR, exist_ok=True)

    perlin = PerlinNoise(seed=7)
    perlin2 = PerlinNoise(seed=1337)

    width = SIZE
    height = SIZE * FRAMES
    pixels = []

    for frame in range(FRAMES):
        t = frame / FRAMES  # normalized time [0, 1), loops cleanly

        for y in range(SIZE):
            for x in range(SIZE):
                # Tiling coordinates (wrap seamlessly)
                nx = x / SIZE
                ny = y / SIZE

                # Use 4D trick for seamless tiling in both space and time
                # Map x -> circle in (cx, sx), y -> circle in (cy, sy), t -> circle in (ct, st)
                cx = math.cos(nx * 2 * math.pi) * 0.4
                sx = math.sin(nx * 2 * math.pi) * 0.4
                cy = math.cos(ny * 2 * math.pi) * 0.4
                sy = math.sin(ny * 2 * math.pi) * 0.4

                # Time as a circular coordinate for seamless looping
                ct = math.cos(t * 2 * math.pi) * 0.3
                st = math.sin(t * 2 * math.pi) * 0.3

                # Base plasma layer (slow, large scale)
                v1 = perlin.fbm(cx + ct, cy + st, sx + sy,
                                octaves=OCTAVES, persistence=PERSISTENCE,
                                lacunarity=LACUNARITY)

                # Secondary swirl layer (faster, offset)
                v2 = perlin2.fbm(sx + ct * 1.5 + 10, sy + st * 1.5 + 10,
                                  cx + cy + ct,
                                  octaves=3, persistence=0.6, lacunarity=2.2)

                # Combine: primary plasma + secondary detail
                val = v1 * 0.6 + v2 * 0.4

                # Remap from roughly [-1, 1] to [0, 1]
                val = (val + 1.0) * 0.5
                val = max(0.0, min(1.0, val))

                # Gentle contrast — keep it bright overall, just add some variation
                # Floor at 0.35 so nothing goes truly dark
                val = 0.35 + val * 0.65
                # Soft S-curve for mild contrast without crushing
                val = val ** 0.85

                # Add bright "veins" — softer highlights from second noise
                vein = perlin2.noise3(cx * 3 + ct * 2, cy * 3 + st * 2, sx * 3 + sy * 3)
                vein = max(0.0, (vein + 0.4)) ** 1.5  # softer threshold
                vein = min(1.0, vein)

                # Final brightness: mostly base plasma, veins add pop
                brightness = val * 0.7 + vein * 0.3
                # Raise the floor so the dimmest areas are still visible
                brightness = 0.3 + brightness * 0.7
                brightness = max(0.0, min(1.0, brightness))

                # Output as true grayscale — vertex color tinting handles hue
                gray = int(brightness * 255)
                pixels.append((gray, gray, gray, 255))

    # Write PNG
    png_path = os.path.join(OUT_DIR, "conduit_flow.png")
    write_png(png_path, width, height, pixels)
    print(f"Written: {png_path} ({width}x{height}, {FRAMES} frames)")

    # Write .mcmeta
    mcmeta = {
        "animation": {
            "interpolate": True,
            "frametime": SPEED
        }
    }
    mcmeta_path = png_path + ".mcmeta"
    with open(mcmeta_path, "w") as f:
        json.dump(mcmeta, f, indent=2)
    print(f"Written: {mcmeta_path}")


if __name__ == "__main__":
    generate()
