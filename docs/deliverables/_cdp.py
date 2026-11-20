# -*- coding: utf-8 -*-
"""
Минимальный клиент Chrome DevTools Protocol для съёмки скриншотов StaffSense.

Зачем свой: selenium и playwright в этом окружении не установлены, а тянуть
их ради шести кадров не нужно. websockets уже есть в бандле рантайма.

Запуск Chrome — только с --disable-features=CalculateNativeWinOcclusion:
без этого флага headless-окно считается окклюдированным и события ввода
не доставляются (это же ограничение описано в отчёте QA).
"""

import base64
import json
import os
import shutil
import socket
import subprocess
import time
import urllib.request
from pathlib import Path

from websockets.sync.client import connect

CHROME = r"C:\Program Files\Google\Chrome\Application\chrome.exe"


def _free_port(start=9333):
    for port in range(start, start + 40):
        with socket.socket() as sock:
            try:
                sock.bind(("127.0.0.1", port))
            except OSError:
                continue
            return port
    raise RuntimeError("нет свободного порта")


class Browser:
    def __init__(self, profile, width=1440, height=900, headless=True):
        self.profile = Path(profile)
        self.profile.mkdir(parents=True, exist_ok=True)
        self.width = width
        self.height = height
        self.port = _free_port()
        self.session_id = None
        self._msg_id = 0

        args = [
            CHROME,
            f"--remote-debugging-port={self.port}",
            f"--user-data-dir={self.profile}",
            f"--window-size={width},{height}",
            "--disable-features=CalculateNativeWinOcclusion",
            "--no-first-run",
            "--no-default-browser-check",
            "--disable-background-networking",
            "--disable-sync",
            "--disable-extensions",
            "--hide-scrollbars",
            "--force-device-scale-factor=1",
            "--lang=ru-RU",
        ]
        if headless:
            args.append("--headless=new")

        self.proc = subprocess.Popen(
            args, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
        )
        self._wait_endpoint()
        self._connect()

    # ── транспорт ────────────────────────────────────────────────────────
    def _wait_endpoint(self, timeout=30):
        deadline = time.time() + timeout
        while time.time() < deadline:
            try:
                with urllib.request.urlopen(
                        f"http://127.0.0.1:{self.port}/json/version", timeout=2) as rsp:
                    self.version = json.load(rsp)
                    return
            except Exception:
                time.sleep(0.25)
        raise RuntimeError("Chrome не открыл порт отладки")

    def _connect(self):
        self.ws = connect(self.version["webSocketDebuggerUrl"],
                          max_size=64 * 1024 * 1024, open_timeout=20)

    def send(self, method, params=None, session=None, timeout=30):
        self._msg_id += 1
        msg = {"id": self._msg_id, "method": method, "params": params or {}}
        if session:
            msg["sessionId"] = session
        self.ws.send(json.dumps(msg))
        deadline = time.time() + timeout
        while time.time() < deadline:
            raw = self.ws.recv(timeout=max(0.1, deadline - time.time()))
            data = json.loads(raw)
            if data.get("id") == self._msg_id:
                if "error" in data:
                    raise RuntimeError(f"{method}: {data['error']}")
                return data.get("result", {})
        raise TimeoutError(method)

    # ── вкладка ──────────────────────────────────────────────────────────
    def open_tab(self, url="about:blank"):
        target = self.send("Target.createTarget", {"url": url})
        attached = self.send("Target.attachToTarget",
                             {"targetId": target["targetId"], "flatten": True})
        self.session_id = attached["sessionId"]
        self.send("Page.enable", session=self.session_id)
        self.send("Runtime.enable", session=self.session_id)
        self.send("Emulation.setDeviceMetricsOverride", {
            "width": self.width, "height": self.height,
            "deviceScaleFactor": 1, "mobile": False,
        }, session=self.session_id)
        return self.session_id

    def goto(self, url, settle=1.6):
        self.send("Page.navigate", {"url": url}, session=self.session_id)
        self._wait_ready()
        time.sleep(settle)

    def _wait_ready(self, timeout=25):
        deadline = time.time() + timeout
        while time.time() < deadline:
            state = self.eval_js("document.readyState")
            if state == "complete":
                return
            time.sleep(0.2)

    def reload(self, settle=2.0):
        self.send("Page.reload", {"ignoreCache": True}, session=self.session_id)
        self._wait_ready()
        time.sleep(settle)

    def eval_js(self, expression, timeout=30):
        result = self.send("Runtime.evaluate", {
            "expression": expression, "returnByValue": True,
            "awaitPromise": True,
        }, session=self.session_id, timeout=timeout)
        if "exceptionDetails" in result:
            raise RuntimeError(result["exceptionDetails"])
        return result.get("result", {}).get("value")

    def shot(self, path):
        result = self.send("Page.captureScreenshot", {
            "format": "png", "captureBeyondViewport": False,
        }, session=self.session_id)
        path = Path(path)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(base64.b64decode(result["data"]))
        return path

    def close(self):
        try:
            self.ws.close()
        except Exception:
            pass
        try:
            self.proc.terminate()
            self.proc.wait(timeout=10)
        except Exception:
            try:
                self.proc.kill()
            except Exception:
                pass
