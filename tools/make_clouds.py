"""Convert generated sky image into a seamless RGBA cloud texture."""

from PIL import Image

SRC = r"C:\Users\garag\.cursor\projects\c-Users-garag-OneDrive-Documents-Opencraft4\assets\clouds.png"
DST = r"c:\Users\garag\OneDrive\Documents\Opencraft4\src\main\resources\textures\sky\clouds.png"

im = Image.open(SRC).convert("RGB").resize((256, 256), Image.Resampling.LANCZOS)
px = im.load()
out = Image.new("RGBA", (256, 256))
op = out.load()
for y in range(256):
    for x in range(256):
        r, g, b = px[x, y]
        bright = (r + g + b) / 3.0
        sky = max(0.0, b - r * 0.55 - g * 0.35)
        cloud = max(0.0, bright - 95.0 - sky * 0.8)
        a = int(max(0, min(255, cloud * 2.2)))
        if a < 18:
            a = 0
        t = bright / 255.0
        cr = int(min(255, 220 + 35 * t))
        cg = int(min(255, 225 + 30 * t))
        cb = int(min(255, 235 + 20 * t))
        op[x, y] = (cr, cg, cb, a)

w, h = out.size
edge = 16
for y in range(h):
    for x in range(edge):
        t = x / edge
        left = op[x, y]
        right = op[w - 1 - x, y]
        mix = tuple(int(left[i] * t + right[i] * (1 - t)) for i in range(4))
        op[x, y] = mix
        op[w - 1 - x, y] = mix
for x in range(w):
    for y in range(edge):
        t = y / edge
        top = op[x, y]
        bot = op[x, h - 1 - y]
        mix = tuple(int(top[i] * t + bot[i] * (1 - t)) for i in range(4))
        op[x, y] = mix
        op[x, h - 1 - y] = mix

out.save(DST)
alphas = [op[x, y][3] for y in range(256) for x in range(256)]
print(
    "saved",
    DST,
    "nonzero",
    sum(1 for a in alphas if a > 0),
    "mean_a",
    round(sum(alphas) / len(alphas), 1),
)
