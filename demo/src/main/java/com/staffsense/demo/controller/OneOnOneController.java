package com.staffsense.demo.controller;

import com.staffsense.demo.model.*;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.service.OneOnOneService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/oneonone")
public class OneOnOneController {

    private final OneOnOneService meetingService;
    private final UserRepository userRepository;

    public OneOnOneController(OneOnOneService meetingService, UserRepository userRepository) {
        this.meetingService = meetingService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String listMeetings(@RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "15") int size,
                               Model model, Authentication auth) {
        User currentUser = userRepository.findByEmail(auth.getName()).orElseThrow();
        Pageable pageable = PageRequest.of(page, size, Sort.by("scheduledAt").descending());
        Page<OneOnOneMeeting> meetingPage;
        if (currentUser.getRole() == Role.EMPLOYEE) {
            meetingPage = meetingService.findAllForEmployee(currentUser, pageable);
        } else if (currentUser.getRole() == Role.PM || currentUser.getRole() == Role.HR) {
            meetingPage = meetingService.findAllForManager(currentUser, pageable);
        } else {
            meetingPage = meetingService.findAll(pageable);
        }
        model.addAttribute("meetingPage", meetingPage);
        model.addAttribute("meetings", meetingPage.getContent());
        model.addAttribute("currentUser", currentUser);
        return "oneonone/list";
    }

    @GetMapping("/new")
    public String newMeetingForm(Model model, Authentication auth) {
        User currentUser = userRepository.findByEmail(auth.getName()).orElseThrow();
        List<User> employees = userRepository.findAll().stream()
                .filter(u -> u.isActive() && u.getRole() == Role.EMPLOYEE)
                .toList();
        model.addAttribute("employees", employees);
        model.addAttribute("currentUser", currentUser);
        return "oneonone/new";
    }

    @PostMapping("/new")
    public String createMeeting(@RequestParam Long employeeId,
                                @RequestParam String scheduledAt,
                                Authentication auth,
                                RedirectAttributes redirectAttrs) {
        User manager = userRepository.findByEmail(auth.getName()).orElseThrow();
        LocalDateTime dateTime = LocalDateTime.parse(scheduledAt);
        meetingService.create(employeeId, manager.getId(), dateTime);
        redirectAttrs.addFlashAttribute("success", "Встреча 1:1 запланирована!");
        return "redirect:/oneonone";
    }

    @PostMapping("/{id}/complete")
    public String completeMeeting(@PathVariable Long id,
                                  @RequestParam(required = false) String notes,
                                  @RequestParam(required = false) String actionItems,
                                  RedirectAttributes redirectAttrs) {
        meetingService.complete(id, notes, actionItems);
        redirectAttrs.addFlashAttribute("success", "Встреча отмечена как завершённая.");
        return "redirect:/oneonone";
    }

    @PostMapping("/{id}/cancel")
    public String cancelMeeting(@PathVariable Long id, RedirectAttributes redirectAttrs) {
        meetingService.cancel(id);
        redirectAttrs.addFlashAttribute("info", "Встреча отменена.");
        return "redirect:/oneonone";
    }
}
