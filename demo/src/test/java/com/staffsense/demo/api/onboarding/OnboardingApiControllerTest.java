package com.staffsense.demo.api.onboarding;

import com.staffsense.demo.model.GradeLevel;
import com.staffsense.demo.model.OnboardingArticle;
import com.staffsense.demo.model.OnboardingSection;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.OnboardingArticleRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Контракт портала онбординга.
 *
 * <p>Ключевая проверка — черновик не отдаётся рядовому сотруднику даже по
 * прямой ссылке: портал читают все, готовят его HR.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OnboardingApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OnboardingArticleRepository articleRepository;

    @Autowired
    private UserRepository userRepository;

    private OnboardingArticle draft;

    @BeforeEach
    void setUp() {
        userRepository.findByEmail("onb.hr@test.local").orElseGet(
                () -> userRepository.save(user("onb.hr@test.local", "HR Портал", Role.HR)));
        userRepository.findByEmail("onb.emp@test.local").orElseGet(
                () -> userRepository.save(user("onb.emp@test.local", "Новичок", Role.EMPLOYEE)));

        draft = articleRepository.save(OnboardingArticle.builder()
                .section(OnboardingSection.POLICIES)
                .title("Черновик регламента")
                .content("Текст, который ещё не опубликован")
                .published(false)
                .orderIndex(99)
                .updatedAt(LocalDateTime.now())
                .build());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Разделы отдаются со счётчиками статей")
    void sectionsWithCounts() throws Exception {
        authenticate("onb.emp@test.local", "ROLE_EMPLOYEE");

        mockMvc.perform(get("/api/v1/onboarding/sections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].code").isNotEmpty())
                .andExpect(jsonPath("$[0].articleCount").exists());
    }

    @Test
    @DisplayName("Черновик не открывается рядовому сотруднику")
    void draftHiddenFromEmployee() throws Exception {
        authenticate("onb.emp@test.local", "ROLE_EMPLOYEE");

        mockMvc.perform(get("/api/v1/onboarding/articles/" + draft.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("HR видит черновик")
    void draftVisibleForHr() throws Exception {
        authenticate("onb.hr@test.local", "ROLE_HR");

        mockMvc.perform(get("/api/v1/onboarding/articles/" + draft.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Черновик регламента"));
    }

    @Test
    @DisplayName("Чек-лист отдаётся с прогрессом")
    void checklistWithProgress() throws Exception {
        authenticate("onb.emp@test.local", "ROLE_EMPLOYEE");

        mockMvc.perform(get("/api/v1/onboarding/checklist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").exists())
                .andExpect(jsonPath("$.percent").exists())
                .andExpect(jsonPath("$.items").isArray());
    }

    private void authenticate(String email, String authority) {
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
                .hireDate(LocalDate.of(2023, 6, 1))
                .active(true)
                .build();
    }
}
