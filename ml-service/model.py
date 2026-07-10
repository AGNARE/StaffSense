"""
================================================================================
ФАЙЛ:       model.py
ПУТЬ:       ml-service/model.py
НАЗНАЧЕНИЕ: Модель логистической регрессии для прогноза увольнения сотрудников
ЧТО ДЕЛАЕТ:
  - Обучает LogisticRegression (scikit-learn) на dataset_training.csv (320 записей)
  - Сохраняет обученную модель в model.pkl + scaler.pkl
  - Предсказывает риск увольнения для одного или нескольких сотрудников
  - Генерирует action plan на русском языке

АРХИТЕКТУРА:
  DatasetGenerator.java → dataset_training.csv → model.py (обучение)
                                                    ↓
  AttritionMlService.java → POST /api/v1/predict → model.predict() → riskScore

ФОРМУЛА ЛОГИСТИЧЕСКОЙ РЕГРЕССИИ:
    logit = W1*X1 + W2*X2 + ... + W7*X7 + intercept

    где W1..W7 — коэффициенты (model.coef_), которые МОДЕЛЬ НАХОДИТ САМА
    при обучении на CSV данных. Изначальные веса заданы в DatasetGenerator.java,
    но обученная модель может найти ДРУГИЕ веса, анализируя закономерности.

    probability = sigmoid(logit) = 1 / (1 + exp(-logit))
    riskScore = probability * 100

УРОВНИ РИСКА:
    LOW:    riskScore < 33   (зелёный — низкий риск)
    MEDIUM: 33-66            (жёлтый — повышенный риск)
    HIGH:   riskScore >= 66  (красный — критический риск)

7 ПРИЗНАКОВ (все нормализованы 0..1):
    salary_ratio       — зарплата / средняя по грейду
    workload_index     — часы / норма (1.0 = норма, >1.0 = перегрузка)
    kpi_norm           — последний KPI / 100
    tenure_months_norm — месяцы работы / 60
    satisfaction_norm  — средний LIKERT / 5
    missed_1on1_ratio  — пропущенные 1:1 / всего встреч
    overtime_norm      — часы переработок / (норма * 0.2)
================================================================================
"""

import os
import pickle
import logging
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.linear_model import LogisticRegression
from sklearn.preprocessing import StandardScaler

logger = logging.getLogger(__name__)

# Feature column names (must match CSV header and order)
FEATURE_COLUMNS = [
    "salary_ratio",
    "workload_index",
    "kpi_norm",
    "tenure_months_norm",
    "satisfaction_norm",
    "missed_1on1_ratio",
    "overtime_norm",
]

RISK_THRESHOLD_LOW = 33.0
RISK_THRESHOLD_HIGH = 66.0

MODEL_PATH = Path(__file__).parent / "model.pkl"
SCALER_PATH = Path(__file__).parent / "scaler.pkl"


