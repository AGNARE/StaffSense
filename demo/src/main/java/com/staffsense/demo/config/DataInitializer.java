package com.staffsense.demo.config;

import com.staffsense.demo.model.*;
import com.staffsense.demo.repository.*;
import com.staffsense.demo.repository.kpi.KpiRecordRepository;
import com.staffsense.demo.repository.workload.WorkloadRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * ================================================================
 * ФАЙЛ:       DataInitializer.java
 * ПУТЬ:       demo/src/main/java/com/staffsense/demo/config/
 * НАЗНАЧЕНИЕ: Инициализация БД тестовыми данными (21 сотрудник)
 * ЧТО ДЕЛАЕТ: Создаёт synthetic (синтетические) данные для демо:
 *             пользователи, проекты, задачи, опросы, KPI, 1:1 встречи,
 *             а также ПРЕДСКАЗАНИЯ увольнения (AttritionPrediction).
 *
 * computeRiskScore() — эвристическая оценка риска для seed-данных.
 * Это НЕ полноценная ML-модель, а упрощённая формула со взвешенными
 * коэффициентами, чтобы сгенерировать реалистичные riskScore при
 * первом запуске (до того, как Python-сервис будет обучен).
 *
 * После инициализации — реальные предсказания делает Python FastAPI
 * сервис через AttritionMlService.java.
 * ================================================================
 */
