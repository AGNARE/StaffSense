# -*- coding: utf-8 -*-
"""
Схемы для документов 03 и 05: связи таблиц, структура себестоимости,
окупаемость по сценариям. Все числа берутся из расчёта _gen_estimate.py.

Шрифт — Liberation Serif; если он не установлен, берётся метрически
совместимый Times New Roman (тот же рисунок шрифта), иначе DejaVu Serif.
DPI 200, ширина 15 см — как вставляются рисунки в документы.
"""

from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.font_manager as fm
import matplotlib.pyplot as plt
from matplotlib.patches import FancyArrowPatch, FancyBboxPatch

HERE = Path(__file__).resolve().parent
IMG = HERE / "img"
IMG.mkdir(exist_ok=True)

WIDTH_IN = 15 / 2.54          # 15 см
DPI = 200

# Типографский минус и неразрывный пробел в подписях осей не нужны,
# но тысячи разделяем пробелом и десятичную часть — запятой.
def n(value, digits=2):
    text = f"{value:,.{digits}f}".replace(",", " ").replace(".", ",")
    return text


def pick_font():
    installed = {f.name for f in fm.fontManager.ttflist}
    for name in ("Liberation Serif", "Times New Roman", "DejaVu Serif"):
        if name in installed:
            return name
    return "serif"


FONT = pick_font()


def setup():
    plt.rcParams.update({
        "font.family": FONT,
        "font.size": 8,
        "axes.edgecolor": "#5a6b7c",
        "axes.labelcolor": "#26323e",
        "text.color": "#26323e",
        "xtick.color": "#4a5a68",
        "ytick.color": "#4a5a68",
        "figure.dpi": DPI,
        "savefig.dpi": DPI,
    })


# --------------------------------------------------------------------------
# 1. Схема таблиц и связей (для руководства программиста)
# --------------------------------------------------------------------------

BOX_W_A = 4.2
BOX_W_B = 5.6
COL_A = 4.2
COL_B = 9.2

ROWS = [
    (8.35, 9.95),
    (5.95, 7.65),
    (3.55, 5.25),
    (1.15, 2.85),
]

TABLES = {
    "users": (0.15, 5.60, 3.25, 4.40, [
        ("id", "PK"), ("email", "UQ"), ("full_name", ""),
        ("role", ""), ("department", ""), ("manager_id", "FK"), ("active", ""),
    ]),
    "projects": (COL_A, ROWS[0][0], BOX_W_A, ROWS[0][1] - ROWS[0][0], [
        ("id", "PK"), ("name", ""), ("owner_pm_id", "FK"), ("created_at", ""),
    ]),
    "tasks": (COL_A, ROWS[1][0], BOX_W_A, ROWS[1][1] - ROWS[1][0], [
        ("id", "PK"), ("project_id", "FK"), ("assignee_id", "FK"),
        ("status / priority", ""), ("estimate_hours", ""), ("start_date / due_date", ""),
    ]),
    "document": (COL_A, ROWS[2][0], BOX_W_A, ROWS[2][1] - ROWS[2][0], [
        ("id", "PK"), ("reg_number", "UQ"), ("doc_type / status", ""),
        ("employee_id", "FK"), ("author_id", "FK"), ("due_at", ""),
    ]),
    "document_signature": (COL_A, ROWS[3][0], BOX_W_A, ROWS[3][1] - ROWS[3][0], [
        ("id", "PK"), ("document_id", "FK"), ("signer_id", "FK"),
        ("decision / signed_at", ""),
    ]),
    "vacancy": (COL_B, ROWS[0][0], BOX_W_B, ROWS[0][1] - ROWS[0][0], [
        ("id", "PK"), ("title / department", ""), ("status / openings", ""),
        ("created_by", "FK"), ("opened_at / closed_at", ""),
    ]),
    "candidate": (COL_B, ROWS[1][0], BOX_W_B, ROWS[1][1] - ROWS[1][0], [
        ("id", "PK"), ("vacancy_id", "FK"), ("full_name / email", ""),
        ("stage", ""), ("applied_at", ""),
    ]),
    "service_memo": (COL_B, ROWS[2][0], BOX_W_B, ROWS[2][1] - ROWS[2][0], [
        ("id", "PK"), ("user_id", "FK"), ("memo_type / status", ""),
        ("start_date / end_date", ""), ("pm_decision_by_id", "FK"),
        ("hr_decision_by_id", "FK"),
    ]),
    "notification": (COL_B, ROWS[3][0], BOX_W_B, ROWS[3][1] - ROWS[3][0], [
        ("id", "PK"), ("user_id", "FK"), ("type / title", ""),
        ("link / read_at", ""),
    ]),
}


