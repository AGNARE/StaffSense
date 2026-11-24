# -*- coding: utf-8 -*-
"""Схемы для статьи: процесс работы HR, поток данных модели, ER-диаграмма процесса.

Каждый блок нарисован по коду, а не «по памяти»:

* маршруты и шаги — `frontend/src/App.tsx` (`#/risk`, `#/risk/:id`, `#/my-day`,
  `#/memos`, `#/documents`, `#/hr-desk`, `#/admin`) и `pages/*.tsx`;
* поток данных — `StaffSenseML/dataset.py` (700 записей, 560/140, метки —
  розыгрыш Бернулли), `train.py` (StandardScaler + LogisticRegression),
  `demo/.../api/risk/*` (`/api/v1/risk/model`, `/api/v1/predict`);
* ER — только `REFERENCES` из миграций V1, V6, V9, V19.

Оформление: DejaVu Serif (Liberation Serif в окружении нет), белый фон,
чёрные рамки, DPI 200, ширина 13 см — ровно в размер вставки, поэтому
кегль на рисунке равен кеглю в статье и не опускается ниже 8 пт.
Скрипт сам проверяет, что ни одна подпись не выходит за рамку блока.

Запуск: python _gen_schemes.py
"""

from pathlib import Path

import matplotlib

matplotlib.use("Agg")

import matplotlib.pyplot as plt  # noqa: E402
from matplotlib.patches import FancyArrowPatch, Rectangle  # noqa: E402

OUT = Path(__file__).resolve().parent / "figures"

CM = 1 / 2.54
WIDTH_CM = 13.0

INK = "#111111"
MUTED = "#2b2b2b"

plt.rcParams.update({
    "font.family": "serif",
    "font.serif": ["DejaVu Serif"],
    "font.size": 8,
    "text.color": INK,
    "savefig.facecolor": "white",
    "figure.facecolor": "white",
})

LINE_H = 0.31
_checks = []


def canvas(height_cm):
    fig = plt.figure(figsize=(WIDTH_CM * CM, height_cm * CM), dpi=200)
    ax = fig.add_axes([0, 0, 1, 1])
    ax.set_xlim(0, WIDTH_CM)
    ax.set_ylim(0, height_cm)
    ax.set_aspect("equal")
    ax.axis("off")
    return fig, ax


def box(ax, x, y, w, h, titles, lines, *, title_size=8.5, body_size=8.0,
        align="center", pad=0.12):
    """Прямоугольник с заголовком (str или список строк) и строками."""
    ax.add_patch(Rectangle((x, y), w, h, facecolor="white", edgecolor=INK,
                           linewidth=0.9, zorder=2))
    cy = y + h - pad - 0.17
    title_lines = [titles] if isinstance(titles, str) else list(titles)
    for title in title_lines:
        artist = ax.text(x + w / 2, cy, title, ha="center", va="center",
                         fontsize=title_size, fontweight="bold", zorder=3)
        _checks.append((artist, (x, y, w, h)))
        cy -= LINE_H + 0.06
    anchor_x, ha = (x + w / 2, "center") if align == "center" else (x + 0.10, "left")
    for line in lines:
        artist = ax.text(anchor_x, cy, line, ha=ha, va="center",
                         fontsize=body_size, color=MUTED, zorder=3)
        _checks.append((artist, (x, y, w, h)))
        cy -= LINE_H


def badge(ax, x, y, number):
    """Номер шага в разрыве рамки над стрелкой перехода."""
    ax.text(x, y, number, ha="center", va="center", fontsize=8.0,
            fontweight="bold", color=INK, zorder=6,
            bbox=dict(boxstyle="square,pad=0.12", facecolor="white",
                      edgecolor="none"))


def arrow(ax, start, end, *, lw=0.9):
    ax.add_patch(FancyArrowPatch(
        start, end, arrowstyle="-|>", mutation_scale=8, linewidth=lw,
        color=INK, shrinkA=0, shrinkB=0, zorder=1))


def line(ax, start, end, *, lw=0.9):
    ax.plot([start[0], end[0]], [start[1], end[1]], color=INK, linewidth=lw,
            zorder=1, solid_capstyle="butt")


def note(ax, x, y, text, *, size=8.0, ha="left", italic=True):
    ax.text(x, y, text, ha=ha, va="center", fontsize=size, color=MUTED,
            style="italic" if italic else "normal")