class AttritionModel:
    """
    ═══════════════════════════════════════════════════════════════════════
    КЛАСС:      AttritionModel
    ФАЙЛ:       ml-service/model.py
    НАЗНАЧЕНИЕ: Модель логистической регрессии для прогноза увольнения
    ЧТО УМЕЕТ:
      - train()        — обучить модель на CSV-датасете
      - predict()      — предсказать риск для одного сотрудника
      - predict_batch()— предсказать риск для нескольких сотрудников
      - load()         — загрузить предобученную модель с диска
      - _save()        — сохранить модель и scaler на диск

    МОДЕЛЬ:
      Алгоритм: LogisticRegression (scikit-learn)
      Признаков: 7 (salary_ratio, workload_index, ..., overtime_norm)
      Классов: 2 (0 = stable, 1 = at_risk)
    ═══════════════════════════════════════════════════════════════════════
    """

    def __init__(self):
        # model — объект LogisticRegression из scikit-learn (None пока не обучена)
        self.model: LogisticRegression | None = None
        # scaler — StandardScaler для нормализации признаков
        self.scaler: StandardScaler | None = None
        # feature_names — список названий 7 признаков (порядок критичен!)
        self.feature_names = FEATURE_COLUMNS
        # _is_trained — флаг: True после успешного train() или load()
        self._is_trained = False

    @property
    def is_trained(self) -> bool:
        # Проверяет, готова ли модель к предсказаниям.
        # Возвращает True только если и модель, и флаг обучения есть.
        return self._is_trained and self.model is not None

    def train(self, csv_path: str | Path) -> dict:
        """
        ═══════════════════════════════════════════════════════════════════════
        ОБУЧЕНИЕ ЛОГИСТИЧЕСКОЙ РЕГРЕССИИ на CSV-датасете
        ═══════════════════════════════════════════════════════════════════════

        ЧТО ПРОИСХОДИТ:
        1. Загружаем CSV (dataset_training.csv) — 320 записей, 8 колонок
        2. Берём 7 признаков (X) и целевую переменную risk_score (y)
        3. Бинаризуем y: >= 33 → 1 (at risk), < 33 → 0 (stable)
        4. Стандартизируем признаки (StandardScaler) — вычитаем среднее,
           делим на стандартное отклонение. Это важно для логистической
           регрессии, чтобы все признаки были в одном масштабе.
        5. Обучаем LogisticRegression:
           - penalty="l2" — L2-регуляризация (Ridge), предотвращает
             переобучение, штрафуя большие веса
           - C=1.0 — обратная сила регуляризации (1 = стандарт)
           - solver="lbfgs" — оптимизатор (Limited-memory BFGS),
             хорошо работает для небольших датасетов
           - class_weight="balanced" — автоматически балансирует веса
             классов, если "at risk" меньше, чем "stable"
           - random_state=42 — фиксируем seed для воспроизводимости
        6. Модель НАХОДИТ веса (coef_) и смещение (intercept_) методом
           градиентного спуска, минимизируя логистическую функцию потерь
        7. Вычисляем accuracy — долю правильных предсказаний
        8. Сохраняем model.pkl и scaler.pkl на диск

        Args:
            csv_path: путь к CSV-файлу с колонками:
                      salary_ratio, workload_index, kpi_norm, tenure_months_norm,
                      satisfaction_norm, missed_1on1_ratio, overtime_norm,
                      risk_score, risk_level

        Returns:
            dict с метриками обучения (accuracy, coefficients, intercept, ...)
        """
        # ── ШАГ 1: Загрузка данных ──────────────────────────────────
        logger.info("Loading training data from %s", csv_path)
        df = pd.read_csv(csv_path)

        # ── ШАГ 2: Разделяем на признаки (X) и цель (y) ────────────
        # X — матрица 320x7 (320 сотрудников, 7 признаков)
        # y — вектор из 320 значений risk_score (0..100)
        X = df[self.feature_names].values
        y = df["risk_score"].values

        # ── ШАГ 3: Бинаризация целевой переменной ──────────────────
        # Логистическая регрессия — это КЛАССИФИКАТОР (да/нет).
        # Она не предсказывает числа, а относит к одному из двух классов.
        # Поэтому risk_score (0..100) нужно превратить в класс:
        #   >= 33 → 1 (at risk — в группе риска)
        #   < 33  → 0 (stable — стабильный сотрудник)
        # Порог 33 взят из определения MEDIUM risk.
        y_binary = (y >= RISK_THRESHOLD_LOW).astype(int)

        # ── ШАГ 4: Стандартизация признаков ─────────────────────────
        # StandardScaler вычитает среднее и делит на std каждого признака.
        # Зачем: LogisticRegression чувствительна к масштабу признаков.
        # Если salary_ratio от 0.5 до 1.5, а overtime_norm от 0 до 0.3,
        # то без стандартизации модель придаст больший вес признакам
        # с большим числовым диапазоном (просто из-за цифр, а не важности).
        # После StandardScaler каждый признак имеет mean=0, std=1.
        self.scaler = StandardScaler()
        X_scaled = self.scaler.fit_transform(X)

        # ── ШАГ 5: Обучение LogisticRegression ──────────────────────
        logger.info("Training LogisticRegression...")
        self.model = LogisticRegression(
            penalty="l2",
            C=1.0,
            solver="lbfgs",
            max_iter=1000,
            class_weight="balanced",
            random_state=42,
        )
        self.model.fit(X_scaled, y_binary)

        self._is_trained = True

        # ── ШАГ 6: Оценка качества ─────────────────────────────────
        y_pred_binary = self.model.predict(X_scaled)
        accuracy = float(np.mean(y_pred_binary == y_binary))

        # ── ШАГ 7: Формируем метрики ────────────────────────────────
        proba = self.model.predict_proba(X_scaled)[:, 1]
        pred_scores = proba * 100

        metrics = {
            "accuracy": round(accuracy, 4),
            "coefficients": {
                name: round(float(coef), 4)
                for name, coef in zip(self.feature_names, self.model.coef_[0])
            },
            "intercept": round(float(self.model.intercept_[0]), 4),
            "n_features": len(self.feature_names),
            "n_samples": len(df),
            "class_weights": {
                "low_risk": float(self.model.class_weight_[0]),
                "at_risk": float(self.model.class_weight_[1]),
            },
        }

        logger.info("Training complete: accuracy=%.4f", accuracy)
        logger.info("Coefficients: %s", metrics["coefficients"])
        logger.info("Intercept: %.4f", metrics["intercept"])

        # ── ШАГ 8: Сохраняем модель на диск ─────────────────────────
        self._save()

        return metrics

    def predict(self, features: dict) -> dict:
        """
        ═══════════════════════════════════════════════════════════════════════
        ПРЕДСКАЗАНИЕ РИСКА УВОЛЬНЕНИЯ для одного сотрудника
        ═══════════════════════════════════════════════════════════════════════

        ЧТО ПРОИСХОДИТ:
        1. Берём 7 признаков из словаря features
        2. Стандартизируем их с помощью сохранённого scaler.pkl
        3. Вычисляем вероятность через predict_proba:
           - model.predict_proba возвращает [P(0), P(1)]
           - P(1) = вероятность класса "at risk" (risk_score >= 33)
        4. riskScore = P(1) * 100 (переводим в проценты)
        5. Определяем уровень: >= 66 HIGH, >= 33 MEDIUM, < 33 LOW
        6. Вычисляем вклад каждого признака в logit:
           contribution[i] = coef[i] * scaled_value[i]
           Чем больше contribution (по модулю), тем сильнее признак
           повлиял на итоговый риск.
        7. Сортируем факторы по влиянию — показываем топ-5
        8. Генерируем action plan на русском языке

        ЭТАПЫ ВНУТРИ ФУНКЦИИ:
        ═══════════════════════════════════════════════════════════════
        ВХОД:   features = {"salary_ratio": 0.7, "workload_index": 1.5, ...}
                ↓
        ШАГ 1:  Упорядочиваем признаки в правильной последовательности
                ↓
        ШАГ 2:  Стандартизируем (вычитаем mean, делим на std)
                ↓
        ШАГ 3:  Вычисляем вероятность: predict_proba → P(1)
                ↓
        ШАГ 4:  riskScore = P(1) * 100
                ↓
        ШАГ 5:  Сравниваем с порогами → LOW / MEDIUM / HIGH
                ↓
        ШАГ 6:  Вычисляем вклад каждого признака в logit
                ↓
        ШАГ 7:  Формируем action plan
                ↓
        ВЫХОД:  {"riskScore": 78.3, "riskLevel": "HIGH", ...}
        ═══════════════════════════════════════════════════════════════

        ПРИМЕР ВЫЧИСЛЕНИЯ:
            Признаки:         salary=0.7, workload=1.5, kpi=0.5, ...
            scaled:           [-0.5, 1.2, -0.8, ...] (после StandardScaler)
            coef_:            [1.2, 1.0, 0.8, ...]
            logit = -1.5 + (-0.5)*1.2 + 1.2*1.0 + ... = 1.2
            P(1) = 1/(1+exp(-1.2)) = 0.77
            riskScore = 77 → HIGH

        Args:
            features: словарь с 7 признаками (ключи = FEATURE_COLUMNS)

        Returns:
            dict с riskScore, riskLevel, topRiskFactors и aiActionPlan
        """
        if not self.is_trained:
            raise RuntimeError("Model not trained. Call train() first.")

        # ── ШАГ 1: Упорядочиваем признаки ────────────────────────────
        # Из словаря features (который пришёл из JSON) собираем список
        # значений в правильном порядке: salary_ratio, workload_index, ...
        # Это важно, потому что model.coef_[i] соответствует i-му признаку
        # именно в этом порядке (FEATURE_COLUMNS).
        X = np.array([[features.get(col, 0.0) for col in self.feature_names]])

        # ── ШАГ 2: Стандартизируем признаки ──────────────────────────
        # Используем тот же StandardScaler, который был обучен на датасете.
        # Для каждого признака: scaled = (value - mean) / std
        # Это нужно, чтобы веса модели (coef_) работали корректно.
        X_scaled = self.scaler.transform(X)

        # ── ШАГ 3: Вычисляем вероятность увольнения ──────────────────
        # predict_proba возвращает массив [P(класс_0), P(класс_1)]
        #   класс_0 = stable (riskScore < 33) — сотрудник стабилен
        #   класс_1 = at_risk (riskScore >= 33) — в группе риска
        # Берём P(класс_1) — вероятность того, что сотрудник "в риске".
        proba = self.model.predict_proba(X_scaled)[0, 1]

        # ── ШАГ 4: Переводим в проценты ──────────────────────────────
        # riskScore от 0 до 100 — удобно для отображения в UI.
        risk_score = round(float(proba * 100), 1)

        # ── ШАГ 5: Определяем уровень риска ──────────────────────────
        # Те же пороги, что и в Java (DatasetGenerator.java, ModelInsightService):
        #   >= 66 → HIGH   (красный — критический риск увольнения)
        #   >= 33 → MEDIUM (жёлтый — повышенный риск)
        #   < 33  → LOW    (зелёный — низкий риск, сотрудник стабилен)
        if risk_score >= RISK_THRESHOLD_HIGH:
            risk_level = "HIGH"
        elif risk_score >= RISK_THRESHOLD_LOW:
            risk_level = "MEDIUM"
        else:
            risk_level = "LOW"

        # ── ШАГ 6: Вычисляем вклад каждого признака в logit ──────────
        # Логистическая регрессия: logit = sum(coef_i * scaled_i) + intercept
        # Каждое слагаемое coef_i * scaled_i — это вклад i-го признака в logit.
        # Чем больше абсолютное значение вклада, тем сильнее признак повлиял
        # на итоговое решение модели.
        logit_contributions = {}
        for i, col in enumerate(self.feature_names):
            raw_coef = self.model.coef_[0][i]     # вес W_i (коэффициент модели)
            scaled_val = X_scaled[0][i]            # scaled значение признака X_i
            logit_contributions[col] = raw_coef * scaled_val

        # ── ШАГ 7: Сортируем факторы по силе влияния ─────────────────
        # Сортируем от самого влиятельного к наименее влиятельному.
        # Берём топ-5 для отображения в UI.
        sorted_impacts = sorted(
            logit_contributions.items(),
            key=lambda x: abs(x[1]),  # сортируем по модулю вклада
            reverse=True,
        )

        top_risk_factors = []
        factor_labels = {
            "salary_ratio": "Уровень зарплаты",
            "workload_index": "Индекс нагрузки",
            "kpi_norm": "Показатели KPI",
            "tenure_months_norm": "Стаж в компании",
            "satisfaction_norm": "Удовлетворённость",
            "missed_1on1_ratio": "Пропуски встреч 1:1",
            "overtime_norm": "Переработки",
        }
        for col, impact in sorted_impacts[:5]:
            desc = _impact_description(col, impact)
            top_risk_factors.append({
                "factor": factor_labels.get(col, col),
                "impact": round(float(impact), 3),
                "description": desc,
            })

        # ── ШАГ 8: Генерируем план действий ──────────────────────────
        # Текст на русском языке, зависит от уровня риска и значений признаков.
        action_plan = _generate_action_plan(risk_score, features)

        return {
            "riskScore": risk_score,
            "riskLevel": risk_level,
            "topRiskFactors": top_risk_factors,
            "aiActionPlan": action_plan,
            "featuresSnapshot": {col: round(float(features.get(col, 0)), 4) for col in self.feature_names},
        }

    def predict_batch(self, features_list: list[dict]) -> list[dict]:
        # ═══════════════════════════════════════════════════════════════════════
        #  predict_batch — пакетное предсказание для нескольких сотрудников
        # ═══════════════════════════════════════════════════════════════════════
        #  Просто вызывает predict() для каждого элемента списка.
        #  Для массовых расчётов (например, для всей компании сразу).
        # ═══════════════════════════════════════════════════════════════════════
        return [self.predict(f) for f in features_list]

    def _save(self):
        # ═══════════════════════════════════════════════════════════════════════
        #  _save — сериализация модели и scaler на диск
        # ═══════════════════════════════════════════════════════════════════════
        #  Использует pickle — стандартную сериализацию Python.
        #  Сохраняет два файла:
        #    model.pkl  — обученный LogisticRegression (веса coef_, intercept)
        #    scaler.pkl — обученный StandardScaler (mean_, scale_ для каждого
        #                 признака — нужен, чтобы предсказания были корректны)
        #
        #  При следующем запуске load() восстановит модель из этих файлов,
        #  и повторное обучение не потребуется.
        # ═══════════════════════════════════════════════════════════════════════
        with open(MODEL_PATH, "wb") as f:
            pickle.dump(self.model, f)
        with open(SCALER_PATH, "wb") as f:
            pickle.dump(self.scaler, f)
        logger.info("Model saved to %s, scaler to %s", MODEL_PATH, SCALER_PATH)

    def load(self):
        # ═══════════════════════════════════════════════════════════════════════
        #  load — загрузка предобученной модели с диска
        # ═══════════════════════════════════════════════════════════════════════
        #  Пытается загрузить model.pkl и scaler.pkl из директории ml-service/.
        #  Если оба файла существуют — восстанавливает модель и scaler.
        #  Если хотя бы одного нет — возвращает False, и initialize() начнёт
        #  обучение с нуля из CSV.
        #
        #  model.pkl содержит:
        #    - coef_ (веса W1..W7)
        #    - intercept_ (смещение B0)
        #    - class_weight_ (веса классов)
        #
        #  scaler.pkl содержит:
        #    - mean_ (среднее каждого признака)
        #    - scale_ (стандартное отклонение каждого признака)
        # ═══════════════════════════════════════════════════════════════════════
        if MODEL_PATH.exists() and SCALER_PATH.exists():
            with open(MODEL_PATH, "rb") as f:
                self.model = pickle.load(f)
            with open(SCALER_PATH, "rb") as f:
                self.scaler = pickle.load(f)
            self._is_trained = True
            logger.info("Model loaded from disk")
            return True
        return False


