package com.staffsense.demo.controller;

import com.staffsense.demo.model.*;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.service.ServiceMemoService;
import com.staffsense.demo.service.TeamAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

/**
 * Служебные записки (модуль C).
 *
 * <p>Подать записку может любой сотрудник (BR-029); маршрут согласования —
 * виза руководителя, затем утверждение HR.
 */
@Controller
@RequestMapping("/memos")
@RequiredArgsConstructor
public class ServiceMemoController {

    private final ServiceMemoService memoService;
    private final TeamAccessService teamAccessService;
    private final UserRepository userRepository;

    @GetMapping
    public String list(Model model, Authentication auth) {
        User actor = teamAccessService.requireCurrentUser(auth);
        model.addAttribute("currentUser", actor);
        model.addAttribute("memos", memoService.getVisibleMemos(actor));
        model.addAttribute("pendingForPm", memoService.getPendingForPm(actor));
        model.addAttribute("pendingForHr", memoService.getPendingForHr(actor));
        return "memos/list";
    }

    @GetMapping("/new")
    public String createForm(Model model, Authentication auth) {
        model.addAttribute("currentUser", teamAccessService.requireCurrentUser(auth));
        model.addAttribute("types", MemoType.values());
        model.addAttribute("today", LocalDate.now());
        return "memos/form";
    }

    @PostMapping("/new")
    public String create(@RequestParam MemoType memoType,
                         @RequestParam String startDate,
                         @RequestParam String endDate,
                         @RequestParam(required = false) String reason,
                         Authentication auth,
                         RedirectAttributes redirectAttrs) {
        User actor = teamAccessService.requireCurrentUser(auth);
        try {
            ServiceMemo memo = memoService.submit(actor, memoType,
                    LocalDate.parse(startDate), LocalDate.parse(endDate), reason);
            redirectAttrs.addFlashAttribute("success", "Записка подана и направлена на визу руководителю.");
            return "redirect:/memos/" + memo.getId();
        } catch (IllegalArgumentException e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
            return "redirect:/memos/new";
        }
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model, Authentication auth) {
        User actor = teamAccessService.requireCurrentUser(auth);
        ServiceMemo memo = memoService.findById(id);

        boolean isAuthor = actor.getId() != null && actor.getId().equals(memo.getUser().getId());
        boolean privileged = actor.getRole() == Role.ADMIN || actor.getRole() == Role.HR
                || actor.getRole() == Role.EXEC;
        boolean isManager = actor.getRole() == Role.PM
                && memo.getUser().getManager() != null
                && actor.getId().equals(memo.getUser().getManager().getId());

        if (!isAuthor && !privileged && !isManager) {
            throw new AccessDeniedException("Нет доступа к записке ID=" + id);
        }

        model.addAttribute("currentUser", actor);
        model.addAttribute("memo", memo);
        model.addAttribute("isAuthor", isAuthor);
        model.addAttribute("canApprovePm", memo.getStatus() == MemoStatus.PENDING_PM
                && (isManager || actor.getRole() == Role.ADMIN));
        model.addAttribute("canApproveHr", memo.getStatus() == MemoStatus.PENDING_HR
                && (actor.getRole() == Role.HR || actor.getRole() == Role.ADMIN));
        return "memos/view";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model, Authentication auth,
                           RedirectAttributes redirectAttrs) {
        User actor = teamAccessService.requireCurrentUser(auth);
        ServiceMemo memo = memoService.findById(id);

        try {
            // Форма правки — только для автора и только до финального решения (BR-029).
            memoService.requireEditableBy(actor, memo);
        } catch (IllegalStateException e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
            return "redirect:/memos/" + id;
        }

        model.addAttribute("currentUser", actor);
        model.addAttribute("memo", memo);
        model.addAttribute("types", MemoType.values());
        return "memos/edit";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @RequestParam MemoType memoType,
                         @RequestParam String startDate,
                         @RequestParam String endDate,
                         @RequestParam(required = false) String reason,
                         Authentication auth,
                         RedirectAttributes redirectAttrs) {
        User actor = teamAccessService.requireCurrentUser(auth);
        try {
            memoService.update(actor, id, memoType,
                    LocalDate.parse(startDate), LocalDate.parse(endDate), reason);
            redirectAttrs.addFlashAttribute("success", "Записка обновлена.");
        } catch (AccessDeniedException | IllegalStateException | IllegalArgumentException e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/memos/" + id;
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, Authentication auth, RedirectAttributes redirectAttrs) {
        User actor = teamAccessService.requireCurrentUser(auth);
        try {
            memoService.cancel(actor, id);
            redirectAttrs.addFlashAttribute("info", "Записка отозвана.");
        } catch (AccessDeniedException | IllegalStateException e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/memos/" + id;
    }

    @PostMapping("/{id}/approve-pm")
    public String approvePm(@PathVariable Long id, Authentication auth, RedirectAttributes redirectAttrs) {
        User actor = teamAccessService.requireCurrentUser(auth);
        try {
            memoService.approveByPm(actor, id);
            redirectAttrs.addFlashAttribute("success", "Записка завизирована и направлена в HR.");
        } catch (AccessDeniedException | IllegalStateException e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/memos/" + id;
    }

    @PostMapping("/{id}/approve-hr")
    public String approveHr(@PathVariable Long id, Authentication auth, RedirectAttributes redirectAttrs) {
        User actor = teamAccessService.requireCurrentUser(auth);
        try {
            memoService.approveByHr(actor, id);
            redirectAttrs.addFlashAttribute("success",
                    "Записка утверждена. Дни автоматически проставлены в табеле.");
        } catch (AccessDeniedException | IllegalStateException e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/memos/" + id;
    }

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable Long id,
                         @RequestParam(required = false) String rejectionReason,
                         Authentication auth,
                         RedirectAttributes redirectAttrs) {
        User actor = teamAccessService.requireCurrentUser(auth);
        try {
            memoService.reject(actor, id, rejectionReason);
            redirectAttrs.addFlashAttribute("info", "Записка отклонена.");
        } catch (AccessDeniedException | IllegalStateException e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/memos/" + id;
    }
}
