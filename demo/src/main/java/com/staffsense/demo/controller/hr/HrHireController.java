package com.staffsense.demo.controller.hr;

import com.staffsense.demo.dto.hr.HireEmployeeForm;
import com.staffsense.demo.dto.hr.HireResult;
import com.staffsense.demo.exception.ResourceNotFoundException;
import com.staffsense.demo.model.GradeLevel;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.service.TeamAccessService;
import com.staffsense.demo.service.hr.HireEmployeeService;
import com.staffsense.demo.service.tunduk.MockTundukClient;
import com.staffsense.demo.service.tunduk.TundukLookupResult;
import com.staffsense.demo.service.tunduk.TundukPersonData;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Приём сотрудника на работу с верификацией через «Тундук» (модуль A).
 *
 * <p>Доступен только ADMIN и HR: обработка паспортных данных — чувствительная
 * операция, поэтому она не должна быть доступна руководителям и сотрудникам.
 */
@Controller
@RequestMapping("/hr")
@PreAuthorize("hasAnyRole('ADMIN','HR')")
@RequiredArgsConstructor
public class HrHireController {

    private final HireEmployeeService hireService;
    private final UserRepository userRepository;
    private final TeamAccessService teamAccessService;
    /** Инжектируется конкретная реализация: подсказка с тестовыми ИНН — деталь эмуляции. */
    private final MockTundukClient mockTundukClient;

    // ── Шаг 1: форма приёма ──────────────────────────────────────────────

    @GetMapping("/hire")
    public String hireForm(Model model, Authentication auth) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new HireEmployeeForm());
        }
        addReferenceData(model, auth);
        return "hr/hire";
    }

    // ── Шаг 2: верификация ИНН ───────────────────────────────────────────

    @PostMapping("/hire/verify")
    public String verify(@ModelAttribute("form") HireEmployeeForm form,
                         Model model,
                         Authentication auth) {
        User actor = teamAccessService.requireCurrentUser(auth);

        TundukLookupResult result;
        try {
            // BR-024: согласие передаётся в сервис — без него персональные данные
            // в госсистеме не запрашиваются.
            result = hireService.verify(form.getInn(), form.isConsentGiven(), actor);
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("form", form);
            addReferenceData(model, auth);
            return "hr/hire";
        }

        switch (result.status()) {
            case FOUND -> {
                TundukPersonData person = result.data();
                form.setFullName(person.fullName());
                form.setBirthDate(person.birthDate());
                form.setGender(person.gender());
                form.setPin(person.pin());
                form.setPassportSeries(person.passportSeries());
                form.setPassportNumber(person.passportNumber());
                form.setPassportIssuedBy(person.passportIssuedBy());
                form.setPassportIssueDate(person.passportIssueDate());
                form.setRegistrationAddress(person.registrationAddress());
                form.setPhotoUrl(person.photoUrl());
                form.setTundukVerified(true);
                model.addAttribute("success", "Данные получены из «Тундук». Проверьте и заполните корпоративные поля.");
            }
            case NOT_FOUND, UNAVAILABLE -> {
                form.setTundukVerified(false);
                model.addAttribute("warning", result.message() + ". Заполните данные вручную.");
            }
            case INVALID -> model.addAttribute("error", result.message());
        }

        model.addAttribute("form", form);
        addReferenceData(model, auth);
        return "hr/hire";
    }

    // ── Шаг 3: сохранение ────────────────────────────────────────────────

    @PostMapping("/hire")
    public String hire(@Valid @ModelAttribute("form") HireEmployeeForm form,
                       BindingResult binding,
                       Model model,
                       Authentication auth,
                       RedirectAttributes redirectAttrs) {
        if (binding.hasErrors()) {
            addReferenceData(model, auth);
            return "hr/hire";
        }

        try {
            HireResult result = hireService.hire(form, teamAccessService.requireCurrentUser(auth));

            String message;
            if (result.reactivated()) {
                message = "Сотрудник «" + result.user().getFullName()
                          + "» уже был в системе (ИНН совпал) — учётная запись реактивирована, дубль не создан.";
            } else if (result.alreadyExisted()) {
                message = "Сотрудник «" + result.user().getFullName()
                          + "» уже есть в системе — данные обновлены, новый дубль не создан.";
            } else {
                message = "Сотрудник «" + result.user().getFullName() + "» принят.";
            }

            if (result.generatedPassword() != null) {
                message += " Временный пароль: " + result.generatedPassword();
            }

            redirectAttrs.addFlashAttribute("success", message);
            return "redirect:/hr/documents/" + result.user().getId();

        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            addReferenceData(model, auth);
            return "hr/hire";
        }
    }

    // ── Личное дело ──────────────────────────────────────────────────────

    @GetMapping("/documents/{userId}")
    public String documents(@PathVariable Long userId, Model model, Authentication auth) {
        User employee = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Сотрудник не найден: " + userId));

        model.addAttribute("employee", employee);
        model.addAttribute("documents", hireService.findDocuments(userId).orElse(null));
        model.addAttribute("currentUser", teamAccessService.requireCurrentUser(auth));
        return "hr/documents";
    }

    // ── Справочные данные для формы ──────────────────────────────────────

    private void addReferenceData(Model model, Authentication auth) {
        model.addAttribute("currentUser", teamAccessService.requireCurrentUser(auth));
        model.addAttribute("roles", Role.values());
        model.addAttribute("grades", GradeLevel.values());
        model.addAttribute("managers", userRepository.findByActive(true).stream()
                .filter(u -> u.getRole() == Role.PM || u.getRole() == Role.HR || u.getRole() == Role.ADMIN)
                .toList());
        model.addAttribute("demoInns", List.copyOf(mockTundukClient.getDemoInns()));
        model.addAttribute("unavailableInn", MockTundukClient.UNAVAILABLE_INN);
    }
}
