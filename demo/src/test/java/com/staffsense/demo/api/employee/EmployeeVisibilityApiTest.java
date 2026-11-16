package com.staffsense.demo.api.employee;

import com.staffsense.demo.model.GradeLevel;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Видимость карточки сотрудника.
 *
 * <p>Сотрудник открывает свою карточку, чужую — нет; список остаётся
 * управленческим, потому что там нет построчной проверки.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmployeeVisibilityApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private User employee;
    private User colleague;

    @BeforeEach
    void setUp() {
        employee = userRepository.findByEmail("vis.emp@test.local")
                .orElseGet(() -> userRepository.save(user("vis.emp@test.local", "Видимость Сотрудник", Role.EMPLOYEE, "Design")));
        colleague = userRepository.findByEmail("vis.other@test.local")
                .orElseGet(() -> userRepository.save(user("vis.other@test.local", "Коллега Сотрудник", Role.EMPLOYEE, "Sales")));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Сотрудник открывает свою карточку")
    void employeeSeesSelf() throws Exception {
        authenticate("vis.emp@test.local", "ROLE_EMPLOYEE");

        mockMvc.perform(get("/api/v1/employees/" + employee.getId()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Чужую карточку сотрудник не видит")
    void employeeCannotSeeColleague() throws Exception {
        authenticate("vis.emp@test.local", "ROLE_EMPLOYEE");

        mockMvc.perform(get("/api/v1/employees/" + colleague.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Список сотрудников сотруднику недоступен")
    void employeeCannotList() throws Exception {
        authenticate("vis.emp@test.local", "ROLE_EMPLOYEE");

        mockMvc.perform(get("/api/v1/employees"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("HR список видит")
    void hrCanList() throws Exception {
        userRepository.findByEmail("vis.hr@test.local")
                .orElseGet(() -> userRepository.save(user("vis.hr@test.local", "Видимость HR", Role.HR, "HR")));
        authenticate("vis.hr@test.local", "ROLE_HR");

        mockMvc.perform(get("/api/v1/employees"))
                .andExpect(status().isOk());
    }

    private void authenticate(String email, String authority) {
        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a",
                        List.of(new SimpleGrantedAuthority(authority))));
    }

    private User user(String email, String fullName, Role role, String department) {
        return User.builder()
                .fullName(fullName)
                .email(email)
                .passwordHash("hash")
                .role(role)
                .department(department)
                .position("Specialist")
                .salary(new BigDecimal("80000"))
                .gradeLevel(GradeLevel.MIDDLE)
                .weeklyCapacityHours(40)
                .hireDate(LocalDate.of(2024, 1, 15))
                .active(true)
                .build();
    }
}