def verify(name, ax):
    """Проверка: каждая подпись должна лежать внутри своей рамки."""
    renderer = ax.figure.canvas.get_renderer()
    problems = 0
    for artist, (bx, by, bw, bh) in _checks:
        extent = artist.get_window_extent(renderer=renderer)
        inv = ax.transData.inverted()
        (x0, y0), (x1, y1) = inv.transform([[extent.x0, extent.y0], [extent.x1, extent.y1]])
        if x0 < bx - 0.02 or x1 > bx + bw + 0.02 or y0 < by - 0.02 or y1 > by + bh + 0.02:
            problems += 1
            print(f"  [{name}] выход за рамку: {artist.get_text()!r} "
                  f"({x1 - x0:.2f} см при ширине {bw:.2f})")
    _checks.clear()
    return problems


# ───────────────────────────────────────────────────────────────────────────
# Рис. 7 — процесс работы HR: от сигнала к действию
# ───────────────────────────────────────────────────────────────────────────
def scheme_process():
    height = 5.0
    fig, ax = canvas(height)

    row1 = [
        ("Радар рисков", ["#/risk", "сотрудники, уровни,", "стоимость под риском"]),
        ("Карточка разбора", ["#/risk/:id", "признаки, факторы,", "динамика, источник"]),
        ("Пересчёт прогноза", ["POST /api/v1/risk/{id}", "новый балл;", "ML или эвристика"]),
    ]
    row2 = [
        ("Решение", ["#/my-day · #/memos",
                     "#/documents",
                     "1:1, записка, документ"]),
        ("Повторная оценка", ["#/admin, карточка",
                              "массовый пересчёт:",
                              "новый снимок в журнале"]),
    ]

    left, gap = 0.15, 0.34
    wide_w = (WIDTH_CM - 2 * left - gap) / 2
    narrow_w = (WIDTH_CM - 2 * left - 2 * gap) / 3
    box_h = 1.52
    top1, top2 = 4.92, 2.86

    centers1 = []
    for index, (title, lines) in enumerate(row1):
        x = left + index * (narrow_w + gap)
        box(ax, x, top1 - box_h, narrow_w, box_h, title, lines)
        centers1.append(x + narrow_w / 2)
        if index:
            arrow(ax, (x - gap + 0.02, top1 - box_h / 2), (x - 0.02, top1 - box_h / 2))

    centers2 = []
    for index, (title, lines) in enumerate(row2):
        x = left + index * (wide_w + gap)
        box(ax, x, top2 - box_h, wide_w, box_h, title, lines)
        centers2.append(x + wide_w / 2)
        if index:
            arrow(ax, (x - gap + 0.02, top2 - box_h / 2), (x - 0.02, top2 - box_h / 2))

    # Переход с первого ряда на второй: вниз у правого края, затем влево.
    bus_y = (top1 - box_h + top2) / 2
    line(ax, (centers1[2], top1 - box_h), (centers1[2], bus_y))
    line(ax, (centers1[2], bus_y), (centers2[0] + 0.55, bus_y))
    arrow(ax, (centers2[0] + 0.55, bus_y), (centers2[0] + 0.55, top2))

    # Обратная связь: новый снимок прогноза возвращается в радар и карточку.
    back_y = 0.86
    line(ax, (centers2[1], top2 - box_h), (centers2[1], back_y))
    line(ax, (centers2[1], back_y), (centers1[0] + 0.35, back_y))
    arrow(ax, (centers1[0] + 0.35, back_y), (centers1[0] + 0.35, top1 - box_h))
    note(ax, (centers1[0] + 0.35 + centers2[1]) / 2, back_y - 0.24,
         "новый снимок попадает в журнал: тренд карточки и радар пересчитываются",
         ha="center")

    fig.savefig(OUT / "fig7-hr-process.png", dpi=200, facecolor="white")
    problems = verify("рис. 7", ax)
    plt.close(fig)
    return problems


