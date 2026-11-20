# -*- coding: utf-8 -*-
"""Снимок «Радар рисков» от имени руководителя (pm@staffsense.com).

Нужен для 04: в разделе «Руководитель» на экране должны быть сотрудники
его команды, а не общая картина HR.
"""

import asyncio
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import os
os.chdir(HERE)

import _shots as S


async def step_risk(cdp):
    await cdp.goto(f"{S.BASE}/app/#/risk", wait_ms=2600)
    text = await cdp.js("document.body.innerText.slice(0, 120)")
    print("  page:", text.replace("\n", " | ")[:100])
    await cdp.shot("risk-radar-pm.png")


S.EMAIL = "pm@staffsense.com"
S.STEP["risk"] = step_risk

if __name__ == "__main__":
    asyncio.run(S.main(["risk"]))
