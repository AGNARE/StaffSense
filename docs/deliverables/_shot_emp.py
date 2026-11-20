# -*- coding: utf-8 -*-
"""Снимки от имени сотрудника: форма записки (для раздела «Сотрудник» в 04).

Кадр повторяет step_memo_form из _shots.py, но сохраняется под другим именем,
чтобы не перезаписать кадр, снятый от HR.
"""

import asyncio
import os
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
os.chdir(HERE)

import _shots as S


async def step_memo_emp(cdp):
    await cdp.goto(f"{S.BASE}/app/#/memos", wait_ms=2200)
    clicked = await cdp.js("""
        (() => {
          const b = [...document.querySelectorAll('button')]
            .find(x => x.textContent.trim() === 'Новая записка');
          if (b) { b.click(); return true; }
          return false;
        })()
    """)
    print("  clicked:", clicked)
    await asyncio.sleep(0.8)
    moved = await cdp.js("""
        (() => {
          const card = document.querySelector('header.topbar .card');
          if (!card) return 'no card';
          card.style.position = 'relative';
          card.style.zIndex = '9999';
          card.style.transform = 'translateY(96px)';
          card.style.boxShadow = '0 18px 50px rgba(16,24,40,.22)';
          card.style.width = '340px';
          return card.innerText.replace(/\\n+/g, ' | ');
        })()
    """)
    print("  form:", moved)
    await asyncio.sleep(0.5)
    await cdp.shot("memo-form-emp.png")


S.EMAIL = "employee@staffsense.com"
S.STEP["memo-emp"] = step_memo_emp

if __name__ == "__main__":
    asyncio.run(S.main(["memo-emp"]))
