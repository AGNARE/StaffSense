package com.staffsense.demo.api.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Контракт управления каталогом ИИ-скиллов (FR-033).
 *
 * <p>Проверяем обещания, которые нельзя увидеть в коде контроллера: каталог
 * наполняют ADMIN и HR, рядовой сотрудник получает 403 на любую мутацию,
 * публикация меняет видимость скилла для сотрудника, удаление убирает его из
 * каталога, а правка без нового файла не теряет прежний.
 *
 * <p><b>Авторизация — постпроцессором {@code user(...)} на каждый запрос, а не
 * через {@code SecurityContextHolder}.</b> В этом приложении контекст первого
 * запроса сохраняется в сессии, и следующий запрос продолжает видеть прежнюю
 * роль — тест «HR публикует, затем проверяем глазами сотрудника» на общем
 * {@code SecurityContextHolder} молча проверял бы роль HR. Постпроцессор
 * задаёт субъекта именно для запроса, поэтому смена роли внутри теста честная.
 *
 * <p>Тело запроса — {@code multipart/form-data}: файл и метаданные едут одним
 * запросом, без base64. CSRF передаётся так же, как его передал бы клиент
 * заголовком {@code X-XSRF-TOKEN}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SkillManagerApiControllerTest {

    private static final String FILE_TEXT = "Ты — старший инженер. Проверь diff по пунктам.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AiSkillRepository skillRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    /** Что создали тесты: убираем за собой, контекст Spring общий для классов. */
    private final List<Long> created = new ArrayList<>();

    @BeforeEach
    void setUp() {
        userRepository.findByEmail("skillmgr.admin@test.local").orElseGet(
                () -> userRepository.save(testUser("skillmgr.admin@test.local", "Админ Каталога", Role.ADMIN)));
        userRepository.findByEmail("skillmgr.hr@test.local").orElseGet(
                () -> userRepository.save(testUser("skillmgr.hr@test.local", "HR Каталога", Role.HR)));
        userRepository.findByEmail("skillmgr.emp@test.local").orElseGet(
                () -> userRepository.save(testUser("skillmgr.emp@test.local", "Сотрудник Каталога", Role.EMPLOYEE)));
    }

    @AfterEach
    void tearDown() {
        created.forEach(skillRepository::deleteById);
        created.clear();
    }

    @Test
    @DisplayName("ADMIN создаёт скилл — он сразу виден в каталоге")
    void adminCreatesSkillVisibleInCatalog() throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/v1/skills")
                        .file(file("code-review-checklist.md"))
                        .param("name", "Code Review Checklist")
                        .param("description", "Проверяет PR по 14 пунктам")
                        .param("targetRole", "BACKEND")
                        .param("githubUrl", "https://github.com/staffsense/skills")
                        .with(csrf())
                        .with(admin()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Code Review Checklist"))
                .andExpect(jsonPath("$.targetRole").value("BACKEND"))
                .andExpect(jsonPath("$.targetRoleTitle").value("Бэкенд-разработчик"))
                .andExpect(jsonPath("$.fileName").value("code-review-checklist.md"))
                .andExpect(jsonPath("$.published").value(true))
                .andReturn();

        long id = idOf(result);

        // Сотрудник видит созданный скилл в каталоге и открывает карточку.
        mockMvc.perform(get("/api/v1/skills").with(employee()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name").value(hasItem("Code Review Checklist")));
        mockMvc.perform(get("/api/v1/skills/" + id).with(employee()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(true));
    }

    @Test
    @DisplayName("HR публикует черновик — скилл появляется у сотрудника")
    void hrPublishesDraft() throws Exception {
        AiSkill draft = draft("Черновик HR", SkillRole.QA);
        created.add(draft.getId());

        // Пока черновик — сотруднику карточка не открывается.
        mockMvc.perform(get("/api/v1/skills/" + draft.getId()).with(employee()))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/skills/" + draft.getId() + "/publish")
                        .with(csrf())
                        .with(hr()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(draft.getId().intValue()))
                .andExpect(jsonPath("$.published").value(true))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/skills/" + draft.getId()).with(employee()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Черновик HR"));
    }

    @Test
    @DisplayName("HR снимает скилл с публикации — сотрудник его больше не видит")
    void hrUnpublishes() throws Exception {
        AiSkill published = published("Опубликованный", SkillRole.ANALYST);
        created.add(published.getId());

        mockMvc.perform(post("/api/v1/skills/" + published.getId() + "/publish")
                        .param("published", "false")
                        .with(csrf())
                        .with(hr()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(false));

        mockMvc.perform(get("/api/v1/skills/" + published.getId()).with(employee()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("EMPLOYEE не может создать скилл — 403")
    void employeeCannotCreate() throws Exception {
        mockMvc.perform(multipart("/api/v1/skills")
                        .param("name", "Самозваный скилл")
                        .param("targetRole", "QA")
                        .with(csrf())
                        .with(employee()))
                .andExpect(status().isForbidden());

        assertThat(skillRepository.findAll())
                .extracting(AiSkill::getName)
                .doesNotContain("Самозваный скилл");
    }

    @Test
    @DisplayName("EMPLOYEE не может удалить или изменить скилл — 403")
    void employeeCannotMutate() throws Exception {
        AiSkill skill = published("Неприкосновенный", SkillRole.PM);
        created.add(skill.getId());

        MockMultipartHttpServletRequestBuilder put = multipart("/api/v1/skills/" + skill.getId());
        put.with(request -> {
            request.setMethod("PUT");
            return request;
        });
        mockMvc.perform(put.param("name", "Переименованный").with(csrf()).with(employee()))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/skills/" + skill.getId()).with(csrf()).with(employee()))
                .andExpect(status().isForbidden());

        assertThat(skillRepository.findById(skill.getId()).orElseThrow().getName())
                .isEqualTo("Неприкосновенный");
    }

    @Test
    @DisplayName("HR правит поля, не загружая файл заново — файл сохраняется")
    void updateKeepsExistingFile() throws Exception {
        long id = createWithFile("Скилл до правки");

        MockMultipartHttpServletRequestBuilder put = multipart("/api/v1/skills/" + id);
        put.with(request -> {
            request.setMethod("PUT");
            return request;
        });
        mockMvc.perform(put
                        .param("name", "Скилл после правки")
                        .param("description", "Новое описание")
                        .param("targetRole", "QA")
                        .with(csrf())
                        .with(hr()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Скилл после правки"))
                .andExpect(jsonPath("$.description").value("Новое описание"))
                .andExpect(jsonPath("$.targetRole").value("QA"))
                // файл не загружали — прежний на месте
                .andExpect(jsonPath("$.fileName").value("skill.md"))
                .andExpect(jsonPath("$.published").value(true));

        mockMvc.perform(get("/api/v1/skills/" + id + "/file").with(hr()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Удаление убирает скилл из каталога")
    void deleteRemovesFromCatalog() throws Exception {
        long id = createWithFile("Скилл под удаление");

        mockMvc.perform(delete("/api/v1/skills/" + id).with(csrf()).with(admin()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/skills").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name").value(not(hasItem("Скилл под удаление"))));
        mockMvc.perform(get("/api/v1/skills/" + id).with(admin()))
                .andExpect(status().isNotFound());

        assertThat(skillRepository.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("Без названия и роли — 400 с понятным текстом, а не 500")
    void validationIsBadRequest() throws Exception {
        mockMvc.perform(multipart("/api/v1/skills")
                        .param("targetRole", "QA")
                        .with(csrf())
                        .with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Укажите название скилла"));

        mockMvc.perform(multipart("/api/v1/skills")
                        .param("name", "Скилл без роли")
                        .with(csrf())
                        .with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Выберите роль, для которой предназначен скилл"));

        mockMvc.perform(multipart("/api/v1/skills")
                        .param("name", "Скилл с кривой ролью")
                        .param("targetRole", "PILOT")
                        .with(csrf())
                        .with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Неизвестная роль: PILOT"));
    }

    @Test
    @DisplayName("Справочник ролей для формы приходит с сервера")
    void rolesAvailableForForm() throws Exception {
        mockMvc.perform(get("/api/v1/skills/roles").with(hr()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.value=='DESIGNER')].title").value(hasItem("Дизайнер")))
                .andExpect(jsonPath("$[?(@.value=='OTHER')].title").value(hasItem("Другое")));
    }

    // ── вспомогательное ──────────────────────────────────────────────────

    private RequestPostProcessor admin() {
        return user("skillmgr.admin@test.local").roles("ADMIN");
    }

    private RequestPostProcessor hr() {
        return user("skillmgr.hr@test.local").roles("HR");
    }

    private RequestPostProcessor employee() {
        return user("skillmgr.emp@test.local").roles("EMPLOYEE");
    }

    private MockMultipartFile file(String fileName) {
        return new MockMultipartFile("file", fileName, "text/markdown",
                FILE_TEXT.getBytes(StandardCharsets.UTF_8));
    }

    private long createWithFile(String name) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/v1/skills")
                        .file(file("skill.md"))
                        .param("name", name)
                        .param("description", "Описание до правки")
                        .param("targetRole", "BACKEND")
                        .with(csrf())
                        .with(admin()))
                .andExpect(status().isCreated())
                .andReturn();
        return idOf(result);
    }

    private long idOf(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        long id = objectMapper.readTree(body).get("id").asLong();
        created.add(id);
        return id;
    }

    private AiSkill draft(String name, SkillRole role) {
        return skillRepository.save(AiSkill.builder()
                .name(name)
                .description("Ещё не опубликован")
                .targetRole(role)
                .fileName("draft.md")
                .contentType("text/markdown")
                .fileData("черновик".getBytes(StandardCharsets.UTF_8))
                .published(false)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private AiSkill published(String name, SkillRole role) {
        return skillRepository.save(AiSkill.builder()
                .name(name)
                .description("Уже в каталоге")
                .targetRole(role)
                .published(true)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private User testUser(String email, String fullName, Role role) {
        return User.builder()
                .fullName(fullName)
                .email(email)
                .passwordHash("hash")
                .role(role)
                .department("System")
                .position("Specialist")
                .salary(new BigDecimal("90000"))
                .gradeLevel(GradeLevel.SENIOR)
                .weeklyCapacityHours(40)
                .hireDate(LocalDate.of(2022, 3, 1))
                .active(true)
                .build();
    }
}
