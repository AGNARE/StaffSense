package com.staffsense.demo.controller;

import com.staffsense.demo.dto.directory.DirectoryEntryDto;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.service.TeamAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Справочник сотрудников (FR-043) — доступен всем аутентифицированным.
 *
 * <p>🔴 Отдаёт <b>только</b> ФИО, должность, отдел, рабочий email и руководителя.
 * Оклад, KPI, нагрузка и прогноз риска в справочник не попадают: он открыт
 * любому сотруднику, в отличие от {@code /employees}.
 */
@Controller
@RequiredArgsConstructor
public class DirectoryController {

    private final UserRepository userRepository;
    private final TeamAccessService teamAccessService;

    @GetMapping("/directory")
    public String directory(@RequestParam(required = false) String department,
                            @RequestParam(required = false) String q,
                            Model model,
                            Authentication auth) {
        List<User> active = userRepository.findByActive(true);
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        String dept = department == null ? "" : department.trim();

        List<DirectoryEntryDto> entries = active.stream()
                .filter(u -> dept.isBlank() || dept.equals(u.getDepartment()))
                .filter(u -> query.isBlank()
                        || (u.getFullName() != null && u.getFullName().toLowerCase(Locale.ROOT).contains(query))
                        || (u.getPosition() != null && u.getPosition().toLowerCase(Locale.ROOT).contains(query))
                        || (u.getEmail() != null && u.getEmail().toLowerCase(Locale.ROOT).contains(query)))
                .sorted(Comparator.comparing(User::getFullName, Comparator.nullsLast(String::compareTo)))
                .map(this::toDto)
                .toList();

        model.addAttribute("currentUser", teamAccessService.requireCurrentUser(auth));
        model.addAttribute("entries", entries);
        model.addAttribute("departments", active.stream()
                .map(User::getDepartment)
                .filter(d -> d != null && !d.isBlank())
                .distinct()
                .sorted()
                .toList());
        model.addAttribute("selectedDept", department);
        model.addAttribute("query", q);
        return "directory/index";
    }

    private DirectoryEntryDto toDto(User user) {
        return new DirectoryEntryDto(
                user.getId(),
                user.getFullName(),
                user.getPosition(),
                user.getDepartment(),
                user.getEmail(),
                user.getManager() != null ? user.getManager().getFullName() : null);
    }
}