def draw_table(ax, name, x, y, w, h, fields):
    title_h = 0.52
    box = FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0,rounding_size=0.06",
                         linewidth=0.9, edgecolor="#34506e", facecolor="#f8fafc",
                         zorder=2)
    ax.add_patch(box)
    ax.add_patch(plt.Rectangle((x, y + h - title_h), w, title_h,
                               facecolor="#23405f", edgecolor="#23405f", zorder=3))
    ax.text(x + 0.12, y + h - title_h / 2, name, color="white", fontsize=7.6,
            fontweight="bold", va="center", ha="left", zorder=4,
            fontfamily="monospace")
    step = (h - title_h - 0.10) / max(1, len(fields))
    for index, (field, mark) in enumerate(fields):
        ty = y + h - title_h - 0.10 - step * (index + 0.5)
        weight = "bold" if mark == "PK" else "normal"
        ax.text(x + 0.14, ty, field, fontsize=6.4, va="center", ha="left",
                fontweight=weight, zorder=4)
        if mark:
            ax.text(x + w - 0.12, ty, mark, fontsize=5.6, va="center", ha="right",
                    color="#7b8a99", zorder=4)


def link(ax, points, color="#2f6f9f", label=None, label_at=None, lw=1.0):
    xs = [p[0] for p in points]
    ys = [p[1] for p in points]
    if len(points) > 2:
        ax.plot(xs[:-1], ys[:-1], color=color, lw=lw, zorder=1,
                solid_capstyle="round", solid_joinstyle="round")
    ax.add_patch(FancyArrowPatch(points[-2], points[-1], arrowstyle="-|>",
                                 mutation_scale=7, lw=lw, color=color,
                                 shrinkA=0, shrinkB=0, zorder=1))
    if label and label_at:
        ax.text(label_at[0], label_at[1], label, fontsize=5.9, color="#5a6b7c",
                ha=label_at[2], va=label_at[3], zorder=5)


def db_scheme():
    setup()
    fig = plt.figure(figsize=(WIDTH_IN, 11 / 2.54))
    ax = fig.add_axes([0, 0, 1, 1])
    ax.set_xlim(0, 15)
    ax.set_ylim(0, 11)
    ax.set_aspect("equal")
    ax.axis("off")

    for name, (x, y, w, h, fields) in TABLES.items():
        draw_table(ax, name, x, y, w, h, fields)

    # Связи «users → …»: вертикали выходят из нижней грани разными x,
    # чтобы горизонтальные участки не пересекались.
    users_x = 0.15
    link(ax, [(users_x + 3.25, 9.45), (COL_A - 0.02, 9.15)])
    link(ax, [(users_x + 3.25, 7.05), (COL_A - 0.02, 6.90)])
    # document: employee_id / author_id
    link(ax, [(users_x + 2.45, 5.60), (users_x + 2.45, 4.40), (COL_A - 0.02, 4.40)])
    # document_signature: signer_id
    link(ax, [(users_x + 1.05, 5.60), (users_x + 1.05, 2.00), (COL_A - 0.02, 2.00)])
    # notification: user_id — под всеми таблицами
    link(ax, [(users_x + 0.45, 5.60), (users_x + 0.45, 0.72), (12.0, 0.72),
              (12.0, ROWS[3][0] - 0.02)])
    # service_memo: user_id / pm_decision_by_id / hr_decision_by_id
    link(ax, [(users_x + 1.70, 5.60), (users_x + 1.70, 3.15), (9.0, 3.15),
              (9.0, 4.40), (COL_B - 0.02, 4.40)])
    # vacancy: created_by — над верхним рядом
    link(ax, [(users_x + 3.25, 9.95), (users_x + 3.25, 10.44), (12.0, 10.44),
              (12.0, ROWS[0][1] + 0.02)])

    # Связи между таблицами одного столбца.
    link(ax, [(6.30, ROWS[0][0]), (6.30, ROWS[1][1] + 0.02)])
    link(ax, [(6.30, ROWS[2][0]), (6.30, ROWS[3][1] + 0.02)])
    link(ax, [(12.00, ROWS[0][0]), (12.00, ROWS[1][1] + 0.02)])

    fig.savefig(IMG / "db-tables.png", dpi=DPI, facecolor="white")
    plt.close(fig)
    print("saved db-tables.png")