# ───────────────────────────────────────────────────────────────────────────
# Рис. 8 — поток данных модели
# ───────────────────────────────────────────────────────────────────────────
def scheme_dataflow():
    height = 5.75
    fig, ax = canvas(height)

    blocks = [
        ("1", ["7 признаков"], ["зарплата, нагрузка, KPI,", "стаж, удовлетворённость,", "1:1, переработки"]),
        ("2", ["Синтетический набор"], ["700 записей:", "560 — обучение,", "140 — проверка"]),
        ("3", ["Логистическая", "регрессия"],
         ["LogisticRegression", "+ StandardScaler"]),
        ("4", ["Вероятность и веса"], ["сигмоида → балл 0–100;", "коэффициенты признаков"]),
        ("5", ["API"], ["/api/v1/predict — балл;", "/api/v1/risk/model —", "веса и пороги"]),
        ("6", ["Интерфейс"], ["карточка риска — балл", "и факторы; экран", "«ML-модель» — веса"]),
    ]

    left, gap = 0.15, 0.32
    box_w = (WIDTH_CM - 2 * left - 2 * gap) / 3
    box_h = 1.72
    top1, top2 = 5.60, 2.94

    positions = {}
    for index, (number, titles, lines) in enumerate(blocks):
        row, col = divmod(index, 3)
        x = left + col * (box_w + gap)
        top = top1 if row == 0 else top2
        box(ax, x, top - box_h, box_w, box_h, titles, lines)
        positions[index] = (x, top)

    for index in (0, 1, 3, 4):
        x, top = positions[index]
        arrow(ax, (x + box_w + 0.02, top - box_h / 2), (x + box_w + gap - 0.02, top - box_h / 2))

    # Переход от блока 3 к блоку 4 — вниз по центру третьего блока и влево.
    x3, top3 = positions[2]
    x4, top4 = positions[3]
    mid_y = 3.30
    line(ax, (x3 + box_w / 2, top3 - box_h), (x3 + box_w / 2, mid_y))
    line(ax, (x3 + box_w / 2, mid_y), (x4 + box_w / 2, mid_y))
    arrow(ax, (x4 + box_w / 2, mid_y), (x4 + box_w / 2, top4))

    # Блок данных подписан как синтетический: это не выборка организации.
    note(ax, 0.15, 0.98,
         "Набор синтетический: реальных уходов нет, метки — розыгрыш Бернулли")
    note(ax, 0.15, 0.66,
         "от истинной вероятности генератора (dataset.py, seed 42).")

    fig.savefig(OUT / "fig8-model-flow.png", dpi=200, facecolor="white")
    problems = verify("рис. 8", ax)
    plt.close(fig)
    return problems


# ───────────────────────────────────────────────────────────────────────────
# Рис. 9 — ER-диаграмма сущностей процесса
# ───────────────────────────────────────────────────────────────────────────
def scheme_er():
    height = 7.05
    fig, ax = canvas(height)

    entities = [
        ("attrition_predictions",
         ["id · user_id → users · computed_at",
          "risk_score, risk_level, top_risk_factors_json"]),        ("attrition_prediction_history",
         ["id · user_id → users · computed_by_id",
          "risk_score, computed_at"]),
        ("service_memo",
         ["id · user_id → users · memo_type, status,",
          "pm_decision_by_id, hr_decision_by_id → users"]),
        ("one_on_one_meetings",
         ["id · employee_id → users · manager_id → users",
          "missed, scheduled_at, status"]),
        ("notification",
         ["id · user_id → users · type",
          "title, link, read_at, created_at"]),
    ]

    users_w, users_h = 4.55, 2.95
    users_x, users_y = 0.15, 1.95
    box(ax, users_x, users_y, users_w, users_h, "users",
        ["id, email, full_name, role,",
         "department, grade_level,",
         "salary, hire_date, active,",
         "manager_id → users"],
        title_size=9.0, align="left")

    ent_x = 5.55
    ent_w = WIDTH_CM - ent_x - 0.15
    ent_h = 1.20
    gap = 0.12
    top = 6.73
    bus_x = 5.18
    ys = []
    for title, lines in entities:
        box(ax, ent_x, top - ent_h, ent_w, ent_h, title, lines,
            title_size=8.2, body_size=8.0)
        ys.append(top - ent_h / 2)
        top -= ent_h + gap

    # Сборная шина внешних ключей: каждый блок подключается к users.
    line(ax, (bus_x, ys[0]), (bus_x, ys[-1]))
    for y in ys:
        line(ax, (bus_x, y), (ent_x - 0.01, y))
    arrow(ax, (bus_x, users_y + users_h / 2), (users_x + users_w + 0.01, users_y + users_h / 2))
    ax.text(bus_x + 0.08, users_y + users_h / 2 + 0.20, "N:1",
            ha="left", va="bottom", fontsize=8.0, color=INK)

    # Пояснения вынесены в подпись рисунка: так схема занимает меньше места
    # по высоте и целиком помещается в текст статьи.


    fig.savefig(OUT / "fig9-er-process.png", dpi=200, facecolor="white")
    problems = verify("рис. 9", ax)
    plt.close(fig)
    return problems


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    total = 0
    total += scheme_process()
    total += scheme_dataflow()
    total += scheme_er()
    print("сохранено в", OUT, "| проблем с рамками:", total)
