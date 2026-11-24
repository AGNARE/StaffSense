# -*- coding: utf-8 -*-
"""Снимки экранов StaffSense для статьи: радар, карточка разбора, пульт HR.

Только реальный стенд (http://localhost:9091), headless Chrome через CDP,
окно 1440x900, профиль C:\\Users\\tz-review-staffsense\\fig. Адреса почты в
кадре замазываются серым прямоугольником (Pillow) по координатам, снятым с
DOM, — кадр при этом остаётся настоящим.

Запуск: python _gen_shots.py
"""

import json
import sys
from pathlib import Path

sys.path.insert(0, r"C:\Users\Argen\Products\StaffSense\docs\deliverables")

from PIL import Image, ImageDraw  # noqa: E402

from _cdp import Browser  # noqa: E402

HERE = Path(__file__).resolve().parent
OUT = HERE / "figures"
RAW = HERE / "_work" / "raw"
PROFILE = r"C:\Users\tz-review-staffsense\fig"
BASE = "http://localhost:9091"
EMAIL = "hr@staffsense.com"
PASSWORD = "password"
DSF = 2

# Поиск адресов почты в текстовых узлах: возвращает прямоугольники в CSS px.
EMAIL_RECTS_JS = r"""
(() => {
  const re = /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/;
  const out = [];
  const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
  let node;
  while ((node = walker.nextNode())) {
    if (!re.test(node.nodeValue || '')) continue;
    const range = document.createRange();
    range.selectNodeContents(node);
    for (const rect of range.getClientRects()) {
      if (rect.width > 2 && rect.height > 2) {
        out.push([rect.left, rect.top, rect.right, rect.bottom]);
      }
    }
  }
  return out;
})()
"""


def login(browser):
    browser.goto(f"{BASE}/login", settle=0.6)
    browser.eval_js(f"""
        (() => {{
          const u = document.querySelector('#username');
          const p = document.querySelector('#password');
          if (!u || !p) return 'no form';
          u.value = {EMAIL!r};
          p.value = {PASSWORD!r};
          document.querySelector('form').submit();
          return 'sent';
        }})()
    """)
    import time
    time.sleep(3.0)
    browser._wait_ready()
    where = browser.eval_js("location.href")
    print("после входа:", where)
    if "/login" in where:
        raise RuntimeError("вход не выполнен: " + str(where))
    return where


def mask_emails(png_path, rects):
    """Серые плашки поверх найденных адресов; координаты — в CSS px."""
    if not rects:
        return 0
    image = Image.open(png_path).convert("RGB")
    draw = ImageDraw.Draw(image)
    for left, top, right, bottom in rects:
        box = [
            int(left * DSF) - 3,
            int(top * DSF) - 2,
            int(right * DSF) + 3,
            int(bottom * DSF) + 2,
        ]
        draw.rectangle(box, fill=(158, 163, 172))
    image.save(png_path)
    return len(rects)


def capture(browser, name, crop=None, reset_scroll=True):
    """Снимок вьюпорта: сначала сырой кадр, потом (опционально) обрезка."""
    if reset_scroll:
        browser.eval_js("window.scrollTo(0, 0)")
    import time
    time.sleep(0.35)
    rects = browser.eval_js(EMAIL_RECTS_JS) or []
    raw = RAW / f"{name}-raw.png"
    browser.shot(raw)
    masked = mask_emails(raw, rects)
    if crop:
        image = Image.open(raw)
        left, top, right, bottom = [int(v * DSF) for v in crop]
        image = image.crop((left, top, right, bottom))
    else:
        image = Image.open(raw)
    target = OUT / f"{name}.png"
    OUT.mkdir(parents=True, exist_ok=True)
    image.save(target)
    print(f"{name}: {image.size[0]}x{image.size[1]} px, замазано адресов: {masked}")
    return target, masked


