package com.staffsense.demo.controller;

import com.staffsense.demo.model.Project;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.ProjectRepository;
import com.staffsense.demo.repository.UserRepository;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * FR-007 «Проекты: список и создание» — валидация обязательного PM-владельца.
 *
 * <p>До правки форма отправляла {@code ownerPmId} без {@code required}, а
 * контроллер молча оставлял {@code ownerPm == null}. Миграция V1 объявляет
 * {@code owner_pm_id NOT NULL}, поэтому вместо сообщения у поля пользователь
 * получал страницу 409 «Конфликт данных» (нарушение ERR-006).
 *
 * <p>Фильтры безопасности отключены ({@code addFilters = false}): тест
 * проверяет валидацию и рендер формы, а не правила доступа. Методная
 * безопасность ({@code @PreAuthorize}) при этом работает, поэтому в
 * SecurityContext кладётся администратор.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class ProjectControllerTest {

    /** Общий префикс: по нему чистим созданные проекты, чтобы не влиять на другие тесты. */
    private static final String TEST_PROJECT_PREFIX = "FR-007 тестовый проект";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        projectRepository.findAll().stream()
                .filter(p -> p.getName() != null && p.getName().startsWith(TEST_PROJECT_PREFIX))
                .forEach(projectRepository::delete);
    }

    @Test
    @DisplayName("С активным PM проект сохраняется и уходит редирект на список")
    void createsProjectWithActivePm() throws Exception {
        User pm = firstActivePm();
        String name = TEST_PROJECT_PREFIX + " с владельцем";
        var auth = authenticateAsAdmin();

        mockMvc.perform(post("/projects/new")
                        .principal(auth)
                        .param("name", name)
                        .param("description", "Описание")
                        .param("ownerPm", String.valueOf(pm.getId())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects"));

        Project saved = findByName(name);
        assertThat(saved).as("проект должен быть сохранён").isNotNull();
        assertThat(saved.getOwnerPm()).as("владелец должен быть записан").isNotNull();
        assertThat(saved.getOwnerPm().getId()).isEqualTo(pm.getId());
    }

    @Test
    @DisplayName("Без владельца — возврат на форму с сообщением у поля, проект не сохраняется")
    void missingOwnerReturnsFormWithoutSaving() throws Exception {
        User pm = firstActivePm();
        String name = TEST_PROJECT_PREFIX + " без владельца";
        var auth = authenticateAsAdmin();
        long projectsBefore = projectRepository.count();

        mockMvc.perform(post("/projects/new")
                        .principal(auth)
                        .param("name", name)
                        .param("ownerPm", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("projects/new"))
                .andExpect(model().attributeHasFieldErrors("project", "ownerPm"))
                .andExpect(content().string(containsString("Выберите владельца проекта")))
                // ERR-006: общее сообщение формы
                .andExpect(content().string(containsString("Проверьте введённые данные")))
                // форма перерисована со списком активных PM и сохранённым названием
                .andExpect(content().string(containsString(pm.getFullName())))
                .andExpect(content().string(containsString(name)));

        assertThat(projectRepository.count()).as("проект не должен сохраняться").isEqualTo(projectsBefore);
    }

    @Test
    @DisplayName("Владелец не PM (админ) — возврат на форму, проект не сохраняется")
    void nonPmOwnerReturnsForm() throws Exception {
        User admin = userRepository.findByEmail("admin@staffsense.com").orElseThrow();
        String name = TEST_PROJECT_PREFIX + " с админом";
        var auth = authenticateAsAdmin();
        long projectsBefore = projectRepository.count();

        mockMvc.perform(post("/projects/new")
                        .principal(auth)
                        .param("name", name)
                        .param("ownerPm", String.valueOf(admin.getId())))
                .andExpect(status().isOk())
                .andExpect(view().name("projects/new"))
                .andExpect(model().attributeHasFieldErrors("project", "ownerPm"))
                .andExpect(content().string(containsString("только активный PM")));

        assertThat(projectRepository.count()).as("проект не должен сохраняться").isEqualTo(projectsBefore);
    }

    @Test
    @DisplayName("Несуществующий id владельца — возврат на форму, проект не сохраняется")
    void unknownOwnerReturnsForm() throws Exception {
        String name = TEST_PROJECT_PREFIX + " с неизвестным PM";
        var auth = authenticateAsAdmin();
        long projectsBefore = projectRepository.count();

        mockMvc.perform(post("/projects/new")
                        .principal(auth)
                        .param("name", name)
                        .param("ownerPm", "999999"))
                .andExpect(status().isOk())
                .andExpect(view().name("projects/new"))
                .andExpect(model().attributeHasFieldErrors("project", "ownerPm"));

        assertThat(projectRepository.count()).as("проект не должен сохраняться").isEqualTo(projectsBefore);
    }

    @Test
    @DisplayName("Пустое название — возврат на форму, проект не сохраняется")
    void blankNameReturnsForm() throws Exception {
        User pm = firstActivePm();
        var auth = authenticateAsAdmin();
        long projectsBefore = projectRepository.count();

        mockMvc.perform(post("/projects/new")
                        .principal(auth)
                        .param("name", "   ")
                        .param("ownerPm", String.valueOf(pm.getId())))
                .andExpect(status().isOk())
                .andExpect(view().name("projects/new"))
                .andExpect(model().attributeHasFieldErrors("project", "name"));

        assertThat(projectRepository.count()).as("проект не должен сохраняться").isEqualTo(projectsBefore);
    }

    @Test
    @DisplayName("Форма создания открывается с обязательным полем PM и списком активных PM")
    void createFormMarksOwnerRequired() throws Exception {
        User pm = firstActivePm();
        var auth = authenticateAsAdmin();

        mockMvc.perform(get("/projects/new").principal(auth))
                .andExpect(status().isOk())
                .andExpect(view().name("projects/new"))
                .andExpect(content().string(containsString("Project Manager (PM) *")))
                .andExpect(content().string(containsString("name=\"ownerPm\"")))
                .andExpect(content().string(containsString(pm.getFullName())));
    }

    /**
     * Кладёт администратора и в SecurityContext (для {@code @PreAuthorize}), и в
     * запрос как principal: при {@code addFilters = false} резолвер аргумента
     * {@code Authentication} берёт его из запроса, а не из SecurityContext.
     */
    private UsernamePasswordAuthenticationToken authenticateAsAdmin() {
        User admin = userRepository.findByEmail("admin@staffsense.com").orElseThrow();
        var authentication = new UsernamePasswordAuthenticationToken(admin.getEmail(), "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return authentication;
    }

    private User firstActivePm() {
        return userRepository.findAll().stream()
                .filter(u -> u.isActive() && u.getRole() == Role.PM)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("В тестовых данных нет активного PM"));
    }

    private Project findByName(String name) {
        return projectRepository.findAll().stream()
                .filter(p -> name.equals(p.getName()))
                .findFirst()
                .orElse(null);
    }
}
