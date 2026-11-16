package com.staffsense.demo.journey;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Сквозной сценарий документов: регистрация → согласование → входящие → история.
 *
 * <p>Проверяем путь документа целиком:
 * <ol>
 *   <li>HR регистрирует документ — он появляется в реестре черновиком;</li>
 *   <li>HR отправляет его на согласование — документ оказывается во входящих,
 *       а каждый шаг остаётся отдельной записью истории с актором и
 *       комментарием (кто и почему сдвинул документ);</li>
 *   <li>PM видит только документы своей команды: чужой приказ не появляется
 *       ни в реестре, ни во входящих, а по прямому id карточка — 403.</li>
 * </ol>
 *
 * <p>Тест транзакционный: документы, история и пользователи откатываются,
 * поэтому не мешают другим проверкам реестра.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DocumentJourneyTest {

    private static final String HR_EMAIL = "journey.document.hr@test.local";
    private static final String PM_EMAIL = "journey.document.pm@test.local";
    private static final String OTHER_PM_EMAIL = "journey.document.other.pm@test.local";
    private static final String EMPLOYEE_EMAIL = "journey.document.employee@test.local";
    private static final String FOREIGN_EMPLOYEE_EMAIL = "journey.document.foreign@test.local";

    private static final String DEPARTMENT = "Отдел документных путешествий";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private User hr;
    private User pm;
    private User otherPm;
    private User employee;
    private User foreignEmployee;

    @BeforeEach
    void setUp() {
        hr = ensureUser(HR_EMAIL, "Кадровик Документный", Role.HR, null, "Кадровая служба");
        pm = ensureUser(PM_EMAIL, "Руководитель Документный", Role.PM, null, "Руководство");
        otherPm = ensureUser(OTHER_PM_EMAIL, "Чужой Руководитель Документный", Role.PM, null, "Руководство");
        employee = ensureUser(EMPLOYEE_EMAIL, "Сотрудник Документный", Role.EMPLOYEE, pm, DEPARTMENT);
        foreignEmployee = ensureUser(FOREIGN_EMPLOYEE_EMAIL, "Сотрудник Документный Соседний",
                Role.EMPLOYEE, otherPm, "Соседний отдел");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TestSecurityContextHolder.clearContext();
    }

    // ── Сценарий: документ уходит на согласование и пишет историю ────────

    @Test
    @DisplayName("HR создаёт документ, отправляет в согласование — он во входящих, шаг в истории")
    void createdDocumentGoesToInboxAndWritesHistory() throws Exception {
        authenticate(HR_EMAIL, "ROLE_HR");

        // 1. Регистрация: номер и срок хранения считает сервер, статус — черновик.
        MvcResult created = mockMvc.perform(post("/api/v1/documents")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"ORDER","title":"Приказ о путешествии",
                                 "summary":"Регистрация документа для сквозного сценария",
                                 "employeeId":%d,"documentDate":"2026-11-02"}
                                """.formatted(employee.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("ORDER"))
                .andExpect(jsonPath("$.title").value("Приказ о путешествии"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.statusTitle").value("Черновик"))
                .andExpect(jsonPath("$.regNumber").isNotEmpty())
                .andExpect(jsonPath("$.employeeId").value(employee.getId()))
                .andExpect(jsonPath("$.authorName").value("Кадровик Документный"))
                .andReturn();

        long documentId = idOf(created);

        // 2. Смена статуса по маршруту: DRAFT → ON_APPROVAL.
        mockMvc.perform(post("/api/v1/documents/" + documentId + "/status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"ON_APPROVAL","comment":"Отправляю на согласование"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ON_APPROVAL"))
                .andExpect(jsonPath("$.statusTitle").value("На согласовании"))
                // Запись о регистрации + запись о переходе — обе в истории.
                .andExpect(jsonPath("$.history.length()").value(2))
                .andExpect(jsonPath("$.history[0].toStatus").value("DRAFT"))
                .andExpect(jsonPath("$.history[0].actorName").value("Кадровик Документный"))
                .andExpect(jsonPath("$.history[1].fromStatus").value("DRAFT"))
                .andExpect(jsonPath("$.history[1].toStatus").value("ON_APPROVAL"))
                .andExpect(jsonPath("$.history[1].actorName").value("Кадровик Документный"))
                .andExpect(jsonPath("$.history[1].comment").value("Отправляю на согласование"));

        // 3. Последствие для маршрута: документ ждёт решения во входящих HR.
        mockMvc.perform(get("/api/v1/documents/inbox"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem((int) documentId)))
                .andExpect(jsonPath("$[?(@.id==" + documentId + ")].status")
                        .value(hasItem("ON_APPROVAL")));

        // 4. И карточка при повторном открытии показывает ту же историю.
        mockMvc.perform(get("/api/v1/documents/" + documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ON_APPROVAL"))
                .andExpect(jsonPath("$.history.length()").value(2))
                .andExpect(jsonPath("$.history[1].toStatus").value("ON_APPROVAL"));
    }

    // ── Сценарий: PM не видит документы чужой команды ────────────────────

    @Test
    @DisplayName("PM видит документы своей команды, но не чужой — 403 на чужую карточку")
    void pmSeesOnlyTeamDocuments() throws Exception {
        // Два документа, оба на согласовании: свой и соседний.
        long teamDocumentId = createApprovalDocument("Приказ по своему сотруднику", employee.getId());
        long foreignDocumentId = createApprovalDocument("Приказ по соседнему сотруднику",
                foreignEmployee.getId());

        authenticate(PM_EMAIL, "ROLE_PM");

        // Входящие: свой — есть, чужой — нет.
        mockMvc.perform(get("/api/v1/documents/inbox"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem((int) teamDocumentId)))
                .andExpect(jsonPath("$[*].id").value(not(hasItem((int) foreignDocumentId))));

        // Реестр — такая же точка доступа, а не только входящие.
        mockMvc.perform(get("/api/v1/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem((int) teamDocumentId)))
                .andExpect(jsonPath("$[*].id").value(not(hasItem((int) foreignDocumentId))));

        // Чужую карточку PM не открывает даже по прямому id.
        mockMvc.perform(get("/api/v1/documents/" + foreignDocumentId))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/documents/" + teamDocumentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeName").value("Сотрудник Документный"));
    }

    // ── вспомогательное ──────────────────────────────────────────────────

    /** Регистрирует документ и сразу переводит его в «на согласовании». */
    private long createApprovalDocument(String title, Long employeeId) throws Exception {
        authenticate(HR_EMAIL, "ROLE_HR");

        MvcResult created = mockMvc.perform(post("/api/v1/documents")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"ORDER","title":"%s","employeeId":%d,
                                 "documentDate":"2026-11-03"}
                                """.formatted(title, employeeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();

        long id = idOf(created);

        mockMvc.perform(post("/api/v1/documents/" + id + "/status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ON_APPROVAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ON_APPROVAL"));

        return id;
    }

    private long idOf(MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asLong();
    }

    private void authenticate(String email, String authority) {
        var token = new UsernamePasswordAuthenticationToken(email, "n/a",
                List.of(new SimpleGrantedAuthority(authority)));
        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(token);
        // MockMvc помнит контекст первого запроса теста, поэтому смена роли
        // посреди сценария обновляет и test-хранилище Security.
        TestSecurityContextHolder.setAuthentication(token);
    }

    private User ensureUser(String email, String fullName, Role role, User manager, String department) {
        User user = userRepository.findByEmail(email).orElseGet(User::new);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setRole(role);
        user.setDepartment(department);
        user.setPosition("Specialist");
        user.setSalary(new BigDecimal("88000"));
        user.setGradeLevel(GradeLevel.MIDDLE);
        user.setWeeklyCapacityHours(40);
        user.setHireDate(LocalDate.of(2023, 6, 1));
        user.setActive(true);
        user.setManager(manager);
        return userRepository.save(user);
    }
}
