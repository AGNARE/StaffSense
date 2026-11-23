# -*- coding: utf-8 -*-
"""Генератор рис. 2 — ROC-кривая модели StaffSense и потолок качества.

Скрипт ничего не берёт «на веру»: он заново обучает логистическую регрессию
на том же датасете и с тем же разбиением, что `StaffSenseML/train.py`
(seed=42, 560/140), затем считает ROC на независимой выборке 5 000 записей
(seed=2026) — именно на ней получены публикуемые 0,617 при потолке 0,623.

Запуск (интерпретатор ML-репозитория, там есть sklearn и matplotlib):

    C:\\Users\\Tima\\PythonProjects\\StaffSenseML\\.venv\\Scripts\\python.exe ^
        C:\\Users\\Argen\\Products\\StaffSense\\docs\\paper\\_gen_roc.py

Результат: docs/paper/img/roc-curve.png (200 dpi, 7 x 5,5 дюйма).
"""

import os
import sys

import matplotlib

matplotlib.use("Agg")

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import roc_auc_score, roc_curve
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import StandardScaler

ML_REPO = r"C:\Users\Tima\PythonProjects\StaffSenseML"
OUT = r"C:\Users\Argen\Products\StaffSense\docs\paper\img\roc-curve.png"

sys.path.insert(0, ML_REPO)
from dataset import FEATURES, generate  # noqa: E402

HEADLINE_AUC = 0.6171   # пересчитано ниже; сверяется assert-ом
CEILING_AUC = 0.6228    # истинная вероятность генератора на тех же 5 000

plt.rcParams.update({
    "font.family": "serif",
    "font.serif": ["Liberation Serif", "DejaVu Serif"],
    "font.size": 11,
    "axes.titlesize": 11.5,
    "axes.labelsize": 11,
    "legend.fontsize": 8.8,
    "axes.grid": True,
    "grid.alpha": 0.25,
    "grid.linewidth": 0.6,
})


def train_model():
    """То же обучение, что в train.py: scaler и модель видят только train."""
    frame = pd.read_csv(os.path.join(ML_REPO, "dataset_training.csv"))
    x = frame[FEATURES].to_numpy()
    y = frame["attrition"].to_numpy()
    idx = np.arange(len(frame))
    idx_train, idx_test = train_test_split(
        idx, test_size=0.2, stratify=y, random_state=42)
    scaler = StandardScaler().fit(x[idx_train])
    model = LogisticRegression(penalty="l2", C=1.0, solver="lbfgs", max_iter=1000,
                               class_weight="balanced", random_state=42)
    model.fit(scaler.transform(x[idx_train]), y[idx_train])
    return model, scaler


def ru(value, digits=3):
    """Число с десятичной запятой — как в тексте статьи."""
    return f"{value:.{digits}f}".replace(".", ",")


def main():
    model, scaler = train_model()

    fresh = generate(n=5000, seed=2026)
    xf = fresh[FEATURES].to_numpy()
    yf = fresh["attrition"].to_numpy()
    p_model = model.predict_proba(scaler.transform(xf))[:, 1]
    p_true = fresh["risk_score"].to_numpy() / 100.0

    auc = roc_auc_score(yf, p_model)
    ceiling = roc_auc_score(yf, p_true)
    fpr, tpr, _ = roc_curve(yf, p_model)

    print(f"n={len(yf)}, seed=2026, уходов {yf.mean():.1%}")
    print(f"ROC-AUC модели: {auc:.4f}   потолок: {ceiling:.4f}")
    assert abs(auc - HEADLINE_AUC) < 5e-4, f"модель дала {auc:.4f}"
    assert abs(ceiling - CEILING_AUC) < 5e-4, f"потолок дал {ceiling:.4f}"

    fig, ax = plt.subplots(figsize=(7.0, 5.5), dpi=200)

    ax.plot(fpr, tpr, color="#1f4e79", linewidth=1.9, solid_capstyle="round",
            label=f"Модель (логистическая регрессия): AUC = {ru(auc)}")
    ax.plot([0, 1], [0, 1], linestyle="--", color="0.45", linewidth=1.2,
            label="Случайное угадывание: AUC = 0,500")
    ax.axhline(ceiling, linestyle="-.", color="#c00000", linewidth=1.25,
               label=f"Потолок качества: AUC = {ru(ceiling)} "
                     "(истинная вероятность генератора)")

    ax.annotate(f"потолок {ru(ceiling)}", xy=(0.985, ceiling), xytext=(0.985, ceiling - 0.055),
                ha="right", va="center", fontsize=8.8, color="#c00000")

    ax.set_xlim(-0.01, 1.01)
    ax.set_ylim(-0.01, 1.02)
    ax.set_xticks(np.arange(0, 1.01, 0.2))
    ax.set_yticks(np.arange(0, 1.01, 0.2))
    ax.set_xlabel("Доля ложноположительных")
    ax.set_ylabel("Доля истинноположительных")
    ax.set_title(f"ROC-кривая: AUC = {ru(auc)}, потолок качества = {ru(ceiling)}")
    ax.legend(loc="lower right", framealpha=0.95, edgecolor="0.7")
    ax.text(0.035, 0.965, "независимая выборка: n = 5 000, seed = 2026",
            fontsize=8.4, color="0.35", ha="left", va="top")

    fig.tight_layout()
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    fig.savefig(OUT, dpi=200, bbox_inches="tight", facecolor="white")
    plt.close(fig)
    print("сохранено:", OUT)


if __name__ == "__main__":
    main()
