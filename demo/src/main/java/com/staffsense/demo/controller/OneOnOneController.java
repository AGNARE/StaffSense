package com.staffsense.demo.controller;

import com.staffsense.demo.model.*;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.service.OneOnOneService;
import com.staffsense.demo.service.TeamAccessService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Встречи 1:1.
 *
 * <p>Ранее контроллер не имел ни одной аннотации безопасности: любой
 * аутентифицированный (включая EMPLOYEE) мог открыть форму назначения,
 * создать встречу с произвольным сотрудником и завершить/отменить чужую
 * встречу по ID. Теперь создание и изменение ограничены ролями, а
 * принадлежность проверяется в сервисе.
 */
@Controller
@RequestMapping("/oneonone")
public class OneOnOneController {

    private final OneOnOneService meetingService;
    private final UserRepository userRepository;
    private final TeamAccessService teamAccessService;

    public OneOnOneController(OneOnOneService meetingService,
                              UserRepository userRepository,
                              TeamAccessService teamAccessService) {
        this.meetingService = meetingService;
        this.userRepository = userRepository;
        this.teamAccessService = teamAccessService;
    }

    @GetMapping
    public String listMeetings(@RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "15") int size,
                               Model model, Authentication auth) {
        User currentUser = userRepository.findByEmail(auth.getName()).orElseThrow();
        Pageable pageable = PageRequest.of(page, size, Sort.by("scheduledAt").descending());
        Page<OneOnOneMeeting> meetingPage;
        if (currentUser.getRole() == Role.EMPLOYEE) {
            // Сотрудник видит только свои встречи.
            meetingPage = meetingService.findAllForEmployee(currentUser, pageable);
        } else if (currentUser.getRole() == Role.PM) {
            // PM — только встречи, где он менеджер.
            meetingPage = meetingService.findAllForManager(currentUser, pageable);
        } else {
            // ADMIN, HR, EXEC — все встречи (матрица доступа, раздел 21 ТЗ).
            meetingPage = meetingService.findAll(pageable);
        }
        model.addAttribute("meetingPage", meetingPage);
        model.addAttribute("meetings", meetingPage.getContent());
        model.addAttribute("currentUser", currentUser);
        return "oneonone/list";
    }

    /** Назначение встречи — ADMIN, HR, PM (создание 1:1 в матрице доступа). */
    @GetMapping("/new")
    @PreAuthorize("hasAnyRole('ADMIN','HR','PM')")
    public String newMeetingForm(Model model, Authentication auth) {
        User currentUser = userRepository.findByEmail(auth.getName()).orElseThrow();
        // PM видит только своих подчинённых, ADMIN/HR — всех сотрудников.
        List<User> employees = userRepository.findAll().stream()
                .filter(User::isActive)
                .filter(u -> u.getRole() == Role.EMPLOYEE)
                .filter(u -> teamAccessService.canScheduleMeetingWith(currentUser, u))
                .toList();
        model.addAttribute("employees", employees);
        model.addAttribute("currentUser", currentUser);
        return "oneonone/new";
    }

    @PostMapping("/new")
    @PreAuthorize("hasAnyRole('ADMIN','HR','PM')")
    public String createMeeting(@RequestParam Long employeeId,
                                @RequestParam String scheduledAt,
                                Authentication auth,
                                RedirectAttributes redirectAttrs) {
        User actor = userRepository.findByEmail(auth.getName()).orElseThrow();
        LocalDateTime dateTime = LocalDateTime.parse(scheduledAt);
        meetingService.create(employeeId, actor, dateTime);
        redirectAttrs.addFlashAttribute("success", "Встреча 1:1 запланирована!");
        return "redirect:/oneonone";
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('ADMIN','HR','PM')")
    public String completeMeeting(@PathVariable Long id,
                                  @RequestParam(required = false) String notes,
                                  @RequestParam(required = false) String actionItems,
                                  Authentication auth,
                                  RedirectAttributes redirectAttrs) {
        User actor = userRepository.findByEmail(auth.getName()).orElseThrow();
        meetingService.complete(id, notes, actionItems, actor);
        redirectAttrs.addFlashAttribute("success", "Встреча отмечена как завершённая.");
        return "redirect:/oneonone";
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','HR','PM')")
    public String cancelMeeting(@PathVariable Long id,
                                Authentication auth,
                                RedirectAttributes redirectAttrs) {
        User actor = userRepository.findByEmail(auth.getName()).orElseThrow();
        meetingService.cancel(id, actor);
        redirectAttrs.addFlashAttribute("info", "Встреча отменена.");
        return "redirect:/oneonone";
    }
}