def _impact_description(factor: str, impact: float) -> str:
    # ═══════════════════════════════════════════════════════════════════════
    #  Описание влияния признака — возвращает текст на русском языке.
    #  Вызывается для каждого из топ-5 факторов, влияющих на риск.
    #  impact — вклад признака в logit (положительный = увеличивает риск,
    #  отрицательный = уменьшает).
    # ═══════════════════════════════════════════════════════════════════════
    if factor == "salary_ratio":
        return "Сильный фактор. Низкая зарплата относительно рынка повышает риск."
    elif factor == "workload_index":
        return "Превышение нормативной нагрузки (>1.0) — маркер выгорания."
    elif factor == "kpi_norm":
        return "Низкие показатели эффективности сигнализируют о снижении мотивации."
    elif factor == "tenure_months_norm":
        return "Новые сотрудники имеют повышенный риск увольнения."
    elif factor == "satisfaction_norm":
        return "Низкая удовлетворённость — второй по значимости предиктор."
    elif factor == "missed_1on1_ratio":
        return "Пропуски 1:1 указывают на разрыв коммуникации."
    elif factor == "overtime_norm":
        return "Хронические переработки ведут к выгоранию."
    return "Влияет на итоговый риск."


def _generate_action_plan(score: float, features: dict) -> str:
    """
    ═══════════════════════════════════════════════════════════════════════
    Генерация плана действий на русском языке
    ═══════════════════════════════════════════════════════════════════════
    В зависимости от riskScore возвращает подробные рекомендации.
    Для HIGH — перечисляет конкретные критические факторы.
    Для MEDIUM — даёт список общих рекомендаций.
    Для LOW — сообщает, что всё в порядке.
    """
    if score >= 66:
        plan = (
            "ВЫСОКИЙ РИСК УВОЛЬНЕНИЯ\n\n"
            "Анализ выявил критические точки:\n"
        )
        high_factors = []
        if features.get("salary_ratio", 1) < 0.8:
            high_factors.append("- Зарплата значительно ниже рыночного уровня")
        if features.get("workload_index", 0) > 1.2:
            high_factors.append("- Критическая рабочая нагрузка")
        if features.get("satisfaction_norm", 1) < 0.4:
            high_factors.append("- Низкая удовлетворённость работой")
        if features.get("missed_1on1_ratio", 0) > 0.3:
            high_factors.append("- Регулярные пропуски встреч 1:1")
        if features.get("overtime_norm", 0) > 0.5:
            high_factors.append("- Хронические переработки")
        plan += "\n".join(high_factors) if high_factors else "- Требуется комплексный анализ\n"
        plan += "\n\nРекомендуется принять меры немедленно: пересмотреть нагрузку, провести 1:1,"
        plan += " рассмотреть корректировку зарплаты."
        return plan

    elif score >= 33:
        plan = (
            "СРЕДНИЙ РИСК УВОЛЬНЕНИЯ\n\n"
            "Рекомендуемые действия:\n"
            "- Провести 1:1 встречу для выявления проблем\n"
            "- Рассмотреть возможность повышения зарплаты или бонусов\n"
            "- Снизить нагрузку и нормализовать рабочие часы\n"
            "- Предложить обучение или карьерное развитие\n\n"
            "Рекомендуется принять меры в течение 2 недель."
        )
        return plan

    else:
        return (
            "НИЗКИЙ РИСК УВОЛЬНЕНИЯ\n\n"
            "Сотрудник находится в стабильном состоянии.\n"
            "Рекомендуется поддерживать текущий уровень взаимодействия,\n"
            "проводить регулярные 1:1 встречи для профилактики."
        )


