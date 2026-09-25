"""Remove near-white backgrounds from UI assets via edge flood-fill."""
from collections import deque
from pathlib import Path

from PIL import Image

SRC = Path(r"C:\Users\garag\.cursor\projects\c-Users-garag-OneDrive-Documents-Opencraft4\assets")
DST = Path(r"c:\Users\garag\OneDrive\Documents\Opencraft4\src\main\resources\ui\img")
RUN = Path(r"c:\Users\garag\OneDrive\Documents\Opencraft4\run\ui\img")

FILES = ["logo.png", "button.png", "button_hover.png", "input_field.png", "panel.png"]


def is_white(rgb, threshold):
    r, g, b = rgb
    # Near-white: all channels high and close to each other
    return r >= threshold and g >= threshold and b >= threshold and (max(r, g, b) - min(r, g, b)) <= 18


def remove_white(im, threshold=245):
    im = im.convert("RGBA")
    w, h = im.size
    px = im.load()
    visited = [[False] * h for _ in range(w)]
    q = deque()

    def try_seed(x, y):
        if visited[x][y]:
            return
        r, g, b, a = px[x, y]
        if is_white((r, g, b), threshold):
            visited[x][y] = True
            q.append((x, y))

    for x in range(w):
        try_seed(x, 0)
        try_seed(x, h - 1)
    for y in range(h):
        try_seed(0, y)
        try_seed(w - 1, y)

    while q:
        x, y = q.popleft()
        r, g, b, _ = px[x, y]
        px[x, y] = (r, g, b, 0)
        for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if 0 <= nx < w and 0 <= ny < h and not visited[nx][ny]:
                nr, ng, nb, na = px[nx, ny]
                if is_white((nr, ng, nb), threshold):
                    visited[nx][ny] = True
                    q.append((nx, ny))

    # Second pass: clear enclosed white (e.g. letter holes) that touches transparent
    changed = True
    while changed:
        changed = False
        for y in range(h):
            for x in range(w):
                r, g, b, a = px[x, y]
                if a == 0 or not is_white((r, g, b), threshold):
                    continue
                for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                    if 0 <= nx < w and 0 <= ny < h and px[nx, ny][3] == 0:
                        px[x, y] = (r, g, b, 0)
                        changed = True
                        break
    return im


def crop_alpha(im, pad=4):
    bbox = im.getbbox()
    if not bbox:
        return im
    l, t, r, b = bbox
    return im.crop((max(0, l - pad), max(0, t - pad), min(im.width, r + pad), min(im.height, b + pad)))


def main():
    DST.mkdir(parents=True, exist_ok=True)
    for name in FILES:
        src = SRC / name
        im = Image.open(src)
        # Slightly lower threshold for logo so soft white fringes go away
        threshold = 238 if name == "logo.png" else 245
        out = crop_alpha(remove_white(im, threshold=threshold))
        out.save(DST / name)
        if RUN.exists():
            RUN.mkdir(parents=True, exist_ok=True)
            out.save(RUN / name)
        # Keep a transparent copy next to generated asset too
        out.save(SRC / name)
        corner = out.getpixel((0, 0))[3]
        print(f"{name}: {out.size}, corner_alpha={corner}")


if __name__ == "__main__":
    main()
