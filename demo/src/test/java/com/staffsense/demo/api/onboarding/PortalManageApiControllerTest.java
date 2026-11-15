package com.staffsense.demo.api.onboarding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Контракт «Управления порталом».
 *
 * <p>Проверяем двухшаговый сценарий экрана: статья создаётся черновиком,
 * правится без потери статуса и публикуется отдельным действием. Рядовой
 * сотрудник не создаёт статьи и не видит список управления с черновиками.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PortalManageApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OnboardingArticleRepository articleRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.findByEmail("portal.hr@test.local").orElseGet(
                () -> userRepository.save(user("portal.hr@test.local", "Кадровик Портала", Role.HR)));
        userRepository.findByEmail("portal.emp@test.local").orElseGet(
                () -> userRepository.save(user("portal.emp@test.local", "Сотрудник Портала", Role.EMPLOYEE)));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── создание и публикация ────────────────────────────────────────────

    @Test
    @DisplayName("Статья создаётся черновиком — 201, публикация включает её в эфир")
    void articleCreatedAndPublished() throws Exception {
        authenticate("portal.hr@test.local", "ROLE_HR");

        String body = """
                {
                  "section": "POLICIES",
                  "title": "Как проходит код-ревью",
                  "content": "Ревью обязано пройти до слияния в основную ветку.",
                  "orderIndex": 3
                }
                """;

        MvcResult created = mockMvc.perform(post("/api/v1/onboarding/articles")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.section").value("POLICIES"))
                .andExpect(jsonPath("$.sectionTitle").value("Регламенты и политики"))
                .andExpect(jsonPath("$.title").value("Как проходит код-ревью"))
                .andExpect(jsonPath("$.orderIndex").value(3))
                .andExpect(jsonPath("$.published").value(false))
                .andExpect(jsonPath("$.statusTitle").value("Черновик"))
                .andReturn();

        long id = idOf(created);

        OnboardingArticle stored = articleRepository.findById(id).orElseThrow();
        assertFalse(stored.isPublished(), "новая статья не уходит сотрудникам сразу");
        assertEquals(OnboardingSection.POLICIES, stored.getSection());
        assertEquals(userRepository.findByEmail("portal.hr@test.local").orElseThrow().getId(),
                stored.getUpdatedBy().getId(), "автор правки — тот, кто создал статью");

        mockMvc.perform(post("/api/v1/onboarding/articles/" + id + "/publish").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(true))
                .andExpect(jsonPath("$.statusTitle").value("Опубликована"));

        assertTrue(articleRepository.findById(id).orElseThrow().isPublished(),
                "публикация сохранена в базе");

        // Опубликованная статья видна сотруднику в обычном чтении портала.
        authenticate("portal.emp@test.local", "ROLE_EMPLOYEE");
        mockMvc.perform(get("/api/v1/onboarding/articles/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Как проходит код-ревью"));
    }

    @Test
    @DisplayName("Правка статьи не снимает её с публикации")
    void updateKeepsPublicationState() throws Exception {
        authenticate("portal.hr@test.local", "ROLE_HR");

        OnboardingArticle published = articleRepository.save(OnboardingArticle.builder()
                .section(OnboardingSection.ABOUT)
                .title("Ценности компании")
                .content("Черновик ценностей")
                .orderIndex(1)
                .published(true)
                .build());

        String body = """
                {
                  "section": "ABOUT",
                  "title": "Ценности компании (обновлено)",
                  "content": "Обновлённый текст ценностей.",
                  "orderIndex": 2
                }
                """;

        mockMvc.perform(put("/api/v1/onboarding/articles/" + published.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Ценности компании (обновлено)"))
                .andExpect(jsonPath("$.orderIndex").value(2))
                // признак публикации не передан — статья остаётся опубликованной
                .andExpect(jsonPath("$.published").value(true));

        assertTrue(articleRepository.findById(published.getId()).orElseThrow().isPublished());
    }

    // ── права ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("EMPLOYEE не может создать статью — 403")
    void employeeCannotCreateArticle() throws Exception {
        authenticate("portal.emp@test.local", "ROLE_EMPLOYEE");

        String body = """
                {"section": "FAQ", "title": "Статья от сотрудника", "content": "Текст"}
                """;

        mockMvc.perform(post("/api/v1/onboarding/articles")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Список управления с черновиками закрыт от сотрудника — 403")
    void manageListForbiddenForEmployee() throws Exception {
        authenticate("portal.emp@test.local", "ROLE_EMPLOYEE");

        mockMvc.perform(get("/api/v1/onboarding/manage/articles"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Список управления показывает черновики и счётчики разделов")
    void manageListContainsDrafts() throws Exception {
        authenticate("portal.hr@test.local", "ROLE_HR");

        OnboardingArticle draft = articleRepository.save(OnboardingArticle.builder()
                .section(OnboardingSection.CHECKLIST)
                .title("Чек-лист новичка (черновик управления)")
                .orderIndex(1)
                .published(false)
                .build());

        mockMvc.perform(get("/api/v1/onboarding/manage/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalArticles").isNumber())
                .andExpect(jsonPath("$.draftArticles").isNumber())
                .andExpect(jsonPath("$.sections").isArray())
                .andExpect(jsonPath("$.articles[?(@.id == " + draft.getId() + ")].published")
                        .value(org.hamcrest.Matchers.hasItem(false)));
    }

    // ── вспомогательное ──────────────────────────────────────────────────

    private long idOf(MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asLong();
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
                .salary(new BigDecimal("90000"))
                .gradeLevel(GradeLevel.MIDDLE)
                .weeklyCapacityHours(40)
                .hireDate(LocalDate.of(2023, 3, 1))
                .active(true)
                .build();
    }
}
