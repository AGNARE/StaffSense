package com.staffsense.demo.controller;

import com.staffsense.demo.dto.timesheet.TimesheetReport;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.service.TeamAccessService;
import com.staffsense.demo.service.TimesheetService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Табель рабочего времени (модуль C).
 *
 * <p>Часы вносит руководитель по своей команде; HR и ADMIN правят любые табели;
 * сотрудник видит свой табель <b>только на чтение</b> (BR-027).
 */
@Controller
@RequestMapping("/timesheet")
@RequiredArgsConstructor
public class TimesheetController {

    private final TimesheetService timesheetService;
    private final UserRepository userRepository;
    private final TeamAccessService teamAccessService;

    /** Табель выбранного сотрудника за месяц. */
    @GetMapping
    public String timesheet(@RequestParam(required = false) Long userId,
                            @RequestParam(required = false) String month,
                            Model model, Authentication auth) {
        User actor = teamAccessService.requireCurrentUser(auth);
        LocalDate periodStart = parseMonth(month);
        LocalDate periodEnd = periodStart.withDayOfMonth(periodStart.lengthOfMonth());

        List<User> viewable = timesheetService.getViewableEmployees(actor);
        User selected = resolveSelected(actor, userId, viewable);

        if (!timesheetService.canView(actor, selected)) {
            throw new AccessDeniedException("Нет доступа к табелю сотрудника ID=" + selected.getId());
        }

        model.addAttribute("currentUser", actor);
        model.addAttribute("employees", viewable);
        model.addAttribute("selected", selected);
        model.addAttribute("entries", timesheetService.getEntries(selected.getId(), periodStart, periodEnd));
        model.addAttribute("canEdit", timesheetService.canEdit(actor, selected));
        model.addAttribute("locked", timesheetService.isDateLocked(selected, periodStart));
        model.addAttribute("periodLabel", timesheetService.periodLabel(periodStart));
        model.addAttribute("monthValue", YearMonth.from(periodStart).toString());
        model.addAttribute("periodStart", periodStart);
        model.addAttribute("periodEnd", periodEnd);
        model.addAttribute("readOnlyRole", actor.getRole() == Role.EMPLOYEE);
        return "timesheet/index";
    }

    /** Внести или обновить запись за день. */
    @PostMapping("/entry")
    public String saveEntry(@RequestParam Long userId,
                            @RequestParam String workDate,
                            @RequestParam(required = false) Double hours,
                            @RequestParam(defaultValue = "false") boolean remote,
                            @RequestParam(defaultValue = "false") boolean sickLeave,
                            @RequestParam(defaultValue = "false") boolean vacation,
                            @RequestParam(required = false) Double overtime,
                            @RequestParam(required = false) String month,
                            Authentication auth,
                            RedirectAttributes redirectAttrs) {
        User actor = teamAccessService.requireCurrentUser(auth);
        LocalDate date = LocalDate.parse(workDate);

        try {
            timesheetService.saveEntry(actor, userId, date, hours, remote, sickLeave, vacation, overtime);
            redirectAttrs.addFlashAttribute("success", "Запись за " + workDate + " сохранена.");
        } catch (AccessDeniedException | IllegalStateException | IllegalArgumentException e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/timesheet?userId=" + userId + "&month=" + (month != null ? month : "");
    }

    /** Удалить запись за день. */
    @PostMapping("/entry/delete")
    public String deleteEntry(@RequestParam Long userId,
                              @RequestParam String workDate,
                              @RequestParam(required = false) String month,
                              Authentication auth,
                              RedirectAttributes redirectAttrs) {
        User actor = teamAccessService.requireCurrentUser(auth);
        try {
            timesheetService.deleteEntry(actor, userId, LocalDate.parse(workDate));
            redirectAttrs.addFlashAttribute("info", "Запись за " + workDate + " удалена.");
        } catch (AccessDeniedException | IllegalStateException e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/timesheet?userId=" + userId + "&month=" + (month != null ? month : "");
    }

    /** Закрыть месяц (BR-028: после закрытия правка запрещена). */
    @PostMapping("/close")
    public String closePeriod(@RequestParam(required = false) String month,
                              @RequestParam(required = false) Long managerId,
                              Authentication auth,
                              RedirectAttributes redirectAttrs) {
        User actor = teamAccessService.requireCurrentUser(auth);
        LocalDate periodStart = parseMonth(month);

        User manager = (managerId != null
                ? userRepository.findById(managerId).orElse(null)
                : null);
        if (manager == null) {
            // По умолчанию: PM закрывает свою команду, HR/ADMIN — команду выбранного руководителя.
            manager = actor;
        }

        try {
            timesheetService.closePeriod(actor, manager, periodStart.getYear(), periodStart.getMonthValue());
            redirectAttrs.addFlashAttribute("success",
                    "Период «" + timesheetService.periodLabel(periodStart) + "» закрыт. Правка табеля заблокирована.");
        } catch (AccessDeniedException e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/timesheet/report?month=" + YearMonth.from(periodStart);
    }

    /** Отчёт за месяц: по команде либо сводный по компании. */
    @GetMapping("/report")
    public String report(@RequestParam(required = false) String month,
                         @RequestParam(defaultValue = "team") String scope,
                         Model model, Authentication auth) {
        User actor = teamAccessService.requireCurrentUser(auth);
        LocalDate periodStart = parseMonth(month);
        LocalDate periodEnd = periodStart.withDayOfMonth(periodStart.lengthOfMonth());

        boolean companyScope = "company".equals(scope)
                && (actor.getRole() == Role.ADMIN || actor.getRole() == Role.HR);

        List<User> employees = companyScope
                ? userRepository.findByActive(true).stream()
                        .filter(u -> u.getRole() == Role.EMPLOYEE)
                        .toList()
                : timesheetService.getEditableEmployees(actor);

        TimesheetReport report = timesheetService.buildReport(
                employees, periodStart, periodEnd, companyScope ? "Сводный по компании" : "По команде");

        model.addAttribute("currentUser", actor);
        model.addAttribute("report", report);
        model.addAttribute("monthValue", YearMonth.from(periodStart).toString());
        model.addAttribute("scope", companyScope ? "company" : "team");
        model.addAttribute("canClose", actor.getRole() == Role.PM
                || actor.getRole() == Role.HR || actor.getRole() == Role.ADMIN);
        return "timesheet/report";
    }

    // ── Вспомогательные ──────────────────────────────────────────────────

    private User resolveSelected(User actor, Long userId, List<User> viewable) {
        if (userId != null) {
            return userRepository.findById(userId).orElse(actor);
        }
        if (actor.getRole() == Role.EMPLOYEE || viewable.isEmpty()) {
            return actor;
        }
        return viewable.get(0);
    }

    private LocalDate parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return LocalDate.now().withDayOfMonth(1);
        }
        try {
            return YearMonth.parse(month).atDay(1);
        } catch (Exception e) {
            return LocalDate.now().withDayOfMonth(1);
        }
    }
}
