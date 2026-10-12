# STAFFSENSE_DISCOVERY_STATE — ТЕКУЩЕЕ СОСТОЯНИЕ ИССЛЕДОВАНИЯ

> **Примечание при публикации.** Отчёт приведён в исходном виде (фаза Discovery).
> Значение пароля БД отредактировано: `<пароль скрыт>`.
**Дата аудита:** 2026-10-09  
**Аудитор:** Senior Software Architect + QA + BA  
**Статус фазы:** PHASE 0 — DISCOVERY & AUDIT COMPLETE  

Этот документ фиксирует текущий статус изучения двух связанных систем проекта StaffSense на основании анализа исходного кода, файлов конфигурации, базы данных PostgreSQL 18 и технического задания (ТЗ / SRS версия 1.0).

---

## 1. ИЗУЧЕННЫЕ ОБЛАСТИ И ФАЙЛЫ

### A. Платформа StaffSense (Java/Spring Boot)
Изучена файловая структура и исходный код в каталоге `C:\Users\Argen\Products\StaffSense\demo\`:
*   **Конфигурация:** `pom.xml` (Maven, Spring Boot 3.2.5), `application.yml` (PostgreSQL 5433, Hibernate ddl-auto=update, дефолт-порт 9091), `application-test.yml` (профиль H2 in-memory).
*   **Безопасность:** `SecurityConfig.java` (CookieCsrfTokenRepository, rate limit, фильтр URL, BCrypt), `RateLimitingFilter.java` (Bucket4j + Caffeine на `/login`).
*   **Контроллеры (10 шт.):** `AdminController`, `AnalyticsController`, `AuthController`, `DashboardController`, `DatasetController`, `EmployeeController`, `OneOnOneController`, `ProjectController`, `RiskController`, `SurveyController`, `ProjectBoardController`, `TaskController`.
*   **Модели JPA (11 шт.):** `User`, `Project`, `Task`, `Survey`, `SurveyQuestion`, `SurveyResponse`, `SurveyAnswer`, `OneOnOneMeeting`, `KpiRecord`, `WorkloadRecord`, `AttritionPrediction` и enum-типы.
*   **Репозитории (11 шт.):** `UserRepository`, `ProjectRepository`, `TaskRepository` и другие.
*   **Сервисы:** `AttritionMlService` (мост к ML), `EmployeeService`, `OneOnOneService`, `SurveyService`, `SurveyTemplateService`, `TaskService`, `CsvExportService`, `ModelInsightService` и расчетные сервисы KPI/Workload.
*   **Инициализация:** `DataInitializer.java` (seed-данные для H2/PostgreSQL), `DatasetGenerator.java` (генератор CSV-выборок).

### B. Микросервисы ML (Python/FastAPI)
Обнаружено существование двух независимых реализаций ML-сервиса:
1.  **Локальный легаси-сервис (`StaffSense/ml-service/`):** FastAPI + scikit-learn LogisticRegression, StandardScaler, обучает модель на старте из CSV-файла, порт 8001. Полностью изучен (`app.py`, `model.py`, `run.py`, `requirements.txt`).
2.  **Удаленный репозиторий GitHub (`AGNARE/StaffSenseML`):** FastAPI 2.0.0, усовершенствованная Pydantic-валидация, раздельное обучение через `train.py`. Изучен удаленно через GitHub API (`main.py`, `services.py`, `schemas.py`, `train.py`, `model/weights.json`).
3.  **Локальная папка `StaffSenseML/`:** Пустая заготовка проекта PyCharm (содержит только дефолтный 16-строчный шаблон `main.py`).

---

## 2. ГЛАВНЫЕ ОТКРЫТИЯ И КРИТИЧЕСКИЕ ПРОБЛЕМЫ (EVIDENCE)

1.  **Критическая ошибка CSV-генератора (Сломанный датасет):**
    *   **Файл:** `DatasetGenerator.java:164`
    *   **Проблема:** Метод `String.format` выполняется на локали машины (`ru-RU`) без `Locale.US` и пишет вещественные числа через запятую-разделитель (например, `0,8855,1,0000`). Из-за этого CSV-файлы (`dataset_training.csv` и `dataset_test.csv`) структурно повреждены — строки содержат 17-18 полей вместо 9.
    *   **Влияние:** `pandas.read_csv` в Python-сервисе падает с `ParserError` при старте. Модель **никогда не обучается**, а файлы `model.pkl` и `scaler.pkl` отсутствуют. ML-сервис работает только на локальной эвристике (fallback).
2.  **Критическая инверсия признаков (Зарплата увеличивает риск):**
    *   **Файл:** `DatasetGenerator.java:130-137`
    *   **Проблема:** Знаки весов генератора для `salary_ratio`, `kpi_norm`, `tenure_months_norm`, `satisfaction_norm` заданы как положительные (`+1.2 * salaryRatio` и т.д.).
    *   **Влияние:** Рост зарплаты, эффективности, стажа и удовлетворенности в датасете *повышает* риск увольнения. Обученная на этих данных модель будет выдавать диаметрально противоположные результаты (считать самых ценных людей зоной риска).
3.  **Серьезные уязвимости безопасности (🔴 SECURITY GAP):**
    *   **TaskController & ProjectBoardController:** Отсутствуют `@PreAuthorize` и проверки ролей. Доступны EMPLOYEE. **CSRF-защита отключена** для всех путей `/api/**` (SecurityConfig:66). Любой вошедший может создавать задачи в любых проектах и менять статус любой задачи.
    - **OneOnOneController:** complete/cancel не имеют проверки автора/менеджера и не принимают `Authentication` — любой может завершить чужую встречу.
    - **AnalyticsController:** Все аналитические экраны (KPI, нагрузка, wellbeing всей компании, ML-модель с риском сотрудников) доступны EMPLOYEE по правилу `.requestMatchers("/analytics/**").authenticated()` в `SecurityConfig`. EMPLOYEE обходит скрытие рисков и видит все зарплаты/риски коллег.
    - **CsvExportService:** Метод `/employees/export/csv` выгружает зарплаты всех сотрудников компании любому PM. Уязвимость к **CSV-инъекциям** в free-text полях опросов (нет префиксации символов `=`, `+`, `-`, `@`).
4.  **Скрытые пароли в репозитории:**
    *   **Файл:** `application.yml:14`
    *   **Проблема:** Пароль БД PostgreSQL (`<пароль скрыт>`) зашит открытым текстом в yml-файле по умолчанию и продублирован в `docs/setup/configuration.md`.
5.  **Мертвый код бизнес-логики (Dead Code):**
    *   **Сервисы:** `KpiCalculationService` и `WorkloadCalculationService` **нигде не вызываются**. Рост KPI и индекса нагрузки рассчитывается только при первичном сидировании в `DataInitializer`. В рантайме KPI не пересчитывается.
    - `OneOnOneService` не имеет логики автопометки пропущенных встреч (`missed=true`), шедулер отсутствует.

---

## 3. СОСТОЯНИЕ ВЕРИФИКАЦИИ СБОРКИ (BUILD AUDIT)
*   **Компиляция и тесты:** Успешно выполнен запуск `.\mvnw.cmd test -Dspring.profiles.active=test`. Сборка завершилась статусом **BUILD SUCCESS** (47.03 s). Smoke-тест `contextLoads` пройден.
*   **Побочный эффект тестов:** При запуске тестов генерируются дефектные файлы `dataset_training.csv` и `dataset_test.csv` в папке `demo/` (перезаписывая старые), так как бин `DatasetGenerator` инициализируется через `@PostConstruct`.
*   **СУБД:** PostgreSQL 18 на порту 5433 запущена и работает.

---

## 4. ДАЛЬНЕЙШИЕ ДЕЙСТВИЯ (IMPLEMENTATION STEPS)
1.  Исправление `DatasetGenerator.java` (добавление `Locale.US` для разделителей точек).
2.  Исправление знаков признаков в генераторе (перенос в дефицитную форму согласно ТЗ).
3.  Ограничение доступа к `/analytics/**` в `SecurityConfig` (только для ADMIN, HR, PM, EXEC).
4.  Активация `@PreAuthorize` и включение CSRF на `/api/tasks/**`.
5.  Замена `ddl-auto=update` на полноценный `Flyway` (подготовка SQL-миграций).
6.  Интеграция пересчета KPI/нагрузки при изменениях задач/табелей.