def main():
    RAW.mkdir(parents=True, exist_ok=True)
    OUT.mkdir(parents=True, exist_ok=True)
    meta = {}

    browser = Browser(PROFILE, width=1440, height=900, headless=True)
    try:
        session = browser.open_tab("about:blank")
        browser.send("Emulation.setDeviceMetricsOverride", {
            "width": 1440, "height": 900, "deviceScaleFactor": DSF, "mobile": False,
        }, session=session)
        login(browser)

        # ── Р4: радар рисков под ролью HR ────────────────────────────────
        browser.goto(f"{BASE}/app/#/risk", settle=3.0)
        rows = browser.eval_js("""
            (() => [...document.querySelectorAll('table.table tbody tr')]
              .map(tr => tr.innerText.replace(/\\s+/g, ' ').trim())
              .filter(Boolean).slice(0, 20))()
        """)
        kpi = browser.eval_js("""
            (() => [...document.querySelectorAll('.kpi')]
              .map(k => k.innerText.replace(/\\s+/g, ' ').trim()))()
        """)
        head = browser.eval_js("""
            (() => {
              const t = document.querySelector('.t-hero');
              const s = document.querySelector('.t-sm.muted');
              return [t ? t.innerText : null, s ? s.innerText : null];
            })()
        """)
        role_menu = browser.eval_js("""
            (() => [...document.querySelectorAll('a.sb-item')]
              .map(a => a.innerText.replace(/\\s+/g, ' ').trim()))()
        """)
        print("радар:", head)
        print("KPI:", kpi)
        print("меню:", role_menu)
        for row in rows:
            print("  строка:", row)
        meta["radar"] = {"head": head, "kpi": kpi, "rows": rows, "menu": role_menu}
        capture(browser, "fig4-risk-radar", crop=(0, 0, 1440, 760))

        # ── Р5: карточка разбора прогноза ───────────────────────────────
        browser.goto(f"{BASE}/app/#/risk", settle=2.5)
        clicked = browser.eval_js("""
            (() => {
              const tr = [...document.querySelectorAll('table.table tbody tr')]
                .find(r => r.querySelector('.risk-score.is-high'));
              if (!tr) return null;
              tr.click();
              return true;
            })()
        """)
        import time
        time.sleep(3.0)
        browser._wait_ready()
        where = browser.eval_js("location.href")
        print("переход:", clicked, where)
        card = browser.eval_js("""
            (() => document.querySelector('.page') ? document.querySelector('.page').innerText : document.body.innerText)()
        """)
        scroll = browser.eval_js("""
            (() => {
              const el = document.querySelector('.page') || document.scrollingElement;
              return [document.body.scrollHeight, window.innerHeight,
                      document.scrollingElement.scrollHeight,
                      document.documentElement.scrollHeight];
            })()
        """)
        print("карточка, высота:", scroll)
        print("карточка, текст:\n", (card or "")[:2500])
        meta["card"] = {"url": where, "text": card, "scroll": scroll}
        capture(browser, "fig5-risk-card", crop=(0, 0, 1440, 845))

        # ── Р6: пульт HR, журнал кадровых операций ───────────────────────
        browser.goto(f"{BASE}/app/#/hr-desk", settle=3.5)
        info = browser.eval_js("""
            (() => {
              const cards = [...document.querySelectorAll('.card-hd .t-h3')]
                .map(h => h.innerText.trim());
              const target = [...document.querySelectorAll('.card-hd .t-h3')]
                .find(h => h.innerText.includes('Мои последние действия'));
              const box = target ? target.closest('.card').getBoundingClientRect() : null;
              // Журнал ставим под шапку: в кадр попадают и шапка, и все строки.
              if (box) window.scrollTo(0, Math.max(0, window.scrollY + box.top - 92));
              return {cards, scrollY: window.scrollY,
                      height: document.documentElement.scrollHeight};
            })()
        """)
        print("блоки пульта:", info.get("cards"))
        print("прокрутка:", info.get("scrollY"), "из", info.get("height"))
        import time
        # Ставим журнал под шапку: измеряем и доводим прокрутку за два прохода,
        # потому что после первой прокрутки карточки выше меняют высоту.
        for _ in range(2):
            top = browser.eval_js("""
                (() => {
                  const t = [...document.querySelectorAll('.card-hd .t-h3')]
                    .find(h => h.innerText.includes('Мои последние действия'));
                  const card = t ? t.closest('.card') : null;
                  if (!card) return null;
                  window.scrollTo(0, Math.max(0, window.scrollY + card.getBoundingClientRect().top - 92));
                  return card.getBoundingClientRect().top;
                })()
            """)
            time.sleep(0.5)
        rect = browser.eval_js("""
            (() => {
              const t = [...document.querySelectorAll('.card-hd .t-h3')]
                .find(h => h.innerText.includes('Мои последние действия'));
              const card = t ? t.closest('.card') : null;
              const r = card ? card.getBoundingClientRect() : null;
              return {top: r ? r.top : 0, bottom: r ? r.bottom : 900,
                      scrollY: window.scrollY};
            })()
        """)
        print("блоки пульта:", info.get("cards"))
        print("карточка журнала:", rect)
        bottom = min(800, round(rect["bottom"] + 22))
        capture(browser, "fig6-hr-actions", crop=(0, 0, 1440, bottom),
                reset_scroll=False)
        actions = browser.eval_js("""
            (() => {
              const t = [...document.querySelectorAll('.card-hd .t-h3')]
                .find(h => h.innerText.includes('Мои последние действия'));
              const card = t ? t.closest('.card') : null;
              return card ? card.innerText.replace(/\\s+/g, ' ').trim() : 'нет блока';
            })()
        """)
        print("журнал действий:", actions)
        meta["desk_actions"] = actions
    finally:
        browser.close()

    (HERE / "_work" / "shots-meta.json").write_text(
        json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
