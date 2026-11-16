package com.staffsense.demo.api.skill;

import com.staffsense.demo.model.AiSkill;
import com.staffsense.demo.model.GradeLevel;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.SkillRole;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.AiSkillRepository;
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
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Контракт каталога ИИ-скиллов.
 *
 * <p>Проверяем два обещания: содержимое файла не попадает в JSON (иначе
 * каталог тянул бы мегабайты) и черновик не открывается рядовому сотруднику.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SkillApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AiSkillRepository skillRepository;

    @Autowired
    private UserRepository userRepository;

    private AiSkill draft;

    @BeforeEach
    void setUp() {
        userRepository.findByEmail("skill.emp@test.local").orElseGet(
                () -> userRepository.save(user("skill.emp@test.local", "Сотрудник Каталога", Role.EMPLOYEE)));
        userRepository.findByEmail("skill.hr@test.local").orElseGet(
                () -> userRepository.save(user("skill.hr@test.local", "HR Каталога", Role.HR)));

        draft = skillRepository.save(AiSkill.builder()
                .name("Черновик скилла")
                .description("Ещё не опубликован")
                .targetRole(SkillRole.QA)
                .fileName("draft.md")
                .contentType("text/markdown")
                .fileData("содержимое".getBytes())
                .published(false)
                .createdAt(LocalDateTime.now())
                .build());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Список не содержит содержимого файлов")
    void listHasNoFileContent() throws Exception {
        authenticate("skill.emp@test.local", "ROLE_EMPLOYEE");

        mockMvc.perform(get("/api/v1/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                // содержимое файла в контракт не попадает
                .andExpect(jsonPath("$[0].fileData").doesNotExist());
    }

    @Test
    @DisplayName("Черновик скилла скрыт от сотрудника")
    void draftHiddenFromEmployee() throws Exception {
        authenticate("skill.emp@test.local", "ROLE_EMPLOYEE");

        mockMvc.perform(get("/api/v1/skills/" + draft.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Черновик скилла виден HR")
    void draftVisibleForHr() throws Exception {
        authenticate("skill.hr@test.local", "ROLE_HR");

        mockMvc.perform(get("/api/v1/skills/" + draft.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Черновик скилла"));
    }

    private void authenticate(String email, String authority) {
        SecurityContextHolder.clearContext();
        var token = new UsernamePasswordAuthenticationToken(
                email, "n/a", List.of(new SimpleGrantedAuthority(authority)));
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    private User user(String email, String fullName, Role role) {
        return User.builder()
                .fullName(fullName)
                .email(email)
                .passwordHash("hash")
                .role(role)
                .department("HR")
                .position("Specialist")
                .salary(new BigDecimal("80000"))
                .gradeLevel(GradeLevel.MIDDLE)
                .weeklyCapacityHours(40)
                .hireDate(LocalDate.of(2023, 7, 1))
                .active(true)
                .build();
    }
}
