package com.staffsense.demo.controller;

import com.staffsense.demo.model.OneOnOneMeeting;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.OneOnOneMeetingRepository;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.service.OneOnOneService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * FR-019: «Радар выгорания» выводится на управленческом дашборде и не попадает
 * в личный кабинет сотрудника.
 *
 * <p>Тест защищает разметку: блок радара рендерится и в пустом состоянии
 * (когда прогнозов риска ещё нет), а {@code dashboard/personal} для роли
 * EMPLOYEE остаётся без него. Именно в этом месте шаблона ранее терялась
 * переменная {@code theme} — радар падал при отсутствии данных о риске.
 *
 * <p>Фильтры безопасности отключены ({@code addFilters = false}): проверяется
 * рендер, а не правила доступа; в SecurityContext и в запрос кладётся
 * аутентификация, потому что при отключённых фильтрах аргумент
 * {@code Authentication} резолвится из запроса.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OneOnOneService oneOnOneService;

    @Autowired
    private OneOnOneMeetingRepository oneOnOneRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("ADMIN видит «Радар выгорания» на управленческом дашборде")
    void adminSeesBurnoutRadar() throws Exception {
        var auth = authenticateAs("admin@staffsense.com", "ROLE_ADMIN");

        mockMvc.perform(get("/dashboard").principal(auth))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard/main"))
                .andExpect(content().string(containsString("Радар выгорания")))
                .andExpect(content().string(containsString("burnoutRadarChart")));
    }

    @Test
    @DisplayName("PM видит радар в пределах своей области видимости")
    void pmSeesBurnoutRadar() throws Exception {
        User pm = firstActiveWithRole(Role.PM);
        var auth = authenticateAs(pm.getEmail(), "ROLE_PM");

        mockMvc.perform(get("/dashboard").principal(auth))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard/main"))
                .andExpect(content().string(containsString("Радар выгорания")));
    }

    @Test
    @DisplayName("EMPLOYEE попадает в личный кабинет без блока радара")
    void employeeGetsPersonalDashboardWithoutRadar() throws Exception {
        User employee = firstActiveWithRole(Role.EMPLOYEE);
        var auth = authenticateAs(employee.getEmail(), "ROLE_EMPLOYEE");

        mockMvc.perform(get("/dashboard").principal(auth))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard/personal"))
                .andExpect(content().string(not(containsString("Радар выгорания"))))
                .andExpect(content().string(not(containsString("burnoutRadarChart"))));
    }

    @Test
    @DisplayName("Назначенная сотруднику встреча 1:1 видна ему в «Моём пространстве»")
    void assignedMeetingAppearsOnEmployeeDashboard() throws Exception {
        // Встречу назначает руководитель сотрудника (PM), а смотрит её сам сотрудник.
        User employee = userRepository.findAll().stream()
                .filter(u -> u.isActive() && u.getRole() == Role.EMPLOYEE && u.getManager() != null)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("В тестовых данных нет сотрудника с руководителем"));
        User manager = employee.getManager();
        var auth = authenticateAs(employee.getEmail(), "ROLE_EMPLOYEE");

        OneOnOneMeeting meeting = oneOnOneService.create(
                employee.getId(), manager, LocalDateTime.now().plusDays(3).withNano(0));

        try {
            mockMvc.perform(get("/dashboard").principal(auth))
                    .andExpect(status().isOk())
                    .andExpect(view().name("dashboard/personal"))
                    .andExpect(content().string(containsString("Мои 1:1 встречи")))
                    // в строке встречи выводятся руководитель и статус
                    .andExpect(content().string(containsString(manager.getFullName())))
                    .andExpect(content().string(containsString("PLANNED")));
        } finally {
            oneOnOneRepository.deleteById(meeting.getId());
        }
    }

    private UsernamePasswordAuthenticationToken authenticateAs(String email, String authority) {
        var authentication = new UsernamePasswordAuthenticationToken(email, "n/a",
                List.of(new SimpleGrantedAuthority(authority)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return authentication;
    }

    private User firstActiveWithRole(Role role) {
        return userRepository.findAll().stream()
                .filter(u -> u.isActive() && u.getRole() == role)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("В тестовых данных нет активного пользователя с ролью " + role));
    }
}
