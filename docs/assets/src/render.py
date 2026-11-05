"""docs/assets/src/*.html 을 헤드리스 Chrome 으로 PNG 렌더링한다.

    python docs/assets/src/render.py            # 전부
    python docs/assets/src/render.py banner     # 하나만

CHROME 환경변수로 브라우저 경로를 바꿀 수 있다 (기본: Windows Chrome → Edge 순).
"""
import os
import pathlib
import shutil
import subprocess
import sys

SRC = pathlib.Path(__file__).resolve().parent
OUT = SRC.parent

# 이름: (CSS 폭, CSS 높이, 배율)
PAGES = {
    "banner": (1600, 520, 2),
    "system-architecture": (1600, 1000, 2),
    "service-architecture": (1600, 1000, 2),
    "deployment-architecture": (1600, 1000, 2),
}

CANDIDATES = [
    os.environ.get("CHROME", ""),
    r"C:\Program Files\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    shutil.which("google-chrome") or "",
    shutil.which("chromium") or "",
]


def chrome() -> str:
    for c in CANDIDATES:
        if c and pathlib.Path(c).exists():
            return c
    sys.exit("Chrome/Edge 를 찾지 못했다. CHROME 환경변수로 경로를 지정한다.")


def render(name: str) -> None:
    w, h, scale = PAGES[name]
    html = SRC / f"{name}.html"
    out = OUT / f"{name}.png"
    cmd = [
        chrome(),
        "--headless=new",
        "--disable-gpu",
        "--hide-scrollbars",
        "--default-background-color=00000000",
        f"--force-device-scale-factor={scale}",
        f"--window-size={w},{h}",
        "--virtual-time-budget=15000",
        f"--screenshot={out}",
        html.as_uri(),
    ]
    subprocess.run(cmd, check=True, capture_output=True)
    print(f"{out.relative_to(OUT.parent.parent)}  {w * scale}x{h * scale}")


if __name__ == "__main__":
    for n in sys.argv[1:] or PAGES:
        render(n)
