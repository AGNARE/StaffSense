package com.staffsense.demo.api.hr;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Контракт REST-модуля HR: пульт и табель.
 *
 * <p>DTO лежат рядом с {@link HrApiController} и живут только в JSON-слое:
 * наружу не отдаётся ни одна JPA-сущность. Причина не косметическая —
 * {@code User} содержит {@code passwordHash} и {@code salary}, а
 * {@code TimesheetRow} (существующий DTO Thymeleaf-отчёта) хранит саму
 * сущность сотрудника. Отдать их в JSON значило бы вынести хеш пароля и
 * оклад в браузер.
 *
 * <p>Принцип «нет выдуманных чисел»: если данных в БД нет, поле приходит
 * {@code null} (или пустой массив), а не {@code 0}. Клиент рисует прочерк.
 */
public final class HrApiDtos {

    private HrApiDtos() {
    }

    // ── Пульт HR ─────────────────────────────────────────────────────────

    /**
     * Сводка «Пульт HR».
     *
     * @param generatedAt     когда посчитано
     * @param date            якорная дата (по умолчанию — сегодня)
     * @param week            неделя (пн–вс), в которую попадает якорная дата
     * @param headcount       сколько людей в штате
     * @param probation       кто на испытательном сроке
     * @param forecast        у кого есть/нет прогноза увольнения
     * @param absences        отсутствия: сегодня, на неделе, ближайшие
     * @param documents       документы, ждущие решения
     */
    public record Desk(
            LocalDateTime generatedAt,
            LocalDate date,
            Period week,
            Headcount headcount,
            Probation probation,
            Forecast forecast,
            Absences absences,
            Documents documents
    ) {
    }

    /** Границы периода; {@code from} и {@code to} включительно. */
    public record Period(LocalDate from, LocalDate to) {
    }

    /**
     * Штат: всего активных учётных записей, из них сотрудников (роль EMPLOYEE)
     * и остальных (руководители, HR, администраторы, EXEC).
     */
    public record Headcount(
            int total,
            int employees,
            int other,
            List<DepartmentHeadcount> byDepartment
    ) {
    }

    public record DepartmentHeadcount(String department, int headcount) {
    }

    /**
     * Испытательный срок.
     *
     * <p>В схеме БД нет поля «испытательный срок» — правило выводится из даты
     * приёма ({@code hireDate + months}). Поэтому в ответе едут и сам срок
     * ({@code months}), и основание ({@code basis}): клиент подписывает блок
     * правилом, а не зашитой в него цифрой.
     */
    public record Probation(
            int months,
            String basis,
            int total,
            List<ProbationItem> items
    ) {
    }

    /**
     * @param daysLeft       сколько дней осталось до конца испытательного срока (0 — последний день)
     * @param checklistDone  пройдено пунктов чек-листа онбординга
     * @param checklistTotal всего пунктов чек-листа
     */
    public record ProbationItem(
            Long userId,
            String fullName,
            String department,
            String position,
            LocalDate hireDate,
            LocalDate probationEnd,
            long daysLeft,
            int checklistDone,
            int checklistTotal
    ) {
    }

    /**
     * Покрытие прогнозами увольнения: сколько сотрудников и у кого прогноза нет.
     *
     * @param total          активных сотрудников (роль EMPLOYEE)
     * @param withForecast   у кого есть хотя бы один сохранённый прогноз
     * @param withoutForecast у кого прогноза нет
     */
    public record Forecast(
            int total,
            int withForecast,
            int withoutForecast,
            List<MissingForecast> items
    ) {
    }

    public record MissingForecast(
            Long userId,
            String fullName,
            String department,
            String position,
            LocalDate hireDate
    ) {
    }

    /**
     * Отсутствия.
     *
     * @param todayCount    сколько человек отсутствует на якорную дату
     * @param weekCount     сколько отрезков отсутствия попадает в неделю
     * @param upcomingCount сколько отрезков начинается в ближайшие {@code UPCOMING_DAYS} дней
     * @param today         отсутствующие на якорную дату (полный период, а не обрезок недели)
     * @param week          отсутствия, пересекающиеся с неделей
     * @param upcoming      текущие и ближайшие отсутствия
     * @param coverage      отсутствия по подразделениям на якорную дату
     */
    public record Absences(
            int todayCount,
            int weekCount,
            int upcomingCount,
            List<Absence> today,
            List<Absence> week,
            List<Absence> upcoming,
            List<DepartmentCoverage> coverage
    ) {
    }

