package com.staffsense.demo.controller;

import com.staffsense.demo.model.*;
import com.staffsense.demo.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public String listUsers(@RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "20") int size,
                            @RequestParam(required = false) String role,
                            Model model, Authentication auth) {
        User currentUser = userRepository.findByEmail(auth.getName()).orElseThrow();
        Pageable pageable = PageRequest.of(page, size, Sort.by("fullName"));
        Page<User> userPage;

        if (role != null && !role.isBlank()) {
            Role roleEnum = Role.valueOf(role);
            userPage = userRepository.findByActiveAndRolePaged(true, roleEnum, pageable);
        } else {
            userPage = userRepository.findAll(pageable);
        }

        model.addAttribute("userPage", userPage);
        model.addAttribute("users", userPage.getContent());
        model.addAttribute("selectedRole", role);
        model.addAttribute("currentUser", currentUser);
        return "admin/users";
    }

    @GetMapping("/users/new")
    public String newUserForm(Model model, Authentication auth) {
        User currentUser = userRepository.findByEmail(auth.getName()).orElseThrow();
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("roles", Role.values());
        model.addAttribute("gradeLevels", GradeLevel.values());
        return "admin/user-form";
    }

    @PostMapping("/users/new")
    public String createUser(@RequestParam String fullName,
                             @RequestParam String email,
                             @RequestParam String password,
                             @RequestParam String role,
                             @RequestParam String department,
                             @RequestParam String position,
                             @RequestParam String gradeLevel,
                             @RequestParam String salary,
                             @RequestParam String hireDate,
                             Authentication auth,
                             RedirectAttributes redirectAttrs) {
        userRepository.findByEmail(auth.getName()).orElseThrow();

        User user = User.builder()
                .fullName(fullName)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .role(Role.valueOf(role))
                .department(department)
                .position(position)
                .gradeLevel(GradeLevel.valueOf(gradeLevel))
                .salary(new BigDecimal(salary))
                .hireDate(LocalDate.parse(hireDate))
                .active(true)
                .build();
        userRepository.save(user);

        redirectAttrs.addFlashAttribute("success", "Пользователь '" + fullName + "' успешно создан.");
        return "redirect:/admin";
    }

    @PostMapping("/users/{id}/toggle-active")
    public String toggleActive(@PathVariable Long id,
                               Authentication auth,
                               RedirectAttributes redirectAttrs) {
        User currentUser = userRepository.findByEmail(auth.getName()).orElseThrow();
        User targetUser = userRepository.findById(id).orElseThrow();

        if (currentUser.getId().equals(id) && targetUser.isActive()) {
            redirectAttrs.addFlashAttribute("error", "Вы не можете отключить себя.");
            return "redirect:/admin";
        }

        targetUser.setActive(!targetUser.isActive());
        userRepository.save(targetUser);

        String status = targetUser.isActive() ? "активирован" : "деактивирован";
        redirectAttrs.addFlashAttribute("success", "Пользователь '" + targetUser.getFullName() + "' " + status + ".");
        return "redirect:/admin";
    }
}