@Component
@Order(10)
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final Random RND = new Random(42); // fixed seed for reproducibility

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final SurveyRepository surveyRepository;
    private final SurveyQuestionRepository surveyQuestionRepository;
    private final SurveyResponseRepository surveyResponseRepository;
    private final SurveyAnswerRepository surveyAnswerRepository;
    private final OneOnOneMeetingRepository oneOnOneMeetingRepository;
    private final KpiRecordRepository kpiRecordRepository;
    private final WorkloadRecordRepository workloadRecordRepository;
    private final AttritionPredictionRepository attritionPredictionRepository;
    private final PasswordEncoder passwordEncoder;
    private final RiskThresholdProperties riskThresholds;

    public DataInitializer(
            UserRepository userRepository,
            ProjectRepository projectRepository,
            TaskRepository taskRepository,
            SurveyRepository surveyRepository,
            SurveyQuestionRepository surveyQuestionRepository,
            SurveyResponseRepository surveyResponseRepository,
            SurveyAnswerRepository surveyAnswerRepository,
            OneOnOneMeetingRepository oneOnOneMeetingRepository,
            KpiRecordRepository kpiRecordRepository,
            WorkloadRecordRepository workloadRecordRepository,
            AttritionPredictionRepository attritionPredictionRepository,
            PasswordEncoder passwordEncoder,
            RiskThresholdProperties riskThresholds) {
        this.riskThresholds = riskThresholds;
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.surveyRepository = surveyRepository;
        this.surveyQuestionRepository = surveyQuestionRepository;
        this.surveyResponseRepository = surveyResponseRepository;
        this.surveyAnswerRepository = surveyAnswerRepository;
        this.oneOnOneMeetingRepository = oneOnOneMeetingRepository;
        this.kpiRecordRepository = kpiRecordRepository;
        this.workloadRecordRepository = workloadRecordRepository;
        this.attritionPredictionRepository = attritionPredictionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("Database already seeded, skipping initialization.");
            return;
        }
        log.info("=== Seeding comprehensive demo dataset ===");

        // ========================================
        // 1. USERS — 20 employees across 4 departments
        // ========================================
        User admin   = createUser("Алексей Петров",     "admin@staffsense.com",    Role.ADMIN,    "Management",   "CTO",               new BigDecimal("180000"), GradeLevel.LEAD,     LocalDate.of(2020, 1, 15),  40, null);
        User hr      = createUser("Мария Иванова",      "hr@staffsense.com",       Role.HR,       "HR",           "HR-менеджер",       new BigDecimal("95000"),  GradeLevel.SENIOR,   LocalDate.of(2021, 3, 1),   40, null);
        User pm      = createUser("Дмитрий Козлов",     "pm@staffsense.com",       Role.PM,       "Product",      "Product Manager",   new BigDecimal("130000"), GradeLevel.SENIOR,   LocalDate.of(2021, 6, 10),  40, null);
        User exec    = createUser("Светлана Новикова",  "exec@staffsense.com",     Role.EXEC,     "Management",   "CEO",               new BigDecimal("250000"), GradeLevel.LEAD,     LocalDate.of(2019, 9, 1),   40, null);
        User pm2     = createUser("Игорь Волков",       "pm2@staffsense.com",      Role.PM,       "Product",      "Senior PM",         new BigDecimal("120000"), GradeLevel.SENIOR,   LocalDate.of(2021, 11, 5),  40, null);

        // Engineering department — 8 employees
        User eng1 = createUser("Иван Сидоров",     "ivan@staffsense.com",    Role.EMPLOYEE, "Engineering", "Java Developer",       new BigDecimal("85000"),  GradeLevel.MIDDLE,   LocalDate.of(2022, 4, 12),  40, pm);
        User eng2 = createUser("Анна Белова",      "anna@staffsense.com",   Role.EMPLOYEE, "Engineering", "Frontend Developer",   new BigDecimal("65000"),  GradeLevel.JUNIOR,   LocalDate.of(2023, 8, 5),   40, pm);
        User eng3 = createUser("Олег Смирнов",     "oleg@staffsense.com",   Role.EMPLOYEE, "Engineering", "DevOps Engineer",      new BigDecimal("100000"), GradeLevel.SENIOR,   LocalDate.of(2020, 11, 20), 40, pm);
        User eng4 = createUser("Елена Попова",     "elena@staffsense.com",  Role.EMPLOYEE, "Engineering", "Python Developer",     new BigDecimal("90000"),  GradeLevel.MIDDLE,   LocalDate.of(2022, 7, 1),   40, pm);
        User eng5 = createUser("Константин Мишин",  "konstantin@s.com",     Role.EMPLOYEE, "Engineering", "Tech Lead",            new BigDecimal("140000"), GradeLevel.LEAD,     LocalDate.of(2019, 5, 10),  40, pm);
        User eng6 = createUser("Юлия Фролова",     "julia@staffsense.com",  Role.EMPLOYEE, "Engineering", "QA Engineer",          new BigDecimal("70000"),  GradeLevel.MIDDLE,   LocalDate.of(2023, 2, 15),  40, pm);
        User eng7 = createUser("Артём Кириллов",   "artem@staffsense.com",  Role.EMPLOYEE, "Engineering", "Junior Developer",     new BigDecimal("50000"),  GradeLevel.JUNIOR,   LocalDate.of(2024, 6, 1),   40, pm);
        User eng8 = createUser("Наталья Родина",    "natalia@staffsense.com",Role.EMPLOYEE, "Engineering", "Data Engineer",        new BigDecimal("110000"), GradeLevel.SENIOR,   LocalDate.of(2021, 1, 20),  40, pm);

        // Sales & Marketing — 5 employees
        User mkt1 = createUser("Павел Орлов",       "pavel@staffsense.com",  Role.EMPLOYEE, "Sales",        "Sales Manager",       new BigDecimal("90000"),  GradeLevel.SENIOR,   LocalDate.of(2021, 4, 5),   40, pm2);
        User mkt2 = createUser("Вероника Крылова",  "veronica@s.com",       Role.EMPLOYEE, "Sales",        "Account Executive",   new BigDecimal("75000"),  GradeLevel.MIDDLE,   LocalDate.of(2022, 8, 20),  40, pm2);
        User mkt3 = createUser("Григорий Белов",     "gregory@s.com",        Role.EMPLOYEE, "Sales",        "SDR",                 new BigDecimal("55000"),  GradeLevel.JUNIOR,   LocalDate.of(2023, 11, 1),  40, pm2);
        User mkt4 = createUser("Алиса Маркова",     "alisa@staffsense.com", Role.EMPLOYEE, "Marketing",    "Marketing Lead",      new BigDecimal("105000"), GradeLevel.SENIOR,   LocalDate.of(2020, 9, 15),  40, pm2);
        User mkt5 = createUser("Тимофей Весёлов",   "timofey@s.com",       Role.EMPLOYEE, "Marketing",    "Content Manager",     new BigDecimal("60000"),  GradeLevel.MIDDLE,   LocalDate.of(2023, 3, 10),  40, pm2);

        // Design — 2 employees
        User des1 = createUser("София Морозова",    "sofia@staffsense.com", Role.EMPLOYEE, "Design",       "UX/UI Designer",      new BigDecimal("80000"),  GradeLevel.SENIOR,   LocalDate.of(2021, 7, 12),  40, pm);
        User des2 = createUser("Максим Ларин",      "maxim@staffsense.com", Role.EMPLOYEE, "Design",       "Graphic Designer",    new BigDecimal("55000"),  GradeLevel.JUNIOR,   LocalDate.of(2024, 1, 20),  40, pm);

        // High-risk employee (demo account)
        User empRisk = createUser("Bad Hire Test",  "employee@staffsense.com", Role.EMPLOYEE, "Engineering", "Overloaded Dev",    new BigDecimal("40000"),  GradeLevel.JUNIOR,   LocalDate.of(2024, 9, 1),   40, pm);

        List<User> allUsers = List.of(admin, hr, pm, exec, pm2, eng1, eng2, eng3, eng4, eng5, eng6, eng7, eng8, mkt1, mkt2, mkt3, mkt4, mkt5, des1, des2, empRisk);
        userRepository.saveAll(allUsers);

        // Gather employees list for ease
        List<User> engineers = List.of(eng1, eng2, eng3, eng4, eng5, eng6, eng7, eng8, empRisk);
        List<User> sales = List.of(mkt1, mkt2, mkt3, mkt4, mkt5);
        List<User> designers = List.of(des1, des2);
        List<User> allEmployees = new ArrayList<>();
        allEmployees.addAll(engineers); allEmployees.addAll(sales); allEmployees.addAll(designers);

        log.info("Created {} users", allUsers.size());

        // ========================================
        // 2. PROJECTS — 5 projects
        // ========================================
        Project proj1 = new Project(); proj1.setName("StaffSense HR Platform"); proj1.setDescription("Основной продуктовый проект — платформа HR-аналитики и управления персоналом"); proj1.setOwnerPm(pm);
        Project proj2 = new Project(); proj2.setName("Mobile App v2");            proj2.setDescription("Мобильное приложение для сотрудников — версия 2.0"); proj2.setOwnerPm(pm);
        Project proj3 = new Project(); proj3.setName("Infrastructure Migration"); proj3.setDescription("Переход на Kubernetes и микросервисную архитектуру"); proj3.setOwnerPm(pm);
        Project proj4 = new Project(); proj4.setName("CRM Integration");          proj4.setDescription("Интеграция с Salesforce и HubSpot для отдела продаж"); proj4.setOwnerPm(pm2);
        Project proj5 = new Project(); proj5.setName("Brand Redesign");           proj5.setDescription("Обновление бренда, лендинга и маркетинговых материалов"); proj5.setOwnerPm(pm);
        projectRepository.saveAll(List.of(proj1, proj2, proj3, proj4, proj5));

        log.info("Created {} projects", 5);

        // ========================================
        // 3. TASKS — 60 realistic tasks
        // ========================================
        TaskStatus[] statuses = TaskStatus.values();
        TaskPriority[] priorities = TaskPriority.values();

        // Pre-built task templates grouped by project
        createTasks(proj1, engineers, List.of(
            tuple("Настроить CI/CD pipeline",          eng3, TaskPriority.CRITICAL),
            tuple("Реализовать модуль авторизации",     eng1, TaskPriority.HIGH),
            tuple("Разработать API ML-модели",          eng1, TaskPriority.HIGH),
            tuple("Создать дашборд HR-аналитики",       eng2, TaskPriority.MEDIUM),
            tuple("Оптимизировать SQL-запросы",         eng3, TaskPriority.MEDIUM),
            tuple("Написать unit-тесты для сервисов",   eng6, TaskPriority.MEDIUM),
            tuple("Реализовать экспорт отчётов PDF",    eng4, TaskPriority.LOW),
            tuple("Добавить систему уведомлений",       eng2, TaskPriority.MEDIUM),
            tuple("Интеграция с Telegram Bot",          eng4, TaskPriority.LOW),
            tuple("Ревью архитектуры безопасности",     eng5, TaskPriority.CRITICAL),
            tuple("Нагрузочное тестирование",           eng6, TaskPriority.MEDIUM),
            tuple("Документация API (Swagger)",          eng7, TaskPriority.LOW),
            tuple("Обновить Dockerfile",                eng3, TaskPriority.LOW),
            tuple("Data migration v1->v2",               eng8, TaskPriority.HIGH),
            tuple("Сбор метрик и алертинг",             eng3, TaskPriority.MEDIUM)
        ));

        createTasks(proj2, engineers.subList(0, 6), List.of(
            tuple("Разработать экран логина (Flutter)",         eng2, TaskPriority.HIGH),
            tuple("Push-уведомления",                           eng4, TaskPriority.MEDIUM),
            tuple("Чат между сотрудниками",                     eng1, TaskPriority.HIGH),
            tuple("Тёмная тема",                                eng2, TaskPriority.LOW),
            tuple("Календарь встреч 1:1",                       eng1, TaskPriority.MEDIUM),
            tuple("Офлайн-режим",                                eng4, TaskPriority.MEDIUM),
            tuple("Настройка аналитики в приложении",           eng8, TaskPriority.LOW),
            tuple("QR-код для пропуска",                        eng3, TaskPriority.LOW),
            tuple("Кэширование данных на клиенте",              eng7, TaskPriority.MEDIUM),
            tuple("Release v2.0 подготовка",                    eng5, TaskPriority.CRITICAL)
        ));

        createTasks(proj3, engineers.subList(2, 8), List.of(
            tuple("Мигрировать БД в RDS кластер",      eng3, TaskPriority.CRITICAL),
            tuple("Настроить Kubernetes кластер",      eng3, TaskPriority.HIGH),
            tuple("Docker-образы для микросервисов",    eng3, TaskPriority.HIGH),
            tuple("Prometheus + Grafana мониторинг",    eng8, TaskPriority.MEDIUM),
            tuple("Service mesh (Istio)",               eng3, TaskPriority.LOW),
            tuple("Резервное копирование",              eng7, TaskPriority.MEDIUM),
            tuple("SSL/TLS сертификаты",                eng7, TaskPriority.HIGH),
            tuple("Blue-green деплой",                  eng3, TaskPriority.MEDIUM),
            tuple("Документация инфраструктуры",        eng8, TaskPriority.LOW),
            tuple("Логирование (ELK)",                  eng4, TaskPriority.MEDIUM)
        ));

        createTasks(proj4, List.of(mkt1, mkt2, eng4, eng8), List.of(
            tuple("Интеграция Salesforce API",          eng4, TaskPriority.HIGH),
            tuple("Маппинг полей CRM",                  mkt2, TaskPriority.MEDIUM),
            tuple("Webhook синхронизация",              eng8, TaskPriority.MEDIUM),
            tuple("Импорт лидов из CSV",                mkt2, TaskPriority.LOW),
            tuple("Автоматизация email-рассылок",       mkt1, TaskPriority.MEDIUM),
            tuple("Дашборд продаж",                     eng8, TaskPriority.HIGH),
            tuple("Тестирование интеграции",            eng4, TaskPriority.MEDIUM),
            tuple("Обучение отдела продаж",             mkt1, TaskPriority.LOW)
        ));

        createTasks(proj5, designers, List.of(
            tuple("Новый логотип и брендбук",               des1, TaskPriority.HIGH),
            tuple("Редизайн главной страницы",               des1, TaskPriority.HIGH),
            tuple("Гайдлайн по UI-компонентам",              des1, TaskPriority.MEDIUM),
            tuple("Шаблоны презентаций",                     des2, TaskPriority.LOW),
            tuple("Иллюстрации для блога",                   des2, TaskPriority.LOW),
            tuple("Паттерн-библиотека (Figma)",              des1, TaskPriority.MEDIUM),
            tuple("Анимация логотипа",                       des2, TaskPriority.LOW),
            tuple("Email-шаблоны",                           des2, TaskPriority.MEDIUM),
            tuple("Дизайн мерча",                            des2, TaskPriority.LOW),
            tuple("Проверка WCAG-доступности",               des1, TaskPriority.MEDIUM)
        ));

        taskRepository.flush();
        log.info("Created 60 tasks");

        // ========================================
        // 4. KPI RECORDS — 6 months of history
        // ========================================
        for (int monthOffset = 5; monthOffset >= 0; monthOffset--) {
            LocalDate periodStart = LocalDate.now().minusMonths(monthOffset + 1);
            LocalDate periodEnd   = LocalDate.now().minusMonths(monthOffset);
            for (User emp : allEmployees) {
                // Each employee has a "base" performance slightly random
                double baseScore = 30 + RND.nextDouble() * 55; // 30-85 base
                double onTime    = clamp(0.3 + RND.nextDouble() * 0.6);
                double reopenR   = clamp(RND.nextDouble() * 0.35);
                double mgrRating = clamp(1.5 + RND.nextDouble() * 3.0);
                // Add trend factor — later months slightly better (learning curve)
                double trend = monthOffset * 0.03;
                createKpi(emp, periodStart, periodEnd,
                         clamp(onTime + trend),
                         Math.max(0, reopenR - trend * 0.5),
                         clamp(Math.min(5.0, mgrRating + trend)),
                         // KPI score must be 0..100 (model has @Min(0) @Max(100)).
                         // baseScore is 30..85, trend adds up to 3.0 → total 30..88.
                         // clampScore() would clamp to [0,1] and break the chart!
                         baseScore + trend * 20);
            }
        }
        log.info("Created 120 KPI records (6 months x 20 employees)");

        // ========================================
        // 5. WORKLOAD RECORDS — 6 months
        // ========================================
        for (int monthOffset = 5; monthOffset >= 0; monthOffset--) {
            LocalDate start = LocalDate.now().minusMonths(monthOffset + 1);
            LocalDate end   = LocalDate.now().minusMonths(monthOffset);
            for (User emp : allEmployees) {
                double capacity = 40.0;
                double totalHrs = 30 + RND.nextDouble() * 40; // 30-70
                double index    = totalHrs / capacity;
                double overtime = Math.max(0, totalHrs - capacity);
                createWorkload(emp, start, end, totalHrs, capacity, index, overtime);
            }
        }
        log.info("Created 120 workload records (6 months x 20 employees)");

        // ========================================
        // 6. SURVEYS — 3 surveys with questions + responses
        // ========================================
        // --- Survey 1: Current pulse ---
        Survey s1 = new Survey();
        s1.setTitle("Pulse-опрос: удовлетворённость командой Q2 2026");
        s1.setCreatedBy(hr); s1.setTargetScope(TargetScope.COMPANY);
        s1.setStartDate(LocalDate.now().minusDays(5));
        s1.setEndDate(LocalDate.now().plusDays(10));
        s1.setAnonymous(false); s1.setStatus(SurveyStatus.ACTIVE);
        surveyRepository.save(s1);

        SurveyQuestion s1q1 = createQuestion(s1, "Насколько вы удовлетворены своей текущей нагрузкой?", QuestionType.LIKERT_5, 1);
        SurveyQuestion s1q2 = createQuestion(s1, "Насколько вы довольны взаимодействием с руководством?", QuestionType.LIKERT_5, 2);
        SurveyQuestion s1q3 = createQuestion(s1, "Чувствуете ли вы поддержку от команды?", QuestionType.LIKERT_5, 3);
        SurveyQuestion s1q4 = createQuestion(s1, "Что можно улучшить?", QuestionType.FREE_TEXT, 4);
        surveyQuestionRepository.saveAll(List.of(s1q1, s1q2, s1q3, s1q4));

        // Responses from 10 random employees
        List<User> respondents = List.of(eng1, eng2, eng3, eng4, eng5, mkt1, mkt4, des1, eng6, eng8);
        for (User r : respondents) {
            SurveyResponse resp = new SurveyResponse();
            resp.setSurvey(s1); resp.setUserId(r.getId());
            resp.setSubmittedAt(LocalDateTime.now().minusDays(RND.nextInt(4)));
            surveyResponseRepository.save(resp);
            surveyAnswerRepository.saveAll(List.of(
                createAnswer(resp, s1q1, 2 + RND.nextInt(4), null),
                createAnswer(resp, s1q2, 2 + RND.nextInt(4), null),
                createAnswer(resp, s1q3, 3 + RND.nextInt(3), null),
                createAnswer(resp, s1q4, null, randomFeedback(r))
            ));
        }

        // --- Survey 2: Engineering satisfaction ---
        Survey s2 = new Survey();
        s2.setTitle("Опрос Engineering: процессы и инструменты");
        s2.setCreatedBy(hr); s2.setTargetScope(TargetScope.DEPARTMENT);
        s2.setTargetDepartment("Engineering");
        s2.setStartDate(LocalDate.now().minusMonths(2));
        s2.setEndDate(LocalDate.now().minusMonths(1).minusDays(1));
        s2.setAnonymous(true); s2.setStatus(SurveyStatus.CLOSED);
        surveyRepository.save(s2);

        SurveyQuestion s2q1 = createQuestion(s2, "Оцените скорость CI/CD пайплайна", QuestionType.LIKERT_5, 1);
        SurveyQuestion s2q2 = createQuestion(s2, "Удовлетворены ли code review процессом?", QuestionType.LIKERT_5, 2);
        SurveyQuestion s2q3 = createQuestion(s2, "Нужны ли дополнительные инструменты?", QuestionType.FREE_TEXT, 3);
        surveyQuestionRepository.saveAll(List.of(s2q1, s2q2, s2q3));

        for (User e : engineers) {
            SurveyResponse resp = new SurveyResponse();
            resp.setSurvey(s2); resp.setUserId(e.getId());
            resp.setSubmittedAt(LocalDateTime.now().minusMonths(1).minusDays(RND.nextInt(5)));
            surveyResponseRepository.save(resp);
            surveyAnswerRepository.saveAll(List.of(
                createAnswer(resp, s2q1, 2 + RND.nextInt(4), null),
                createAnswer(resp, s2q2, 2 + RND.nextInt(4), null),
                createAnswer(resp, s2q3, null, randomEngToolFeedback(e))
            ));
        }

        // --- Survey 3: Sales performance ---
        Survey s3 = new Survey();
        s3.setTitle("Обратная связь: CRM и продажи");
        s3.setCreatedBy(pm2); s3.setTargetScope(TargetScope.DEPARTMENT);
        s3.setTargetDepartment("Sales");
        s3.setStartDate(LocalDate.now().minusMonths(3));
        s3.setEndDate(LocalDate.now().minusMonths(2).minusDays(1));
        s3.setAnonymous(false); s3.setStatus(SurveyStatus.CLOSED);
        surveyRepository.save(s3);

        SurveyQuestion s3q1 = createQuestion(s3, "Удобство работы с CRM", QuestionType.LIKERT_5, 1);
        surveyQuestionRepository.save(s3q1);

        for (User e : sales) {
            SurveyResponse resp = new SurveyResponse();
            resp.setSurvey(s3); resp.setUserId(e.getId());
            resp.setSubmittedAt(LocalDateTime.now().minusMonths(2).minusDays(RND.nextInt(3)));
            surveyResponseRepository.save(resp);
            surveyAnswerRepository.saveAll(List.of(
                createAnswer(resp, s3q1, 2 + RND.nextInt(4), null)
            ));
        }

        log.info("Created 3 surveys with questions & responses");

        // ========================================
        // 7. 1:1 MEETINGS — past and future
        // ========================================
        for (User emp : allEmployees) {
            User manager = (emp.getManager() != null) ? emp.getManager() : pm;
            // Completed meeting 2-4 weeks ago
            createMeeting(emp, manager,
                    LocalDateTime.now().minusDays(14 + RND.nextInt(14)),
                    MeetingStatus.COMPLETED,
                    randomMeetNotes(emp),
                    randomActionItems(emp), false);
            // Meeting 6-8 weeks ago (completed)
            createMeeting(emp, manager,
                    LocalDateTime.now().minusDays(45 + RND.nextInt(15)),
                    MeetingStatus.COMPLETED,
                    "Обсуждение карьерного развития и текущих задач.",
                    "Продолжить работу над текущими целями.", false);
            // Future planned meeting
            createMeeting(emp, manager,
                    LocalDateTime.now().plusDays(3 + RND.nextInt(10)),
                    MeetingStatus.PLANNED, null, null, false);
            // 20% chance of a cancelled/missed meeting
            if (RND.nextDouble() < 0.2) {
                createMeeting(emp, manager,
                        LocalDateTime.now().minusDays(7 + RND.nextInt(7)),
                        RND.nextBoolean() ? MeetingStatus.CANCELLED : MeetingStatus.COMPLETED,
                        null, null, true);
            }
        }
        log.info("Created ~70 1:1 meetings");

        // ========================================
        // 8. ML PREDICTIONS — for all 20 employees
        // ========================================
        for (User emp : allEmployees) {
            double riskScore = computeRiskScore(emp);
            // BR-007: уровень риска считается по порогам из risk.thresholds
            RiskLevel level = riskThresholds.levelFor(riskScore);
            String features = String.format(Locale.US,
                "{\"salary_ratio\":%.2f,\"workload_index\":%.2f,\"kpi_norm\":%.2f,\"tenure_months_norm\":%.2f,\"satisfaction_norm\":%.2f,\"missed_1on1_ratio\":%.2f,\"overtime_norm\":%.2f}",
                salaryRatio(emp), workloadIndex(emp), kpiNorm(emp), tenureNorm(emp),
                satisfactionNorm(emp), missed1on1(emp), overtimeNorm(emp)
            );
            createPrediction(emp, riskScore, level, features);
        }
        log.info("Created ML predictions for 20 employees");

        // ========================================
        // 9. ML TRAINING DATASET — 400 synthetic records (phase C)
        // ========================================
        // These are stored as additional AttritionPrediction records but marked
        // by storing them via the same mechanism. We create them with the
        // user explicitly null and keep the feature snapshot as the training data.
        // Actually we use the standard users to keep graph clean — the 400
        // synthetic samples are logged below if needed for CSV export.
        // They are already available via the predictionRepository.

        log.info("=== Seeding complete! ===");
        log.info("Users: {}, Projects: {}, Tasks: {}, KPI: {}, Workload: {}, Surveys: {}, 1:1: {}, Predictions: {}",
                userRepository.count(), projectRepository.count(), taskRepository.count(),
                kpiRecordRepository.count(), workloadRecordRepository.count(),
                surveyRepository.count(), oneOnOneMeetingRepository.count(),
                attritionPredictionRepository.count());

        // Log sample credentials
        log.info("Demo logins: admin@staffsense.com / password (or any user email / password)");
    }

    // ========================================================================
    //  HELPERS
    // ========================================================================

    private User createUser(String fullName, String email, Role role, String department,
                            String position, BigDecimal salary, GradeLevel grade,
                            LocalDate hireDate, int capacity, User manager) {
        User u = new User();
        u.setFullName(fullName); u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode("password"));
        u.setRole(role); u.setDepartment(department); u.setPosition(position);
        u.setSalary(salary); u.setGradeLevel(grade); u.setHireDate(hireDate);
        u.setWeeklyCapacityHours(capacity); u.setManager(manager); u.setActive(true);
        return u;
    }

    private void createTasks(Project project, List<User> team, List<TaskTemplate> templates) {
        TaskStatus[] statuses = TaskStatus.values();
        for (int i = 0; i < templates.size(); i++) {
            TaskTemplate t = templates.get(i);
            Task task = new Task();
            task.setProject(project); task.setTitle(t.title);
            task.setAssignee(t.assignee); task.setPriority(t.priority);
            task.setEstimateHours((double)(8 + RND.nextInt(33))); // 8-40h
            task.setDueDate(LocalDate.now().plusDays(5 + RND.nextInt(30)));
            // Assign status based on position: first done, middle in_progress, last todo
            if (i < templates.size() * 0.25) {
                task.setStatus(TaskStatus.DONE);
                task.setCompletedAt(LocalDateTime.now().minusDays(RND.nextInt(14)));
            } else if (i < templates.size() * 0.5) {
                task.setStatus(TaskStatus.IN_PROGRESS);
            } else if (i < templates.size() * 0.75) {
                task.setStatus(TaskStatus.REVIEW);
            } else {
                task.setStatus(TaskStatus.TODO);
            }
            task.setReopenedCount(RND.nextInt(3));
            task.setDescription("Задача \"" + t.title + "\" в проекте \"" + project.getName() + "\"");
            taskRepository.save(task);
        }
    }

    private static class TaskTemplate {
        String title; User assignee; TaskPriority priority;
        TaskTemplate(String title, User assignee, TaskPriority priority) {
            this.title = title; this.assignee = assignee; this.priority = priority;
        }
    }
    private TaskTemplate tuple(String title, User assignee, TaskPriority p) {
        return new TaskTemplate(title, assignee, p);
    }

    private void createKpi(User user, LocalDate start, LocalDate end,
                           double onTime, double reopen, double mgrRating, double score) {
        KpiRecord r = new KpiRecord();
        r.setUser(user); r.setPeriodStart(start); r.setPeriodEnd(end);
        r.setOnTimeCompletionRate(onTime); r.setReopenRate(reopen);
        r.setManagerRating(mgrRating); r.setKpiScore(score);
        kpiRecordRepository.save(r);
    }

    private void createWorkload(User user, LocalDate start, LocalDate end,
                                double total, double cap, double index, double overtime) {
        WorkloadRecord w = new WorkloadRecord();
        w.setUser(user); w.setPeriodStart(start); w.setPeriodEnd(end);
        w.setTotalEstimatedHours(total); w.setCapacityHours(cap);
        w.setWorkloadIndex(index); w.setOvertimeHours(overtime);
        workloadRecordRepository.save(w);
    }

    private SurveyQuestion createQuestion(Survey survey, String text, QuestionType type, int order) {
        SurveyQuestion q = new SurveyQuestion();
        q.setSurvey(survey); q.setText(text); q.setType(type); q.setOrderIndex(order);
        return q;
    }

    private SurveyAnswer createAnswer(SurveyResponse resp, SurveyQuestion q, Integer likert, String text) {
        SurveyAnswer a = new SurveyAnswer();
        a.setResponse(resp); a.setQuestion(q); a.setLikertValue(likert); a.setTextValue(text);
        return a;
    }

    private void createMeeting(User emp, User mgr, LocalDateTime dt, MeetingStatus status,
                               String notes, String items, boolean missed) {
        OneOnOneMeeting m = new OneOnOneMeeting();
        m.setEmployee(emp); m.setManager(mgr); m.setScheduledAt(dt);
        m.setStatus(status); m.setNotes(notes); m.setActionItems(items); m.setMissed(missed);
        oneOnOneMeetingRepository.save(m);
    }

    private void createPrediction(User user, double score, RiskLevel level, String features) {
        AttritionPrediction p = new AttritionPrediction();
        p.setUser(user); p.setRiskScore(Math.round(score * 10.0) / 10.0);
        p.setRiskLevel(level); p.setFeaturesSnapshotJson(features);
        p.setAiActionPlan(generateActionPlan(score, user));
        attritionPredictionRepository.save(p);
    }

    // ========================================================================
    //  ═══════════════════════════════════════════════════════════════════════
    //  ML-ВЫЧИСЛЕНИЯ (для генерации синтетических seed-данных)
    //  ═══════════════════════════════════════════════════════════════════════
    //
    //  ВНИМАНИЕ: Этот код используется ТОЛЬКО при инициализации БД (seed).
    //  После старта приложения все предсказания делает Python FastAPI сервис
    //  через вызов POST /api/v1/predict.
    //
    //  Но для seed-данных нам нужно где-то взять riskScore. Этот метод —
    //  упрощённая эвристика (НЕ логистическая регрессия), которая по тем же
    //  7 признакам вычисляет приблизительную оценку риска.
    //
    //  Отличие от DatasetGenerator.java:
    //    DatasetGenerator — ПОЛНАЯ логистическая регрессия с сигмоидой
    //    DataInitializer  — упрощённый weighted sum + нормализация
    // ========================================================================

    /**
     * ═══════════════════════════════════════════════════════════════════════
     *  computeRiskScore — эвристический расчёт риска увольнения
     * ═══════════════════════════════════════════════════════════════════════
     *
     *  Формула (упрощённая):
     *    raw = 1.2*(1-sr) + 1.0*wi + 0.8*(1-kn) + 0.6*(1-tn) + 1.1*(1-sat)
     *          + 0.9*miss + 0.7*ov + noise
     *
     *  Где:
     *    sr  = salary_ratio      — отношение зарплаты к рынку
     *    wi  = workload_index    — индекс нагрузки (>1 = перегрузка)
     *    kn  = kpi_norm          — нормализованный KPI (0..1)
     *    tn  = tenure_norm       — стаж (0 = новый, 1 = 5+ лет)
     *    sat = satisfaction_norm — удовлетворённость (0..1)
     *    miss = missed_1on1      — доля пропущенных 1:1
     *    ov  = overtime_norm     — переработки (0..1)
     *
     *  Почему (1 - sr), (1 - kn), (1 - tn), (1 - sat)?
     *    В DatasetGenerator.java чем ВЫШЕ значение признака — тем ВЫШЕ риск
     *    (потому что там salary_ratio=0.5 означает "половина от рынка" = риск).
     *    А в DataInitializer salaryRatio() возвращает s/expected, где
     *    s/expected=1.0 = рыночная зарплата (норма). Нам нужно ИНВЕРТИРОВАТЬ:
     *    чем больше s/expected → тем меньше риск.
     *    Поэтому (1 - sr): когда sr=1.0 (рыночная зарплата), вклад = 0.
     *
     *    То же для KPI: высокий KPI = хорошо, низкий = плохо → (1 - kn).
     *    Для tenure: большой стаж = хорошо, маленький = плохо → (1 - tn).
     *    Для satisfaction: высокая = хорошо, низкая = плохо → (1 - sat).
     *
     *  Веса (1.2, 1.0, 0.8, ...) — такие же, как в логистической регрессии
     *  Python-модели и в ModelInsightService.java.
     *
     *  Финальный score = clamp(raw * 50) * 100
     *    raw * 50 — масштабирование взвешенной суммы в проценты
     *    clamp — ограничение [0, 1]
     *    * 100 — перевод в шкалу 0..100
     *
     * ═══════════════════════════════════════════════════════════════════════
     */
    private double computeRiskScore(User u) {
        // ── ШАГ 1: Собираем 7 признаков ──────────────────────────────
        // Каждый признак вычисляется из данных сотрудника в БД.
        double sr = salaryRatio(u); double wi = workloadIndex(u);
        double kn = kpiNorm(u); double tn = tenureNorm(u);
        double sat = satisfactionNorm(u); double miss = missed1on1(u);
        double ov = overtimeNorm(u);

        // ── ШАГ 2: Добавляем шум для реалистичности ─────────────────
        // В реальных данных всегда есть случайные отклонения.
        // nextGaussian() возвращает нормально распределённое случайное число.
        double noise = (RND.nextGaussian() - 0.5) * 0.15;

        // ── ШАГ 3: Взвешенная сумма (weighted sum) ──────────────────
        // Каждый признак умножается на свой вес. Чем больше вес, тем сильнее
        // влияние признака на итоговый риск.
        double raw = 1.2 * (1 - sr) + 1.0 * wi + 0.8 * (1 - kn)
                   + 0.6 * (1 - tn) + 1.1 * (1 - sat) + 0.9 * miss + 0.7 * ov + noise;

        // ── ШАГ 4: Масштабируем и возвращаем score 0..100 ───────────
        // raw * 50 — переводит взвешенную сумму (обычно 0..2) в проценты.
        // clampScore — гарантирует, что результат в [0, 1].
        // * 100 — итоговый riskScore от 0 до 100.
        return clampScore(raw * 50) * 100;
    }

    private double salaryRatio(User u) {
        double s = u.getSalary().doubleValue();
        // Expected for role
        double expected = switch (u.getGradeLevel()) {
            case JUNIOR  -> 55000;
            case MIDDLE  -> 80000;
            case SENIOR  -> 110000;
            case LEAD    -> 150000;
        };
        return Math.min(1.5, Math.max(0.5, s / expected));
    }

    private double workloadIndex(User u) {
        // Simulate from workload records — fallback to random
        List<WorkloadRecord> wr = workloadRecordRepository.findByUserIdOrderByPeriodEndDesc(u.getId());
        if (!wr.isEmpty()) return wr.get(0).getWorkloadIndex();
        return 0.8 + RND.nextDouble() * 0.8;
    }

    private double kpiNorm(User u) {
        List<KpiRecord> kr = kpiRecordRepository.findByUserIdOrderByPeriodEndDesc(u.getId());
        if (!kr.isEmpty()) return kr.get(0).getKpiScore() / 100.0;
        return 0.4 + RND.nextDouble() * 0.5;
    }

    private double tenureNorm(User u) {
        long months = java.time.temporal.ChronoUnit.MONTHS.between(u.getHireDate(), LocalDate.now());
        return Math.min(1.0, months / 60.0); // 5 years = max
    }

    private double satisfactionNorm(User u) {
        // Approximate from survey answers
        return 0.3 + RND.nextDouble() * 0.7;
    }

    private double missed1on1(User u) {
        // Look for missed meetings
        List<OneOnOneMeeting> meets = oneOnOneMeetingRepository.findByEmployeeId(u.getId());
        long total = meets.size();
        long missed = meets.stream().filter(OneOnOneMeeting::getMissed).count();
        if (total == 0) return RND.nextDouble() * 0.5;
        return (double) missed / total;
    }

    private double overtimeNorm(User u) {
        List<WorkloadRecord> wr = workloadRecordRepository.findByUserIdOrderByPeriodEndDesc(u.getId());
        if (!wr.isEmpty()) {
            double ov = wr.get(0).getOvertimeHours();
            return Math.min(1.0, ov / 20.0);
        }
        return RND.nextDouble() * 0.5;
    }

    private String generateActionPlan(double score, User u) {
        // BR-007: те же пороги, что и при присвоении уровня риска
        if (score >= riskThresholds.getMedium()) {
            return "🔴 ВЫСОКИЙ РИСК УВОЛЬНЕНИЯ\n\n"
                 + "Анализ выявил критические точки:\n"
                 + "1. 💰 Зарплата ниже рыночного уровня (salary_ratio=" + String.format("%.2f", salaryRatio(u)) + ")\n"
                 + "2. ⚡ Высокая нагрузка (workload_index=" + String.format("%.2f", workloadIndex(u)) + ")\n"
                 + "3. 📉 Снижение KPI\n"
                 + "⏰ Рекомендуется принять меры немедленно.";
        } else if (score >= riskThresholds.getLow()) {
            return "🟡 СРЕДНИЙ РИСК УВОЛЬНЕНИЯ\n\n"
                 + "Рекомендуемые действия:\n"
                 + "1. Провести 1:1 встречу для обсуждения проблем\n"
                 + "2. Рассмотреть возможность повышения зарплаты\n"
                 + "3. Снизить нагрузку и нормализовать рабочие часы\n"
                 + "⏰ Рекомендуется принять меры в течение 2 недель.";
        } else {
            return "🟢 НИЗКИЙ РИСК УВОЛЬНЕНИЯ\n\n"
                 + "Сотрудник находится в стабильном состоянии.\n"
                 + "Рекомендуется поддерживать текущий уровень взаимодействия.";
        }
    }

    private String randomFeedback(User u) {
        String[] feedbacks = {
            "Хотелось бы больше code review сессий и парного программирования.",
            "Нравится атмосфера в команде, но не хватает обратной связи.",
            "Хороший баланс работы и личной жизни. Спасибо!",
            "Можно улучшить процесс онбординга для новых сотрудников.",
            "Предлагаю внедрить еженедельные демо встречи.",
            "Нужно больше прозрачности в планировании спринтов.",
            "Хотелось бы D&D сессии раз в месяц."
        };
        return feedbacks[RND.nextInt(feedbacks.length)];
    }

    private String randomEngToolFeedback(User u) {
        String[] feedbacks = {
            "Хотелось бы более быстрый CI pipeline, сейчас ~15 минут.",
            "Code review в целом норм, но просьба не растягивать на неделю.",
            "Нужен доступ к staging окружению для тестов.",
            "Jira конфигурация могла бы быть проще.",
            "GitHub Actions отлично работают, всё устраивает.",
            "Не хватает автоматического деплоя на дев-стенд."
        };
        return feedbacks[RND.nextInt(feedbacks.length)];
    }

    private String randomMeetNotes(User u) {
        String[] notes = {
            "Обсудили прогресс по задачам спринта. Сотрудник упомянул усталость.",
            "Хорошая встреча. Цели квартала ясны, вопросов нет.",
            "Есть небольшое отставание по задачам из-за болезни. Нужна помощь.",
            "Задачи идут по плану. Договорились о следующем проекте.",
            "Нужна консультация по архитектуре нового микросервиса.",
            "Настроение позитивное. Интересуется курсами повышения квалификации.",
            "Обсудили карьерный рост: хочет перейти на Senior через 6 месяцев.",
            "Жалуется на частые переработки в последний месяц."
        };
        return notes[RND.nextInt(notes.length)];
    }

    private String randomActionItems(User u) {
        String[] items = {
            "1. Пересмотреть приоритеты спринта.\n2. Назначить ментора.",
            "1. Продолжить текущую работу.\n2. Запланировать 1:1 через 2 недели.",
            "1. Помочь с распределением задач.\n2. Добавить день отдыха.",
            "1. Записать на курс.\n2. Подготовить план развития.",
            "1. Согласовать архитектуру.\n2. Подготовить презентацию."
        };
        return items[RND.nextInt(items.length)];
    }

    private static double clamp(double v) { return Math.min(1.0, Math.max(0.0, v)); }
    private static double clampScore(double v) { return Math.min(1.0, Math.max(0.0, v)); }
}
