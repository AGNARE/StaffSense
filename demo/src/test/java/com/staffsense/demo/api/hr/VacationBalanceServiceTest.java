package com.staffsense.demo.api.hr;

import com.staffsense.demo.model.GradeLevel;
import com.staffsense.demo.model.MemoStatus;
import com.staffsense.demo.model.MemoType;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.ServiceMemo;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.ServiceMemoRepository;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.service.hr.VacationBalanceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Баланс отпусков.
 *
 * <p>Проверяем главное разделение: право на отпуск — кадровое решение,
 * использование — факт из утверждённых записок. И что «не начислено»
 * не превращается в «ноль дней».
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class VacationBalanceServiceTest {

    @Autowired
    private VacationBalanceService balanceService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ServiceMemoRepository memoRepository;

    @Test
    @DisplayName("Без начисления остаток не считается")
    void noEntitlementMeansUnknown() {
        User user = employee("vac.none@test.local");

        VacationBalanceService.Balance balance = balanceService.forUser(user, 2026);

        assertThat(balance.entitledKnown()).isFalse();
        assertThat(balance.entitledDays()).isNull();
        assertThat(balance.remainingDays()).isNull();
        assertThat(balance.usedDays()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Остаток = положено + перенос − использовано")
    void remainingIsComputed() {
        User user = employee("vac.balance@test.local");
        balanceService.grant(user, 2026, new BigDecimal("28"), new BigDecimal("5"), "политика 2026");
        memo(user, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 6), MemoStatus.APPROVED);

        VacationBalanceService.Balance balance = balanceService.forUser(user, 2026);

        assertThat(balance.entitledKnown()).isTrue();
        assertThat(balance.usedDays()).isEqualByComparingTo("5");
        assertThat(balance.remainingDays()).isEqualByComparingTo("28");
    }

    @Test
    @DisplayName("Неутверждённые записки не уменьшают остаток")
    void pendingMemosAreIgnored() {
        User user = employee("vac.pending@test.local");
        balanceService.grant(user, 2026, new BigDecimal("28"), BigDecimal.ZERO, null);
        memo(user, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 3), MemoStatus.PENDING_PM);

        assertThat(balanceService.forUser(user, 2026).usedDays()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Отпуск другого года не попадает в расчёт")
    void otherYearsIgnored() {
        User user = employee("vac.year@test.local");
        balanceService.grant(user, 2026, new BigDecimal("28"), BigDecimal.ZERO, null);
        memo(user, LocalDate.of(2025, 7, 1), LocalDate.of(2025, 7, 10), MemoStatus.APPROVED);

        assertThat(balanceService.forUser(user, 2026).usedDays()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Повторное начисление обновляет право, а не создаёт второе")
    void grantIsIdempotent() {
        User user = employee("vac.grant@test.local");
        balanceService.grant(user, 2026, new BigDecimal("28"), BigDecimal.ZERO, null);
        balanceService.grant(user, 2026, new BigDecimal("31"), new BigDecimal("3"), "пересмотр");

        VacationBalanceService.Balance balance = balanceService.forUser(user, 2026);
        assertThat(balance.entitledDays()).isEqualByComparingTo("31");
        assertThat(balance.remainingDays()).isEqualByComparingTo("34");
    }

    private void memo(User user, LocalDate from, LocalDate to, MemoStatus status) {
        memoRepository.save(ServiceMemo.builder()
                .user(user)
                .memoType(MemoType.VACATION)
                .startDate(from)
                .endDate(to)
                .status(status)
                .build());
    }

    private User employee(String email) {
        return userRepository.save(User.builder()
                .fullName("Отпуск " + email)
                .email(email)
                .passwordHash("hash")
                .role(Role.EMPLOYEE)
                .department("Design")
                .position("Specialist")
                .salary(new BigDecimal("50000"))
                .gradeLevel(GradeLevel.MIDDLE)
                .weeklyCapacityHours(40)
                .hireDate(LocalDate.of(2024, 1, 10))
                .active(true)
                .build());
    }
}
