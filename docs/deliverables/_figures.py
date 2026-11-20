# -*- coding: utf-8 -*-
"""
Схемы для документов StaffSense: архитектура платформы и распределение тестов.

Обе картинки рассчитаны на вставку шириной 15 см при 200 dpi (1181 px),
поэтому кегли подобраны под масштаб 1:1 — в документе они не уменьшаются.

Шрифт: Liberation Serif, если он есть в системе. На этой машине его нет,
а Times New Roman метрически с ним совпадает (Liberation Serif — его
свободный клон), поэтому подставляется он.
"""

import sys
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.font_manager as fm
import matplotlib.pyplot as plt
from matplotlib.patches import FancyArrowPatch, Rectangle

HERE = Path(__file__).resolve().parent
IMG = HERE / "img"
IMG.mkdir(exist_ok=True)

DPI = 200
CM = 1 / 2.54
WIDTH_IN = 15 * CM  # 5.906 in


def pick_font():
    names = {f.name for f in fm.fontManager.ttflist}
    for candidate in ("Liberation Serif", "Times New Roman", "DejaVu Serif"):
        if candidate in names:
            return candidate
    return "DejaVu Serif"


FONT = pick_font()
plt.rcParams["font.family"] = FONT
plt.rcParams["font.serif"] = [FONT]
plt.rcParams["axes.unicode_minus"] = False


def box(ax, x0, y0, x1, y1, *, fill="white", lw=1.1, ls="-", radius=0.0, z=2):
    if radius:
        patch = Rectangle((x0, y0), x1 - x0, y1 - y0, facecolor=fill,
                          edgecolor="black", linewidth=lw, linestyle=ls,
                          zorder=z, joinstyle="round")
    else:
        patch = Rectangle((x0, y0), x1 - x0, y1 - y0, facecolor=fill,
                          edgecolor="black", linewidth=lw, linestyle=ls, zorder=z)
    ax.add_patch(patch)
    return patch


def arrow(ax, start, end, label=None, *, style="-|>", lw=1.1, label_dy=1.6,
          label_dx=0.0, rad=0.0, ha="center"):
    patch = FancyArrowPatch(start, end, arrowstyle=style, mutation_scale=9,
                            linewidth=lw, color="black", shrinkA=1.5,
                            shrinkB=1.5, connectionstyle=f"arc3,rad={rad}",
                            zorder=3)
    ax.add_patch(patch)
    if label:
        mx = (start[0] + end[0]) / 2 + label_dx
        my = (start[1] + end[1]) / 2 + label_dy
        ax.text(mx, my, label, ha=ha, va="bottom", fontsize=7.4, zorder=4)


def arch():
    fig = plt.figure(figsize=(WIDTH_IN, 3.75), dpi=DPI)
    ax = fig.add_axes([0, 0, 1, 1])
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 62)
    ax.axis("off")
    fig.patch.set_facecolor("white")

    # ── браузер ──────────────────────────────────────────────────────────
    box(ax, 1.0, 25.5, 20.0, 51)
    ax.text(10.5, 46.6, "Браузер", ha="center", va="center", fontsize=9.4,
            fontweight="bold")
    for index, line in enumerate(("React 18 + TypeScript",
                                  "SPA · маршруты #/risk",
                                  "статика по адресу /app/")):
        ax.text(10.5, 41.0 - index * 3.6, line, ha="center", va="center", fontsize=6.7)

    # ── Spring Boot ──────────────────────────────────────────────────────
    box(ax, 30.5, 16.5, 66.0, 51)
    ax.text(48.2, 47.5, "Spring Boot 3.2.5 · порт 9091", ha="center", va="center",
            fontsize=9.4, fontweight="bold")
    ax.text(48.2, 44.6, "модульный монолит · Java 21", ha="center", va="center",
            fontsize=6.9, style="italic")

    inner = [(34.0, 36.4, 62.5, 42.6, "Контроллеры",
              "REST /api/v1/** · 84 операции"),
             (34.0, 28.2, 62.5, 34.4, "Сервисы",
              "12 доменов · бизнес-правила"),
             (34.0, 20.0, 62.5, 26.2, "Репозитории",
              "Spring Data JPA · 30 сущностей")]
    for x0, y0, x1, y1, title, subtitle in inner:
        box(ax, x0, y0, x1, y1, lw=0.9)
        ax.text(48.2, (y0 + y1) / 2 + 1.0, title, ha="center", va="center",
                fontsize=8.1, fontweight="bold")
        ax.text(48.2, (y0 + y1) / 2 - 1.5, subtitle, ha="center", va="center",
                fontsize=6.5)

    for y_from, y_to in ((36.4, 34.4), (28.2, 26.2)):
        ax.annotate("", xy=(48.2, y_to), xytext=(48.2, y_from),
                    arrowprops=dict(arrowstyle="-|>", color="black",
                                    linewidth=1.0, mutation_scale=8), zorder=3)

    # ── PostgreSQL ───────────────────────────────────────────────────────
    box(ax, 76.0, 25.5, 99.0, 51)
    ax.text(87.5, 46.6, "PostgreSQL 18", ha="center", va="center",
            fontsize=9.4, fontweight="bold")
    for index, line in enumerate(("порт 5433", "Flyway · 20 миграций",
                                  "30 таблиц", "режим validate")):
        ax.text(87.5, 41.0 - index * 3.3, line, ha="center", va="center", fontsize=6.7)

    # ── ML-сервис ────────────────────────────────────────────────────────
    box(ax, 30.5, 1.0, 66.0, 10.5)
    ax.text(48.2, 7.7, "ML-сервис · порт 8001", ha="center", va="center",
            fontsize=9.4, fontweight="bold")
    ax.text(48.2, 4.9, "FastAPI · scikit-learn · model.pkl", ha="center",
            va="center", fontsize=6.5)
    ax.text(48.2, 2.5, "7 признаков · прогноз вероятности ухода", ha="center",
            va="center", fontsize=6.5)

    # ── связи ────────────────────────────────────────────────────────────
    arrow(ax, (20.0, 39.0), (30.5, 39.0), "HTTP/JSON")
    arrow(ax, (66.0, 39.0), (76.0, 39.0), "JDBC")
    arrow(ax, (48.2, 16.5), (48.2, 10.5), None)
    ax.text(49.6, 13.5, "HTTP · таймаут 2 с", ha="left", va="center", fontsize=7.2)

    path = IMG / "arch.png"
    fig.savefig(path, dpi=DPI, facecolor="white")
    plt.close(fig)
    return path


