from PIL import Image
from collections import deque
from pathlib import Path

img_dir = Path(r"c:\Users\garag\OneDrive\Documents\Opencraft4\src\main\resources\ui\img")
extra = Path(r"C:\Users\garag\.cursor\projects\c-Users-garag-OneDrive-Documents-Opencraft4\assets")


def color_dist(a, b):
    return abs(a[0] - b[0]) + abs(a[1] - b[1]) + abs(a[2] - b[2])


def edge_seeds(w, h, step=1):
    seeds = []
    for x in range(0, w, step):
        seeds.append((x, 0))
        seeds.append((x, h - 1))
    for y in range(0, h, step):
        seeds.append((0, y))
        seeds.append((w - 1, y))
    return seeds


def remove_bg_flood(im, threshold):
    im = im.convert("RGBA")
    w, h = im.size
    px = im.load()
    seed_colors = []
    for x, y in edge_seeds(w, h, step=1):
        c = px[x, y][:3]
        if all(color_dist(c, sc) > 8 for sc in seed_colors):
            seed_colors.append(c)
        if len(seed_colors) > 32:
            break
    for x in range(0, w, 4):
        for y in (0, 1, 2, 3, h - 1, h - 2, h - 3, h - 4):
            c = px[x, y][:3]
            if all(color_dist(c, sc) > 10 for sc in seed_colors):
                seed_colors.append(c)
    for y in range(0, h, 4):
        for x in (0, 1, 2, 3, w - 1, w - 2, w - 3, w - 4):
            c = px[x, y][:3]
            if all(color_dist(c, sc) > 10 for sc in seed_colors):
                seed_colors.append(c)

    visited = [[False] * h for _ in range(w)]
    q = deque()
    for x, y in edge_seeds(w, h, step=1):
        c = px[x, y][:3]
        if any(color_dist(c, sc) <= threshold for sc in seed_colors):
            q.append((x, y))
            visited[x][y] = True

    while q:
        x, y = q.popleft()
        r, g, b, a = px[x, y]
        if not any(color_dist((r, g, b), sc) <= threshold for sc in seed_colors):
            continue
        px[x, y] = (r, g, b, 0)
        for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if 0 <= nx < w and 0 <= ny < h and not visited[nx][ny]:
                nr, ng, nb, na = px[nx, ny]
                if any(color_dist((nr, ng, nb), sc) <= threshold for sc in seed_colors):
                    visited[nx][ny] = True
                    q.append((nx, ny))
    return im


def remove_remaining_dark_edges(im, lim=55):
    w, h = im.size
    px = im.load()
    visited = [[False] * h for _ in range(w)]
    q = deque()
    for x, y in edge_seeds(w, h, 1):
        r, g, b, a = px[x, y]
        if a > 0 and max(r, g, b) <= lim:
            q.append((x, y))
            visited[x][y] = True
        elif a == 0:
            visited[x][y] = True
            q.append((x, y))
    while q:
        x, y = q.popleft()
        r, g, b, a = px[x, y]
        if a > 0 and max(r, g, b) <= lim + 15 and (max(r, g, b) - min(r, g, b)) < 25:
            px[x, y] = (r, g, b, 0)
        for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if 0 <= nx < w and 0 <= ny < h and not visited[nx][ny]:
                nr, ng, nb, na = px[nx, ny]
                if na == 0 or (
                    max(nr, ng, nb) <= lim + 20 and (max(nr, ng, nb) - min(nr, ng, nb)) < 30
                ):
                    visited[nx][ny] = True
                    q.append((nx, ny))
    return im


def remove_logo_bg(im):
    im = im.convert("RGBA")
    px = im.load()
    w, h = im.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            mx = max(r, g, b)
            mn = min(r, g, b)
            sat = mx - mn
            warm = r > g - 5 and r >= b and r > 40
            bright_warm = mx > 90 and r >= g and r >= b - 10 and sat > 25
            brown_side = r > 35 and g > 15 and b < r and (r - b) > 15 and mx < 160
            if (warm and sat > 18 and mx > 55) or bright_warm or brown_side:
                continue
            if mx < 70 or (sat < 20 and mx < 100) or (sat < 15 and mx < 140):
                px[x, y] = (r, g, b, 0)
    return remove_remaining_dark_edges(im, lim=55)


def crop_alpha(im, pad=4):
    bbox = im.getbbox()
    if not bbox:
        return im
    l, t, r, b = bbox
    l = max(0, l - pad)
    t = max(0, t - pad)
    r = min(im.width, r + pad)
    b = min(im.height, b + pad)
    return im.crop((l, t, r, b))


targets = {
    "logo.png": ("logo", 28),
    "button.png": ("solid", 45),
    "button_hover.png": ("solid", 35),
    "input_field.png": ("solid", 35),
    "panel.png": ("solid", 40),
}

for name, (kind, threshold) in targets.items():
    path = img_dir / name
    im = Image.open(path)
    if kind == "logo":
        out = remove_logo_bg(im)
        out = remove_bg_flood(out, threshold=threshold)
        out = remove_logo_bg(out)
    else:
        out = remove_bg_flood(im, threshold=threshold)
        if name in ("button_hover.png", "input_field.png", "panel.png"):
            out = remove_remaining_dark_edges(out, lim=25)
    out = crop_alpha(out, pad=4)
    out.save(path)
    opaque = sum(1 for p in out.getdata() if p[3] > 0)
    print(f"updated {name} -> {out.size}, opaque={opaque}")
    if (extra / name).exists():
        out.save(extra / name)

print("done")
