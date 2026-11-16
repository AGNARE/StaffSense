package com.staffsense.demo.service.hr;

import com.staffsense.demo.model.MemoStatus;
import com.staffsense.demo.model.MemoType;
import com.staffsense.demo.model.ServiceMemo;
import com.staffsense.demo.model.User;
import com.staffsense.demo.model.VacationEntitlement;
import com.staffsense.demo.repository.ServiceMemoRepository;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.repository.VacationEntitlementRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Баланс отпусков: положено, использовано, остаток.
 *
 * <p>Ключевое разделение: <b>право</b> на отпуск — кадровое решение и живёт
 * в таблице {@code vacation_entitlement}; <b>использование</b> — это факт,
 * и он считается из утверждённых записок, а не хранится отдельно. Иначе
 * появился бы второй источник правды, который рано или поздно разойдётся
 * с записками.
 *
 * <p>Если права на год нет, сервис возвращает {@code entitled = null} и
 * остаток не считает: «не начислено» и «ноль дней» — разные утверждения.
 */
@Service
public class VacationBalanceService {

    /** Что делать с остатком, который не перенесли. Правило из макета. */
    public static final int CARRY_OVER_LIMIT_DAYS = 14;

    private final VacationEntitlementRepository entitlementRepository;
    private final ServiceMemoRepository memoRepository;
    private final UserRepository userRepository;

    public VacationBalanceService(VacationEntitlementRepository entitlementRepository,
                                  ServiceMemoRepository memoRepository,
                                  UserRepository userRepository) {
        this.entitlementRepository = entitlementRepository;
        this.memoRepository = memoRepository;
        this.userRepository = userRepository;
    }

    /** Строка баланса по сотруднику. */
    public record Balance(
            Long userId,
            String fullName,
            String department,
            String position,
            Integer year,
            BigDecimal entitledDays,
            BigDecimal carriedOverDays,
            BigDecimal usedDays,
            BigDecimal remainingDays,
            boolean entitledKnown
    ) {
    }

    /** Баланс по всем активным сотрудникам за год. */
    public List<Balance> forYear(Integer year) {
        List<User> employees = userRepository.findByActive(true);
        return employees.stream().map(user -> forUser(user, year)).toList();
    }

    /** Баланс одного сотрудника. */
    public Balance forUser(User user, Integer year) {
        VacationEntitlement entitlement = entitlementRepository
                .findByUserIdAndYear(user.getId(), year)
                .orElse(null);

        BigDecimal used = usedDays(user.getId(), year);

        if (entitlement == null) {
            return new Balance(user.getId(), user.getFullName(), user.getDepartment(),
                    user.getPosition(), year, null, null, used, null, false);
        }

        BigDecimal total = entitlement.getEntitledDays().add(entitlement.getCarriedOverDays());
        return new Balance(user.getId(), user.getFullName(), user.getDepartment(),
                user.getPosition(), year, entitlement.getEntitledDays(),
                entitlement.getCarriedOverDays(), used, total.subtract(used), true);
    }

    /** Сколько дней отпуска уже использовано в году (только утверждённые записки). */
    public BigDecimal usedDays(Long userId, Integer year) {
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);

        long days = memoRepository.findAll().stream()
                .filter(memo -> memo.getUser() != null && userId.equals(memo.getUser().getId()))
                .filter(memo -> memo.getMemoType() == MemoType.VACATION)
                .filter(memo -> memo.getStatus() == MemoStatus.APPROVED)
                .filter(memo -> overlaps(memo, from, to))
                .mapToLong(this::daysInside)
                .sum();

        return BigDecimal.valueOf(days);
    }

    /**
     * Начисляет право на отпуск. Повторный вызов обновляет запись —
     * это идемпотентная операция, а не история версий.
     */
    public VacationEntitlement grant(User user, Integer year, BigDecimal entitled,
                                     BigDecimal carriedOver, String note) {
        VacationEntitlement entitlement = entitlementRepository
                .findByUserIdAndYear(user.getId(), year)
                .orElseGet(VacationEntitlement::new);

        entitlement.setUser(user);
        entitlement.setYear(year);
        entitlement.setEntitledDays(entitled);
        entitlement.setCarriedOverDays(carriedOver != null ? carriedOver : BigDecimal.ZERO);
        entitlement.setNote(note);
        if (entitlement.getCreatedAt() == null) {
            entitlement.setCreatedAt(LocalDateTime.now());
        }
        return entitlementRepository.save(entitlement);
    }

    private boolean overlaps(ServiceMemo memo, LocalDate from, LocalDate to) {
        LocalDate start = memo.getStartDate();
        LocalDate end = memo.getEndDate();
        if (start == null || end == null) {
            return false;
        }
        return !end.isBefore(from) && !start.isAfter(to);
    }

    /** Дни записки, попавшие в границы года. */
    private long daysInside(ServiceMemo memo) {
        LocalDate start = memo.getStartDate();
        LocalDate end = memo.getEndDate();
        return ChronoUnit.DAYS.between(start, end) + 1;
    }
}
