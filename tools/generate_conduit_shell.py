"""
Generates conduit_shell.png – a 16x16 static texture for the conduit shell/border.

Design: dark base with a subtle checker-pattern darkening in the corners (1-2px).
The corners are slightly darker, giving a beveled / industrial pipe look.
Centre area is uniform so the flow texture shows cleanly on top.
"""
import os, struct, zlib, json

SIZE = 16
OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
                       "common", "src", "main", "resources", "assets",
                       "omnio", "textures", "block")

def write_png(filepath, width, height, pixels_rgba):
    def chunk(chunk_type, data):
        c = chunk_type + data
        crc = struct.pack(">I", zlib.crc32(c) & 0xFFFFFFFF)
        return struct.pack(">I", len(data)) + c + crc
    sig = b"\x89PNG\r\n\x1a\n"
    ihdr = chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    raw = bytearray()
    idx = 0
    for _y in range(height):
        raw.append(0)
        for _x in range(width):
            r, g, b, a = pixels_rgba[idx]
            raw.extend((r, g, b, a))
            idx += 1
    compressed = zlib.compress(bytes(raw), 9)
    idat = chunk(b"IDAT", compressed)
    iend = chunk(b"IEND", b"")
    with open(filepath, "wb") as f:
        f.write(sig + ihdr + idat + iend)

def generate():
    os.makedirs(OUT_DIR, exist_ok=True)
    pixels = []

    # Base gray values — texture should be BRIGHT since vertex color handles the tint.
    # The pattern is relative: corners slightly darker, edges a touch darker, center bright.
    BASE = 220       # main shell area — bright, vertex color darkens it
    CORNER_DARK = 160 # darker corner pixels (checker)
    CORNER_LITE = 190 # checker lighter pixel in corners
    EDGE = 200        # 1px border slightly darker than base

    for y in range(SIZE):
        for x in range(SIZE):
            # Distance from each edge in pixels
            dx = min(x, SIZE - 1 - x)
            dy = min(y, SIZE - 1 - y)
            d_corner = min(dx, dy)  # distance from nearest corner

            if d_corner <= 1:
                # Corner region (0-1 px from corner): checker pattern
                if (x + y) % 2 == 0:
                    v = CORNER_DARK
                else:
                    v = CORNER_LITE
            elif dx == 0 or dy == 0:
                # Outer edge: slightly darker than base
                v = EDGE
            elif dx == 1 or dy == 1:
                # Inner edge: subtle transition
                v = BASE - 10
            else:
                # Centre area: base gray, tiny noise for texture
                # Simple deterministic pseudo-noise
                noise = ((x * 7 + y * 13) % 5) - 2  # range -2..2
                v = BASE + noise

            v = max(0, min(255, v))
            pixels.append((v, v, v, 255))

    out = os.path.join(OUT_DIR, "conduit_shell.png")
    write_png(out, SIZE, SIZE, pixels)
    print(f"Written: {out}")

if __name__ == "__main__":
    generate()