    /**
     * Отрезок отсутствия.
     *
     * @param type      код типа ({@code VACATION}, {@code SICK_LEAVE}, {@code OTHER})
     * @param typeTitle подпись типа («Отпуск», «Больничный», «Прочее»)
     * @param from      начало периода отсутствия
     * @param to        конец периода отсутствия
     * @param days      календарных дней в периоде
     * @param source    откуда взято: {@code MEMO} — утверждённая записка,
     *                  {@code TIMESHEET} — только отметки в табеле
     */
    public record Absence(
            Long userId,
            String fullName,
            String department,
            String position,
            String type,
            String typeTitle,
            LocalDate from,
            LocalDate to,
            long days,
            String source
    ) {
    }

    /**
     * @param sharePercent доля отсутствующих, % с одним знаком; {@code null}, если
     *                     в подразделении никого нет (делить не на что)
     */
    public record DepartmentCoverage(
            String department,
            int headcount,
            int absent,
            Double sharePercent
    ) {
    }

    /** Документы в статусе «на согласовании» — что ждёт решения HR. */
    public record Documents(int onApproval, List<DocumentItem> items) {
    }

    public record DocumentItem(
            Long id,
            String regNumber,
            String title,
            String type,
            String typeTitle,
            String employeeName,
            LocalDate documentDate
    ) {
    }

    // ── Табель ───────────────────────────────────────────────────────────

    /**
     * Табель за период с агрегатами по сотруднику.
     *
     * <p>Агрегаты не пересчитываются в контроллере «своей» формулой: строки
     * строит {@code TimesheetService#buildReport} — тот же сервис, что и
     * HTML-страница отчёта. Здесь только перевод в JSON и суммы по уже
     * посчитанным строкам.
     *
     * @param department       выбранное подразделение ({@code null} — все)
     * @param departments      доступные подразделения для фильтра
     * @param closed           закрыт ли период хотя бы у одного сотрудника (BR-028)
     * @param absenceRequests  служебные записки, пересекающиеся с периодом
     */
    public record Timesheet(
            LocalDate from,
            LocalDate to,
            String periodLabel,
            String department,
            List<String> departments,
            boolean closed,
            TimesheetTotals totals,
            List<TimesheetEmployee> rows,
            List<Absence> absences,
            List<AbsenceRequest> absenceRequests
    ) {
    }

    /**
     * Итоги по периоду.
     *
     * @param employeesWithData сколько сотрудников имеют хотя бы одну запись
     */
    public record TimesheetTotals(
            int employees,
            int employeesWithData,
            double totalHours,
            double overtimeHours,
            long workingDays,
            long remoteDays,
            long sickDays,
            long vacationDays
    ) {
    }

    /**
     * Строка табеля по сотруднику.
     *
     * @param totalHours   сумма отработанных часов
     * @param workingDays  число дней с записью в табеле
     * @param hasData      есть ли вообще записи за период (иначе все числа — «нет данных»)
     */
    public record TimesheetEmployee(
            Long userId,
            String fullName,
            String department,
            String position,
            double totalHours,
            double overtimeHours,
            long workingDays,
            long remoteDays,
            long sickDays,
            long vacationDays,
            boolean hasData,
            List<TimesheetDay> days
    ) {
    }

    public record TimesheetDay(
            LocalDate date,
            Double hours,
            boolean remote,
            boolean sickLeave,
            boolean vacation,
            Double overtimeHours
    ) {
    }

    /** Заявка на отсутствие (служебная записка) в периоде табеля. */
    public record AbsenceRequest(
            Long id,
            Long userId,
            String fullName,
            String department,
            String type,
            String typeTitle,
            LocalDate startDate,
            LocalDate endDate,
            long days,
            String status,
            String statusTitle,
            LocalDateTime createdAt
    ) {
    }

    // ── Отметка часов ────────────────────────────────────────────────────

    /**
     * Отметка часов за день. Часы необязательны: день отсутствия может быть
     * без часов ({@code hours = null}) — это не ноль, а «день без работы».
     */
    public record TimesheetEntryRequest(
            @NotNull(message = "userId обязателен") Long userId,
            @NotNull(message = "date обязателен") LocalDate date,
            @DecimalMin(value = "0.0", message = "часы не могут быть отрицательными")
            @DecimalMax(value = "24.0", message = "часов в дне не может быть больше 24")
            Double hours,
            boolean remote,
            boolean sickLeave,
            boolean vacation,
            @DecimalMin(value = "0.0", message = "переработка не может быть отрицательной")
            @DecimalMax(value = "24.0", message = "переработка не может быть больше 24 ч в день")
            Double overtimeHours
    ) {
    }

    /** Сохранённая запись табеля. */
    public record TimesheetEntry(
            Long id,
            Long userId,
            LocalDate date,
            Double hours,
            boolean remote,
            boolean sickLeave,
            boolean vacation,
            Double overtimeHours,
            LocalDateTime updatedAt
    ) {
    }
}
