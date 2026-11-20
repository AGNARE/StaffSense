# -*- coding: utf-8 -*-
"""Кадр только формы записки (без бокового меню и чужих фамилий).

Снимается от сотрудника, обрезается по рамке карточки: в документе нужна
сама форма, а не подписи людей из демо-набора.
"""

import asyncio
import base64
import os
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
os.chdir(HERE)

import _shots as S


async def step_clip(cdp):
    await cdp.goto(f"{S.BASE}/app/#/memos", wait_ms=2200)
    await cdp.js("""
        (() => {
          const b = [...document.querySelectorAll('button')]
            .find(x => x.textContent.trim() === 'Новая записка');
          if (b) { b.click(); }
          return !!b;
        })()
    """)
    await asyncio.sleep(0.9)
    rect = await cdp.js("""
        (() => {
          const card = document.querySelector('header.topbar .card');
          if (!card) return null;
          card.style.position = 'relative';
          card.style.zIndex = '9999';
          card.style.boxShadow = 'none';
          const r = card.getBoundingClientRect();
          return {x: r.x, y: r.y, width: r.width, height: r.height};
        })()
    """)
    print("  rect:", rect)
    await asyncio.sleep(0.3)
    clip = {"x": rect["x"], "y": rect["y"], "width": rect["width"],
            "height": rect["height"], "scale": 2}
    data = await cdp.call("Page.captureScreenshot", format="png", clip=clip,
                          captureBeyondViewport=True)
    path = S.IMG / "memo-form-clip.png"
    path.write_bytes(base64.b64decode(data["data"]))
    print("  saved", path.name, path.stat().st_size // 1024, "KB")


S.EMAIL = "employee@staffsense.com"
S.STEP["clip"] = step_clip

if __name__ == "__main__":
    asyncio.run(S.main(["clip"]))