def test_report():
    """Распределение 369 автотестов по пакетам с привязкой к уровням."""
    rows = [
        ("service/**", 163, "модульный"),
        ("api/**", 158, "интеграционный"),
        ("journey/**", 15, "сквозной"),
        ("controller/**", 14, "вспомогательный"),
        ("config/**", 9, "вспомогательный"),
        ("корневой пакет", 7, "вспомогательный"),
        ("repository/**", 3, "вспомогательный"),
    ]
    colors = {"модульный": "#1f3a5f", "интеграционный": "#2f6f8f",
              "сквозной": "#5b8f6a", "вспомогательный": "#9aa7b4"}

    fig = plt.figure(figsize=(WIDTH_IN, 8.0 * CM * 1.0 / 1.0), dpi=DPI)
    ax = fig.add_axes([0.20, 0.20, 0.76, 0.62])
    fig.patch.set_facecolor("white")

    labels = [r[0] for r in rows][::-1]
    values = [r[1] for r in rows][::-1]
    kinds = [r[2] for r in rows][::-1]
    ypos = range(len(values))

    ax.barh(list(ypos), values, height=0.62,
            color=[colors[k] for k in kinds], edgecolor="black", linewidth=0.6,
            zorder=3)
    ax.set_yticks(list(ypos))
    ax.set_yticklabels(labels, fontsize=8.2)
    ax.set_xlim(0, 195)
    ax.set_xlabel("Тестов", fontsize=8.2)
    ax.tick_params(axis="x", labelsize=7.4, length=2.5)
    ax.tick_params(axis="y", length=0)
    ax.grid(axis="x", color="#c9d2db", linewidth=0.5, linestyle=":", zorder=0)
    ax.set_axisbelow(True)
    for spine in ("top", "right"):
        ax.spines[spine].set_visible(False)
    for spine in ("left", "bottom"):
        ax.spines[spine].set_linewidth(0.7)

    for y, value in zip(ypos, values):
        ax.text(value + 3, y, str(value), va="center", ha="left", fontsize=7.8)

    fig.text(0.02, 0.945,
             "Распределение 369 автоматических тестов по пакетам",
             fontsize=9.4, fontweight="bold", ha="left")
    fig.text(0.02, 0.885,
             "68 классов: 364 метода @Test и 5 параметризованных прогонов по ролям; "
             "падений, ошибок и пропусков — 0",
             fontsize=7.4, ha="left")
    fig.text(0.02, 0.075,
             "Приёмочный уровень — 28 сценариев в браузере четырьмя агентами — "
             "выполняется отдельно и в 369 тестов не входит.",
             fontsize=7.0, style="italic", ha="left", color="#33404d")
    fig.text(0.02, 0.028,
             "163 + 158 + 15 + 33 = 369 тестов",
             fontsize=7.8, ha="left", color="#33404d")

    path = IMG / "test-report.png"
    fig.savefig(path, dpi=DPI, facecolor="white")
    plt.close(fig)
    return path


if __name__ == "__main__":
    print("шрифт схем:", FONT)
    for fn in (arch, test_report):
        print("сохранено:", fn())