# Singleton
_model_instance: AttritionModel | None = None


# ═══════════════════════════════════════════════════════════════════════
#  Singleton — глобальный экземпляр модели
# ═══════════════════════════════════════════════════════════════════════
# Используем паттерн Singleton, чтобы модель обучалась ОДИН РАЗ при
# старте сервиса и использовалась для всех последующих запросов.
# _model_instance хранится в памяти процесса FastAPI (uvicorn).
_model_instance: AttritionModel | None = None


def get_model() -> AttritionModel:
    # ═══════════════════════════════════════════════════════════════════════
    #  get_model — возвращает (или создаёт) singleton модели
    # ═══════════════════════════════════════════════════════════════════════
    #  Вызывается из app.py при каждом POST /api/v1/predict.
    #  Если модель ещё не создана — создаёт новый экземпляр AttritionModel.
    #  Обучение происходит в initialize(), а get_model() только возвращает
    #  готовый объект (обученный или нет — проверяет is_trained).
    # ═══════════════════════════════════════════════════════════════════════
    global _model_instance
    if _model_instance is None:
        _model_instance = AttritionModel()
    return _model_instance


def initialize(csv_path: str | Path | None = None) -> dict:
    # ═══════════════════════════════════════════════════════════════════════
    #  initialize — инициализация модели при старте сервиса
    # ═══════════════════════════════════════════════════════════════════════
    #  Вызывается один раз при запуске FastAPI (см. app.py startup).
    #  Что делает:
    #    1. Пытается загрузить model.pkl + scaler.pkl с диска
    #       (если модель уже была обучена и сохранена ранее)
    #    2. Если файлов нет — ищет dataset_training.csv
    #    3. Если CSV найден — ОБУЧАЕТ модель с нуля
    #    4. Если ничего нет — выбрасывает FileNotFoundError
    #
    #  При старте сервиса нужно ИЛИ model.pkl, ИЛИ dataset_training.csv.
    #  model.pkl и scaler.pkl создаются автоматически после первого обучения
    #  через train() или через POST /api/v1/retrain.
    # ═══════════════════════════════════════════════════════════════════════
    model = get_model()

    # ── Попытка 1: Загрузить предобученную модель с диска ──────────
    # Если ml-service уже запускался ранее, model.pkl и scaler.pkl
    # должны быть на месте. Это самый быстрый путь — не нужно обучать.
    if model.load():
        return {"status": "loaded", "message": "Model loaded from disk"}

    # ── Попытка 2: Обучить из CSV ──────────────────────────────────
    # Если предобученной модели нет — ищем dataset_training.csv.
    if csv_path is None:
        # По умолчанию: ../dataset_training.csv (на уровень выше, в корень проекта)
        csv_path = Path(__file__).parent.parent / "dataset_training.csv"

    if os.path.exists(csv_path):
        metrics = model.train(csv_path)
        return {"status": "trained", "metrics": metrics}

    # ── Ничего не найдено ──────────────────────────────────────────
    # Модель не обучена. FastAPI будет использовать fallback-эвристику
    # в app.py, пока кто-нибудь не вызовет POST /api/v1/retrain.
    raise FileNotFoundError(
        f"No training CSV found at {csv_path}. "
        f"Generate dataset_training.csv first by running the Spring Boot app."
    )
