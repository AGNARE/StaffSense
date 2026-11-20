# -*- coding: utf-8 -*-
"""
Снимки экрана StaffSense через Chrome DevTools Protocol.

Скрипт сам поднимает headless Chrome с отдельным профилем, входит одной
учётной записью (ограничитель входа — 5 попыток в минуту, поэтому входов
ровно столько, сколько нужно) и сохраняет PNG в img/.

Запуск: python _shots.py [menu my-day memo-form vacations swagger]
"""

import asyncio
import base64
import json
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

import websockets

HERE = Path(__file__).resolve().parent
IMG = HERE / "img"
CHROME = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
PROFILE = r"C:\Users\tz-review-staffsense\shots_ru"
PORT = 9333
BASE = "http://localhost:9091"
EMAIL = "hr@staffsense.com"
PASSWORD = "password"


# --------------------------------------------------------------------------
# Минимальный клиент CDP: одно соединение, последовательные вызовы.
# --------------------------------------------------------------------------

class CDP:
    def __init__(self, ws):
        self.ws = ws
        self.next_id = 0

    async def call(self, method, **params):
        self.next_id += 1
        message_id = self.next_id
        await self.ws.send(json.dumps({"id": message_id, "method": method, "params": params}))
        while True:
            raw = await asyncio.wait_for(self.ws.recv(), timeout=60)
            data = json.loads(raw)
            if data.get("id") == message_id:
                if "error" in data:
                    raise RuntimeError(f"{method}: {data['error']}")
                return data.get("result", {})
            # События (Page.loadEventFired и пр.) пропускаем.

    async def js(self, expression, await_promise=False):
        result = await self.call(
            "Runtime.evaluate",
            expression=expression,
            returnByValue=True,
            awaitPromise=await_promise,
        )
        if "exceptionDetails" in result:
            raise RuntimeError(result["exceptionDetails"])
        return result.get("result", {}).get("value")

    async def shot(self, name):
        data = await self.call("Page.captureScreenshot", format="png")
        path = IMG / name
        path.write_bytes(base64.b64decode(data["data"]))
        print("  saved", path.name, path.stat().st_size // 1024, "KB")
        return path

    async def goto(self, url, wait_ms=1500):
        await self.call("Page.navigate", url=url)
        await asyncio.sleep(1.0)
        await self.wait_ready()
        await asyncio.sleep(wait_ms / 1000)

    async def wait_ready(self):
        for _ in range(60):
            state = await self.js("document.readyState")
            if state == "complete":
                return
            await asyncio.sleep(0.25)


def launch():
    IMG.mkdir(parents=True, exist_ok=True)
    Path(PROFILE).mkdir(parents=True, exist_ok=True)
    log = open(HERE / "_chrome.log", "w", encoding="utf-8")
    args = [
        CHROME,
        "--headless=new",
        f"--remote-debugging-port={PORT}",
        f"--user-data-dir={PROFILE}",
        "--window-size=1440,900",
        "--disable-features=CalculateNativeWinOcclusion",
        "--hide-scrollbars",
        "--no-first-run",
        "--no-default-browser-check",
        "--disable-extensions",
        "--disable-gpu",
        "--disable-dev-shm-usage",
        "--force-device-scale-factor=1",
        "about:blank",
    ]
    process = subprocess.Popen(args, stdout=log, stderr=log)
    for _ in range(80):
        try:
            with urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json/list", timeout=1) as response:
                targets = json.loads(response.read())
            pages = [t for t in targets if t.get("type") == "page"]
            if pages:
                return process, pages[0]["webSocketDebuggerUrl"]
        except Exception:
            pass
        time.sleep(0.25)
    raise RuntimeError("Chrome не поднялся на порту отладки")


async def main(steps):
    process, ws_url = launch()
    print("chrome:", ws_url)
    try:
        async with websockets.connect(ws_url, max_size=None) as ws:
            cdp = CDP(ws)
            await cdp.call("Page.enable")
            await cdp.call("Runtime.enable")
            await cdp.call("Emulation.setDeviceMetricsOverride",
                           width=1440, height=900, deviceScaleFactor=1, mobile=False)

            # --- вход ---
            await cdp.goto(f"{BASE}/login", wait_ms=400)
            print("login:", await cdp.js("document.title"))
            await cdp.js(f"""
                document.querySelector('#username').value = {EMAIL!r};
                document.querySelector('#password').value = {PASSWORD!r};
                document.querySelector('form').submit();
                'ok'
            """)
            await asyncio.sleep(2.5)
            await cdp.wait_ready()
            print("after login:", await cdp.js("location.href"))

            for step in steps:
                print("step:", step)
                await STEP[step](cdp)
    finally:
        process.terminate()
        try:
            process.wait(timeout=5)
        except Exception:
            process.kill()


# --------------------------------------------------------------------------
# Шаги: каждый — переход и снимок
# --------------------------------------------------------------------------

CLICK_ITEM = """
    (() => {
      const el = [...document.querySelectorAll('a.sb-item')]
        .find(a => a.textContent.trim().startsWith(%r));
      if (!el) return 'not found';
      // preventDefault гасит переход, но обработчик React получает событие
      // и раскрывает группу: иначе уход со страницы сбрасывает openGroups.
      el.addEventListener('click', (event) => event.preventDefault(), {once: true});
      el.click();
      return true;
    })()
"""


async def step_menu(cdp):
    # «Документы» раскрыты сразу: страница реестра — активный подпункт группы.
    await cdp.goto(f"{BASE}/app/#/documents", wait_ms=1500)
    print("  click Портал:", await cdp.js(CLICK_ITEM % "Портал"))
    await asyncio.sleep(0.9)
    print("  open groups:", await cdp.js("""
        [...document.querySelectorAll('a.sb-item[aria-expanded="true"]')]
          .map(a => a.textContent.trim())
    """))
    print("  nav:", await cdp.js("""
        (() => { const n = document.querySelector('aside nav');
          return n ? [n.scrollHeight, n.clientHeight, n.scrollTop] : null; })()
    """))
    # Меню длиннее окна: прокручиваем к концу, чтобы обе раскрытые группы
    # и «Система» попали в кадр целиком.
    await cdp.js("""
        (() => { const n = document.querySelector('aside nav');
          if (n) { n.scrollTop = n.scrollHeight; }
          return n ? n.scrollTop : null; })()
    """)
    await asyncio.sleep(0.4)
    await cdp.shot("menu.png")


async def step_my_day(cdp):
    await cdp.goto(f"{BASE}/app/#/my-day", wait_ms=2200)
    await cdp.shot("my-day.png")


async def step_memo_form(cdp):
    await cdp.goto(f"{BASE}/app/#/memos", wait_ms=2000)
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
    # Карточка формы живёт в верхней панели и при высоте 60 px обрезается
    # ею же: сдвигаем её вниз, чтобы в кадр попали все поля, а не половина.
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
    await cdp.shot("memo-form.png")


async def step_vacations(cdp):
    await cdp.goto(f"{BASE}/app/#/vacations", wait_ms=2200)
    await cdp.shot("vacations.png")


async def step_swagger(cdp):
    await cdp.goto(f"{BASE}/swagger-ui/index.html", wait_ms=1000)
    # springdoc строит спецификацию на первый запрос: ждём, пока UI
    # разберёт её и нарисует хотя бы один тег ресурса.
    for _ in range(40):
        info = await cdp.js("""
            ({tags: document.querySelectorAll('.opblock-tag').length,
              loading: document.body.innerText.includes('LOADING')})
        """)
        if info["tags"]:
            break
        await asyncio.sleep(0.5)
    print("  tags:", info)
    await asyncio.sleep(0.8)
    await cdp.shot("swagger.png")


STEP = {
    "menu": step_menu,
    "my-day": step_my_day,
    "memo-form": step_memo_form,
    "vacations": step_vacations,
    "swagger": step_swagger,
}


if __name__ == "__main__":
    names = sys.argv[1:] or ["menu", "my-day", "memo-form", "vacations", "swagger"]
    asyncio.run(main(names))