# --------------------------------------------------------------------------
# 2. Структура себестоимости (для оценки программы)
# --------------------------------------------------------------------------

FOT = 844687.50
OVERHEAD = 337875.00
INFRA = 26400.00
COST = FOT + OVERHEAD + INFRA

SCENARIOS = [("Пессимистичный", 3.6503), ("Базовый", 0.5191), ("Оптимистичный", 0.1782)]
MONTHS = [43.8, 6.2, 2.1]


def cost_structure():
    setup()
    fig = plt.figure(figsize=(WIDTH_IN, 7.2 / 2.54))
    ax = fig.add_axes([0.02, 0.05, 0.50, 0.90])

    values = [FOT, OVERHEAD, INFRA]
    labels = ["ФОТ", "Накладные расходы", "Инфраструктура"]
    colors = ["#1f4e79", "#3f7fb0", "#9dc3d9"]

    ax.pie(values, colors=colors, startangle=90, counterclock=False,
           wedgeprops=dict(width=0.36, edgecolor="white", linewidth=1.4))
    ax.text(0, 0.10, n(COST), ha="center", va="center", fontsize=10.5,
            fontweight="bold", color="#1f2c38")
    ax.text(0, -0.16, "сом", ha="center", va="center", fontsize=7.5, color="#5a6b7c")

    ax2 = fig.add_axes([0.54, 0.05, 0.45, 0.90])
    ax2.axis("off")
    for index, (value, label, color) in enumerate(zip(values, labels, colors)):
        y = 0.76 - index * 0.27
        ax2.add_patch(plt.Rectangle((0.02, y - 0.035), 0.05, 0.07,
                                    facecolor=color, edgecolor="none",
                                    transform=ax2.transAxes))
        ax2.text(0.10, y + 0.035, label, fontsize=8.4,
                 transform=ax2.transAxes, va="center")
        ax2.text(0.10, y - 0.045, f"{n(value)} сом · {n(value / COST * 100, 1)} %",
                 fontsize=7.8, color="#4a5a68", transform=ax2.transAxes, va="center")

    fig.savefig(IMG / "cost-structure.png", dpi=DPI, facecolor="white")
    plt.close(fig)
    print("saved cost-structure.png")


# --------------------------------------------------------------------------
# 3. Окупаемость по сценариям
# --------------------------------------------------------------------------

def payback():
    setup()
    fig = plt.figure(figsize=(WIDTH_IN, 7.0 / 2.54))
    ax = fig.add_axes([0.10, 0.17, 0.87, 0.60])

    names = [s[0] for s in SCENARIOS]
    years = [s[1] for s in SCENARIOS]
    months = MONTHS
    colors = ["#9dc3d9", "#3f7fb0", "#1f4e79"]

    bars = ax.bar(names, years, color=colors, width=0.46, zorder=2)
    for bar, year, month in zip(bars, years, months):
        ax.text(bar.get_x() + bar.get_width() / 2, bar.get_height() + 0.10,
                f"{n(year)} года\n{n(month, 1)} мес", va="bottom", ha="center",
                fontsize=8.4, color="#26323e", linespacing=1.25)

    ax.set_ylim(0, 4.6)
    ax.set_yticks([0, 1, 2, 3, 4])
    ax.set_ylabel("Срок окупаемости, лет", fontsize=8.4)
    ax.grid(axis="y", color="#dfe6ec", linewidth=0.7, zorder=0)
    ax.set_axisbelow(True)
    for side in ("top", "right"):
        ax.spines[side].set_visible(False)

    fig.savefig(IMG / "payback.png", dpi=DPI, facecolor="white")
    plt.close(fig)
    print("saved payback.png")


if __name__ == "__main__":
    print("font:", FONT)
    db_scheme()
    cost_structure()
    payback()
