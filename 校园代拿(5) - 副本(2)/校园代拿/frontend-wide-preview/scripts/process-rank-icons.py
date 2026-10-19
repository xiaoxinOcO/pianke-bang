from pathlib import Path
from PIL import Image

src_dir = Path(r"c:\Users\黄俊达\Desktop\校园代拿(5) - 副本(2)(1)\论文")
dst_dir = Path(r"c:\Users\黄俊达\Desktop\校园代拿(5) - 副本(2)(1)\校园代拿(5) - 副本(2)\校园代拿\frontend\public\admin-rank")
dst_dir.mkdir(parents=True, exist_ok=True)

mapping = {
    "第一.png": "rank-1.png",
    "第二.png": "rank-2.png",
    "第三.png": "rank-3.png",
}


def process(src: Path, dst: Path, size: int = 128) -> None:
    im = Image.open(src).convert("RGBA")
    pixels = im.load()
    w, h = im.size
    for y in range(h):
        for x in range(w):
            r, g, b, _a = pixels[x, y]
            if r < 28 and g < 28 and b < 28:
                pixels[x, y] = (0, 0, 0, 0)
    bbox = im.getbbox()
    if bbox:
        im = im.crop(bbox)
    cw, ch = im.size
    side = max(cw, ch)
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    canvas.paste(im, ((side - cw) // 2, (side - ch) // 2), im)
    out = canvas.resize((size, size), Image.Resampling.NEAREST)
    out.save(dst, "PNG")
    print(f"{src.name} -> {dst.name} {out.size} {dst.stat().st_size} bytes")


for src_name, dst_name in mapping.items():
    src = src_dir / src_name
    if not src.exists():
        raise SystemExit(f"missing: {src}")
    process(src, dst_dir / dst_name)

print("done", [p.name for p in sorted(dst_dir.glob("*.png"))])
