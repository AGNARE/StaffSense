package com.staffsense.demo.service.hr;

import com.staffsense.demo.dto.onboarding.ChecklistProgress;
import com.staffsense.demo.model.Document;
import com.staffsense.demo.model.DocumentStatus;
import com.staffsense.demo.model.MemoStatus;
import com.staffsense.demo.model.MemoType;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.ServiceMemo;
import com.staffsense.demo.model.User;
import com.staffsense.demo.model.WorkSchedule;
import com.staffsense.demo.repository.AttritionPredictionRepository;
import com.staffsense.demo.repository.DocumentRepository;
import com.staffsense.demo.repository.ServiceMemoRepository;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.repository.WorkScheduleRepository;
import com.staffsense.demo.service.OnboardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Сводные данные «Пульта HR» и общие выборки для модуля HR.
 *
 * <p><b>Зачем отдельный сервис.</b> Контроллер {@code HrApiController} не должен
 * собирать факты по пяти репозиториям: иначе те же правила («кто отсутствует»,
 * «у кого нет прогноза») разъедутся между пультом и табелем. Здесь собрано
 * ровно одно место, где эти понятия определены.
 *
 * <p><b>Что берётся из БД, а что выводится:</b>
 * <ul>
 *   <li>штат, подразделения — {@code users.active};</li>
 *   <li>отсутствия — служебные записки в статусе {@code APPROVED} (тип, период),
 *       а если записки нет, но дни отмечены в табеле — записи
 *       {@code work_schedule} ({@code is_sick_leave} / {@code is_vacation}).
 *       Дубли не суммируются: пересечение с утверждённой запиской отбрасывается,
 *       потому что записка богаче по данным (тип и период);</li>
 *   <li>прогноз увольнения — наличие записи {@code attrition_predictions};</li>
 *   <li>документы на согласовании — {@code document.status = ON_APPROVAL};</li>
 *   <li><b>испытательный срок — выводится</b> из {@code users.hire_date}:
 *       поля в схеме нет (см. {@link #PROBATION_MONTHS}).</li>
 * </ul>
 *
 * <p>Сервис ничего не досчитывает «на лету»: прогноз не запускается, записки не
 * проставляются в табель. Если данных нет — возвращается пустая выборка, и
 * клиент честно показывает прочерк.
 */
@Service
@RequiredArgsConstructor
public class HrDeskService {

    /**
     * Длительность испытательного срока в месяцах.
     *
     * <p>⚠️ В схеме БД нет поля «испытательный срок» (нет ни колонки, ни
     * справочника), поэтому срок выводится из даты приёма: {@code hireDate + 3}
     * месяца — общая норма ТК КР для рядовых должностей (до 6 месяцев для
     * руководителей в схеме не различить: признака «руководитель» нет).
     * Значение отдаётся в API ({@code probation.months} и {@code basis}),
     * чтобы клиент подписывал блок правилом, а не зашитой в вёрстку цифрой.
     * Если появится колонка {@code probation_end}, менять нужно только этот
     * метод.
     */
    public static final int PROBATION_MONTHS = 3;

    /** Горизонт «ближайших отсутствий»: текущие и начинающиеся в эти дни. */
    public static final int UPCOMING_DAYS = 30;

    /** Источники отсутствия — чтобы клиент мог подписать происхождение. */
    public static final String SOURCE_MEMO = "MEMO";
    public static final String SOURCE_TIMESHEET = "TIMESHEET";

    private final UserRepository userRepository;
    private final AttritionPredictionRepository predictionRepository;
    private final ServiceMemoRepository memoRepository;
    private final WorkScheduleRepository scheduleRepository;
    private final DocumentRepository documentRepository;
    private final OnboardingService onboardingService;

    // ── Модель отсутствия ────────────────────────────────────────────────

    /** Вид отсутствия. {@code OTHER} — командировка/прочее: табель его не помечает. */
    public enum AbsenceKind {
        VACATION(MemoType.VACATION.getTitle()),
        SICK_LEAVE(MemoType.SICK_LEAVE.getTitle()),
        OTHER(MemoType.OTHER.getTitle());

        private final String title;

        AbsenceKind(String title) {
            this.title = title;
        }

        public String getTitle() {
            return title;
        }

        static AbsenceKind of(MemoType type) {
            return switch (type) {
                case VACATION -> VACATION;
                case SICK_LEAVE -> SICK_LEAVE;
                case OTHER -> OTHER;
            };
        }
    }

    /**
     * Отрезок отсутствия одного сотрудника.
     *
     * @param from   начало периода (для записки — как в записке, не обрезается неделей)
     * @param to     конец периода
     * @param days   календарных дней
     * @param source {@link #SOURCE_MEMO} либо {@link #SOURCE_TIMESHEET}
     */
    public record AbsenceSegment(
            Long userId,
            String fullName,
            String department,
            String position,
            AbsenceKind kind,
            LocalDate from,
            LocalDate to,
            long days,
            String source
    ) {
        public boolean covers(LocalDate date) {
            return date != null && !date.isBefore(from) && !date.isAfter(to);
        }
    }

    /** Сотрудник на испытательном сроке с прогрессом онбординга. */
    public record ProbationPerson(
            User user,
            LocalDate probationEnd,
            long daysLeft,
            int checklistDone,
            int checklistTotal
    ) {
    }

    /** Снимок для пульта HR. */
    public record DeskSnapshot(
            LocalDate date,
            LocalDate weekStart,
            LocalDate weekEnd,
            List<User> activeUsers,
            List<User> employees,
            List<ProbationPerson> probation,
            List<User> withoutForecast,
            List<AbsenceSegment> today,
            List<AbsenceSegment> week,
            List<AbsenceSegment> upcoming,
            List<Document> documentsOnApproval,
            LocalDateTime generatedAt
    ) {
    }

    // ── Пульт ────────────────────────────────────────────────────────────

    /**
     * Снимок пульта на дату.
     *
     * @param anchor якорная дата; {@code null} — сегодня. Нужна, чтобы можно было
     *               посмотреть сводку на прошлую неделю, а не только «сейчас».
     */
    @Transactional(readOnly = true)
    public DeskSnapshot desk(LocalDate anchor) {
        LocalDate date = anchor != null ? anchor : LocalDate.now();
        LocalDate weekStart = date.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(6);

        List<User> active = activeUsers();
        List<User> employees = active.stream()
                .filter(user -> user.getRole() == Role.EMPLOYEE)
                .toList();

        List<AbsenceSegment> week = absences(weekStart, weekEnd);
        List<AbsenceSegment> today = week.stream()
                .filter(segment -> segment.covers(date))
                .toList();

        return new DeskSnapshot(
                date,
                weekStart,
                weekEnd,
                active,
                employees,
                probation(active, date),
                employeesWithoutForecast(employees),
                today,
                week,
                absences(date, date.plusDays(UPCOMING_DAYS)),
                documentsOnApproval(),
                LocalDateTime.now());
    }

    /**
     * Активные пользователи, отсортированные по ФИО.
     *
     * <p>«Штат» — это активные учётные записи, а не только роль EMPLOYEE:
     * табель ведётся по всем, и отсутствие администратора — такой же факт,
     * как отсутствие разработчика.
     */
    public List<User> activeUsers() {
        return userRepository.findByActive(true).stream()
                .sorted(Comparator.comparing(User::getFullName,
                        Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    /** Кто сейчас на испытательном сроке: дата приёма + {@link #PROBATION_MONTHS} ещё не прошла. */
    private List<ProbationPerson> probation(List<User> active, LocalDate today) {
        List<ProbationPerson> result = new ArrayList<>();
        for (User user : active) {
            if (user.getHireDate() == null) {
                continue;
            }
            LocalDate end = user.getHireDate().plusMonths(PROBATION_MONTHS);
            if (end.isBefore(today)) {
                continue;
            }
            ChecklistProgress progress = onboardingService.getChecklistProgress(user);
            result.add(new ProbationPerson(
                    user,
                    end,
                    ChronoUnit.DAYS.between(today, end),
                    (int) progress.completed(),
                    (int) progress.total()));
        }
        result.sort(Comparator.comparing(ProbationPerson::probationEnd)
                .thenComparing(person -> person.user().getFullName(),
                        Comparator.nullsLast(String::compareToIgnoreCase)));
        return result;
    }

    /**
     * Сотрудники без сохранённого прогноза увольнения.
     *
     * <p>Проверяется наличие записи, а не её свежесть: «нет прогноза» и
     * «прогноз устарел» — разные состояния, и второе потребовало бы порога
     * актуальности, которого в ТЗ нет. Расчёт здесь не запускается.
     */
    private List<User> employeesWithoutForecast(List<User> employees) {
        List<User> result = new ArrayList<>();
        for (User employee : employees) {
            if (predictionRepository.findFirstByUserIdOrderByComputedAtDesc(employee.getId()).isEmpty()) {
                result.add(employee);
            }
        }
        return result;
    }

    /** Документы в статусе «на согласовании»: свежие сверху по дате документа. */
    public List<Document> documentsOnApproval() {
        return documentRepository.findByStatusOrderByDocumentDateDesc(DocumentStatus.ON_APPROVAL);
    }

    // ── Отсутствия ───────────────────────────────────────────────────────

    /**
     * Отрезки отсутствия, пересекающиеся с {@code [from, to]}.
     *
     * <p>Порядок источников важен: утверждённая записка — первична (она знает
     * тип и полный период), отметки табеля добавляются только там, где записки
     * нет. Иначе один и тот же отпуск посчитался бы дважды.
     */
    @Transactional(readOnly = true)
    public List<AbsenceSegment> absences(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            return List.of();
        }

        Map<Long, User> usersById = activeUsers().stream()
                .collect(Collectors.toMap(User::getId, user -> user, (a, b) -> a, LinkedHashMap::new));

        List<AbsenceSegment> memoMarks = memoAbsences(usersById, from, to);
        List<AbsenceSegment> result = new ArrayList<>(memoMarks);
        result.addAll(timesheetAbsences(usersById, from, to, memoMarks));

        result.sort(Comparator.comparing(AbsenceSegment::from)
                .thenComparing(AbsenceSegment::fullName,
                        Comparator.nullsLast(String::compareToIgnoreCase)));
        return result;
    }

    /** Утверждённые записки, пересекающиеся с периодом. */
    private List<AbsenceSegment> memoAbsences(Map<Long, User> usersById,
                                              LocalDate from, LocalDate to) {
        List<AbsenceSegment> result = new ArrayList<>();
        for (ServiceMemo memo : memoRepository.findAllByOrderByCreatedAtDesc()) {
            if (memo.getStatus() != MemoStatus.APPROVED
                    || memo.getMemoType() == null
                    || memo.getStartDate() == null
                    || memo.getEndDate() == null) {
                continue;
            }
            User user = memo.getUser() != null ? usersById.get(memo.getUser().getId()) : null;
            if (user == null) {
                // Записка неактивного (уволенного) сотрудника в сводку не попадает.
                continue;
            }
            if (memo.getEndDate().isBefore(from) || memo.getStartDate().isAfter(to)) {
                continue;
            }
            result.add(segment(user, AbsenceKind.of(memo.getMemoType()),
                    memo.getStartDate(), memo.getEndDate(), SOURCE_MEMO));
        }
        return result;
    }

    /**
     * Отметки табеля ({@code is_sick_leave} / {@code is_vacation}), не покрытые
     * утверждённой запиской. Подряд идущие дни одного вида склеиваются в один
     * отрезок, чтобы «больничный 12–14.10» не превращался в три строки.
     */
    private List<AbsenceSegment> timesheetAbsences(Map<Long, User> usersById,
                                                   LocalDate from, LocalDate to,
                                                   List<AbsenceSegment> memoMarks) {
        if (usersById.isEmpty()) {
            return List.of();
        }

        List<WorkSchedule> entries = scheduleRepository
                .findByUserIdInAndWorkDateBetweenOrderByWorkDateAsc(
                        new ArrayList<>(usersById.keySet()), from, to);

        // Ключ «сотрудник + вид» → упорядоченные даты отметок.
        Map<String, List<LocalDate>> datesByKey = new LinkedHashMap<>();
        Map<String, Long> idByKey = new LinkedHashMap<>();
        for (WorkSchedule entry : entries) {
            if (entry.getUser() == null || entry.getWorkDate() == null) {
                continue;
            }
            AbsenceKind kind = entry.isSickLeave() ? AbsenceKind.SICK_LEAVE
                    : entry.isVacation() ? AbsenceKind.VACATION
                    : null;
            if (kind == null) {
                continue;
            }
            Long userId = entry.getUser().getId();
            if (!usersById.containsKey(userId) || coveredByMemo(memoMarks, userId, kind, entry.getWorkDate())) {
                continue;
            }
            String key = userId + "|" + kind.name();
            idByKey.put(key, userId);
            datesByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(entry.getWorkDate());
        }

        List<AbsenceSegment> result = new ArrayList<>();
        for (Map.Entry<String, List<LocalDate>> entry : datesByKey.entrySet()) {
            Long userId = idByKey.get(entry.getKey());
            User user = usersById.get(userId);
            AbsenceKind kind = AbsenceKind.valueOf(entry.getKey().substring(entry.getKey().indexOf('|') + 1));
            List<LocalDate> dates = entry.getValue().stream().distinct().sorted().toList();

            LocalDate start = dates.get(0);
            LocalDate previous = start;
            for (int i = 1; i <= dates.size(); i++) {
                boolean last = i == dates.size();
                LocalDate current = last ? null : dates.get(i);
                if (!last && current.equals(previous.plusDays(1))) {
                    previous = current;
                    continue;
                }
                result.add(segment(user, kind, start, previous, SOURCE_TIMESHEET));
                if (!last) {
                    start = current;
                    previous = current;
                }
            }
        }
        return result;
    }

    private static boolean coveredByMemo(List<AbsenceSegment> memoMarks, Long userId,
                                         AbsenceKind kind, LocalDate date) {
        for (AbsenceSegment mark : memoMarks) {
            if (Objects.equals(mark.userId(), userId) && mark.kind() == kind && mark.covers(date)) {
                return true;
            }
        }
        return false;
    }

    private static AbsenceSegment segment(User user, AbsenceKind kind,
                                          LocalDate from, LocalDate to, String source) {
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        return new AbsenceSegment(
                user.getId(),
                user.getFullName(),
                user.getDepartment(),
                user.getPosition(),
                kind,
                from,
                to,
                Math.max(days, 0),
                source);
    }

    // ── Служебные записки периода (для экрана табеля) ────────────────────

    /**
     * Записки всех статусов, пересекающиеся с периодом: на согласовании —
     * первыми (они требуют решения), затем по дате начала.
     */
    @Transactional(readOnly = true)
    public List<ServiceMemo> memosOverlapping(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            return List.of();
        }
        Set<MemoStatus> pending = Set.of(MemoStatus.PENDING_PM, MemoStatus.PENDING_HR);

        return memoRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(memo -> memo.getStartDate() != null && memo.getEndDate() != null)
                .filter(memo -> !memo.getEndDate().isBefore(from) && !memo.getStartDate().isAfter(to))
                .sorted(Comparator
                        .comparing((ServiceMemo memo) -> !pending.contains(memo.getStatus()))
                        .thenComparing(ServiceMemo::getStartDate))
                .toList();
    }
}
