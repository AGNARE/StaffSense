package com.staffsense.demo.service;

import com.staffsense.demo.model.*;
import com.staffsense.demo.repository.ServiceMemoRepository;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.repository.WorkScheduleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тесты служебных записок (BR-029, BR-003, BR-030).
 *
 * <p>Подать записку может любой сотрудник; маршрут — виза руководителя,
 * затем утверждение HR; автор может редактировать и отозвать до решения;
 * утверждённый больничный/отпуск проставляется в табеле.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ServiceMemoServiceTest {

    @Autowired
    private ServiceMemoService memoService;

    @Autowired
    private ServiceMemoRepository memoRepository;

    @Autowired
    private WorkScheduleRepository scheduleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TimesheetService timesheetService;

    private User user(String email) {
        return userRepository.findByEmail(email).orElseThrow();
    }

    private LocalDate day(int plusDays) {
        return LocalDate.now().plusDays(plusDays);
    }

    @Test
    void anyEmployeeCanSubmit() {
        User employee = user("ivan@staffsense.com");
        ServiceMemo memo = memoService.submit(employee, MemoType.VACATION, day(1), day(3), "Отпуск");

        assertEquals(MemoStatus.PENDING_PM, memo.getStatus());
        assertEquals(employee.getId(), memo.getUser().getId());
        assertEquals(3, memo.getDaysCount());
    }

    @Test
    void pmApprovalMovesToHrStage() {
        User employee = user("ivan@staffsense.com");
        User pm = user("pm@staffsense.com");

        ServiceMemo memo = memoService.submit(employee, MemoType.VACATION, day(1), day(2), null);
        ServiceMemo afterPm = memoService.approveByPm(pm, memo.getId());

        assertEquals(MemoStatus.PENDING_HR, afterPm.getStatus());
        assertEquals(pm.getId(), afterPm.getPmDecisionBy().getId());
    }

    @Test
    void foreignPmCannotApprove() {
        User employee = user("ivan@staffsense.com");
        User otherPm = user("pm2@staffsense.com");

        ServiceMemo memo = memoService.submit(employee, MemoType.VACATION, day(1), day(2), null);

        assertThrows(AccessDeniedException.class,
                () -> memoService.approveByPm(otherPm, memo.getId()));
    }

    @Test
    void hrApprovalMarksTimesheetDays() {
        User employee = user("ivan@staffsense.com");
        User pm = user("pm@staffsense.com");
        User hr = user("hr@staffsense.com");
        LocalDate start = day(1);
        LocalDate end = day(2);

        ServiceMemo memo = memoService.submit(employee, MemoType.SICK_LEAVE, start, end, "Болезнь");
        memoService.approveByPm(pm, memo.getId());
        ServiceMemo approved = memoService.approveByHr(hr, memo.getId());

        assertEquals(MemoStatus.APPROVED, approved.getStatus());
        assertEquals(hr.getId(), approved.getHrDecisionBy().getId());

        // BR-030: дни больничного проставлены в табеле
        var entries = scheduleRepository
                .findByUserIdAndWorkDateBetweenOrderByWorkDateAsc(employee.getId(), start, end);
        assertEquals(2, entries.size(), "Должны появиться записи табеля за дни больничного");
        assertTrue(entries.stream().allMatch(WorkSchedule::isSickLeave));
        assertTrue(entries.stream().allMatch(e -> e.getHoursWorked() != null && e.getHoursWorked() == 0.0));
    }

    @Test
    void vacationMarksVacationDays() {
        User employee = user("ivan@staffsense.com");
        User pm = user("pm@staffsense.com");
        User hr = user("hr@staffsense.com");
        LocalDate start = day(5);

        ServiceMemo memo = memoService.submit(employee, MemoType.VACATION, start, start, null);
        memoService.approveByPm(pm, memo.getId());
        memoService.approveByHr(hr, memo.getId());

        var entry = scheduleRepository.findByUserIdAndWorkDate(employee.getId(), start).orElseThrow();
        assertTrue(entry.isVacation());
        assertFalse(entry.isSickLeave());
    }

    @Test
    void employeeCannotApproveByHr() {
        User employee = user("ivan@staffsense.com");
        User pm = user("pm@staffsense.com");

        ServiceMemo memo = memoService.submit(employee, MemoType.VACATION, day(1), day(2), null);
        memoService.approveByPm(pm, memo.getId());

        assertThrows(AccessDeniedException.class,
                () -> memoService.approveByHr(employee, memo.getId()));
    }

    @Test
    void rejectOnPmStage() {
        User employee = user("ivan@staffsense.com");
        User pm = user("pm@staffsense.com");

        ServiceMemo memo = memoService.submit(employee, MemoType.VACATION, day(1), day(2), null);
        ServiceMemo rejected = memoService.reject(pm, memo.getId(), "Высокая нагрузка");

        assertEquals(MemoStatus.REJECTED, rejected.getStatus());
        assertEquals("Высокая нагрузка", rejected.getRejectionReason());
    }

    @Test
    void authorCanEditAndCancelBeforeDecision() {
        User employee = user("ivan@staffsense.com");
        ServiceMemo memo = memoService.submit(employee, MemoType.VACATION, day(1), day(2), "черновик");

        ServiceMemo updated = memoService.update(employee, memo.getId(),
                MemoType.OTHER, day(3), day(4), "изменено");
        assertEquals(MemoType.OTHER, updated.getMemoType());
        assertEquals(day(3), updated.getStartDate());

        ServiceMemo cancelled = memoService.cancel(employee, memo.getId());
        assertEquals(MemoStatus.CANCELLED, cancelled.getStatus());
    }

    @Test
    void authorCannotEditAfterDecision() {
        User employee = user("ivan@staffsense.com");
        User pm = user("pm@staffsense.com");
        User hr = user("hr@staffsense.com");

        ServiceMemo memo = memoService.submit(employee, MemoType.VACATION, day(1), day(2), null);
        memoService.approveByPm(pm, memo.getId());
        memoService.approveByHr(hr, memo.getId());

        assertThrows(IllegalStateException.class,
                () -> memoService.update(employee, memo.getId(), MemoType.OTHER, day(3), day(4), null));
        assertThrows(IllegalStateException.class,
                () -> memoService.cancel(employee, memo.getId()));
    }

    @Test
    void onlyAuthorCanEditOrCancel() {
        User employee = user("ivan@staffsense.com");
        User colleague = user("anna@staffsense.com");

        ServiceMemo memo = memoService.submit(employee, MemoType.VACATION, day(1), day(2), null);

        assertThrows(AccessDeniedException.class,
                () -> memoService.update(colleague, memo.getId(), MemoType.OTHER, day(3), day(4), null));
        assertThrows(AccessDeniedException.class,
                () -> memoService.cancel(colleague, memo.getId()));
    }

    @Test
    void rejectsInvalidPeriod() {
        User employee = user("ivan@staffsense.com");

        assertThrows(IllegalArgumentException.class,
                () -> memoService.submit(employee, MemoType.VACATION, day(5), day(1), null));
    }

    @Test
    void pmSeesOwnTeamMemosOnly() {
        User pm = user("pm@staffsense.com");
        User teamEmployee = user("ivan@staffsense.com");

        memoService.submit(teamEmployee, MemoType.VACATION, day(1), day(2), null);

        assertTrue(memoService.getVisibleMemos(pm).stream()
                        .anyMatch(m -> m.getUser().getId().equals(teamEmployee.getId())),
                "PM должен видеть записки своей команды");
    }

    /**
     * BR-028: утверждение записки не должно переписывать табель закрытого месяца.
     * Раньше {@code approveByHr} проставлял дни без проверки закрытия периода.
     */
    @Test
    void approvalIsBlockedWhenTimesheetPeriodIsClosed() {
        User employee = user("ivan@staffsense.com");
        User pm = user("pm@staffsense.com");
        User hr = user("hr@staffsense.com");
        LocalDate firstOfMonth = LocalDate.now().withDayOfMonth(1);

        ServiceMemo memo = memoService.submit(employee, MemoType.SICK_LEAVE,
                firstOfMonth, firstOfMonth, "Болезнь");
        memoService.approveByPm(pm, memo.getId());

        timesheetService.closePeriod(pm, pm, firstOfMonth.getYear(), firstOfMonth.getMonthValue());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> memoService.approveByHr(hr, memo.getId()));
        assertTrue(ex.getMessage().contains("закрыт"), "Отказ должен ссылаться на закрытый период");

        assertEquals(MemoStatus.PENDING_HR, memoService.findById(memo.getId()).getStatus(),
                "Записка не должна перейти в APPROVED, если период закрыт");
        assertTrue(scheduleRepository
                        .findByUserIdAndWorkDateBetweenOrderByWorkDateAsc(employee.getId(),
                                firstOfMonth, firstOfMonth)
                        .isEmpty(),
                "Табель закрытого месяца изменяться не должен");
    }

    /** Форма правки доступна только автору и только до финального решения (BR-029). */
    @Test
    void editRequiresAuthorAndEditableState() {
        User author = user("ivan@staffsense.com");
        User colleague = user("anna@staffsense.com");

        ServiceMemo memo = memoService.submit(author, MemoType.OTHER, day(1), day(2), null);

        assertThrows(AccessDeniedException.class,
                () -> memoService.requireEditableBy(colleague, memoService.findById(memo.getId())),
                "Чужую записку редактировать нельзя");

        memoService.cancel(author, memo.getId());

        assertThrows(IllegalStateException.class,
                () -> memoService.requireEditableBy(author, memoService.findById(memo.getId())),
                "Отозванную записку редактировать нельзя");
    }
}
