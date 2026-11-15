package com.staffsense.demo.api.document;

import com.staffsense.demo.model.Document;
import com.staffsense.demo.model.DocumentStatus;
import com.staffsense.demo.model.DocumentType;
import com.staffsense.demo.model.GradeLevel;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.DocumentRepository;
import com.staffsense.demo.repository.DocumentStatusHistoryRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Маршрут документов глазами клиента: входящие, карточка и смена статуса.
 *
 * <p>Проверяем границы, ради которых раздел и переделывался: сотрудник не
 * видит реестр вовсе, руководитель — только документы своей команды, HR
 * видит документы «на согласовании», а каждый переход оставляет запись
 * в истории. Недопустимый переход — 409, а не молчаливая запись.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentRouteApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentStatusHistoryRepository historyRepository;

    @Autowired
    private UserRepository userRepository;

    private User hr;
    private User pm;
    private User employee;
    private User foreignEmployee;
    private Document teamDocument;
    private Document foreignDocument;
    private Document draftDocument;

    @BeforeEach
    void setUp() {
        historyRepository.deleteAll();
        documentRepository.deleteAll();

        hr = userRepository.findByEmail("doc.hr@test.local").orElseGet(
                () -> userRepository.save(user("doc.hr@test.local", "Кадровая Служба", Role.HR, null)));

        // Сначала руководитель: подчинённый должен ссылаться на него.
        pm = userRepository.findByEmail("doc.pm@test.local").orElseGet(
                () -> userRepository.save(user("doc.pm@test.local", "Руководитель Документов", Role.PM, null)));

        User otherPm = userRepository.findByEmail("doc.other.pm@test.local").orElseGet(
                () -> userRepository.save(user("doc.other.pm@test.local", "Чужой Руководитель", Role.PM, null)));

        employee = userRepository.findByEmail("doc.employee@test.local").orElseGet(
                () -> userRepository.save(user("doc.employee@test.local", "Свой Сотрудник", Role.EMPLOYEE, pm)));

        foreignEmployee = userRepository.findByEmail("doc.foreign@test.local").orElseGet(
                () -> userRepository.save(user("doc.foreign@test.local", "Чужой Сотрудник", Role.EMPLOYEE, otherPm)));

        teamDocument = documentRepository.save(document("1-пр/2099", "Приказ по своему сотруднику",
                employee, DocumentStatus.ON_APPROVAL));
        foreignDocument = documentRepository.save(document("1-пр/2098", "Приказ по чужому сотруднику",
                foreignEmployee, DocumentStatus.ON_APPROVAL));
        draftDocument = documentRepository.save(document("2-пр/2099", "Черновик приказа",
                employee, DocumentStatus.DRAFT));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        historyRepository.deleteAll();
        documentRepository.deleteAll();
    }

    @Test
    @DisplayName("HR видит входящие: документы на согласовании")
    void hrSeesInbox() throws Exception {
        authenticate("doc.hr@test.local", "ROLE_HR");

        mockMvc.perform(get("/api/v1/documents/inbox"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem(teamDocument.getId().intValue())))
                .andExpect(jsonPath("$[*].id").value(hasItem(foreignDocument.getId().intValue())))
                .andExpect(jsonPath("$[*].status").value(hasItem("ON_APPROVAL")));
    }

    @Test
    @DisplayName("PM видит во входящих только документы своей команды")
    void pmSeesOnlyTeamInbox() throws Exception {
        authenticate("doc.pm@test.local", "ROLE_PM");

        mockMvc.perform(get("/api/v1/documents/inbox"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem(teamDocument.getId().intValue())))
                .andExpect(jsonPath("$[*].id").value(not(hasItem(foreignDocument.getId().intValue()))));

        // Реестр — такая же точка доступа: чужой приказ не должен появиться и там.
        mockMvc.perform(get("/api/v1/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem(teamDocument.getId().intValue())))
                .andExpect(jsonPath("$[*].id").value(not(hasItem(foreignDocument.getId().intValue()))));
    }

    @Test
    @DisplayName("Чужую карточку PM не открывает даже по прямому id")
    void pmCannotOpenForeignCard() throws Exception {
        authenticate("doc.pm@test.local", "ROLE_PM");

        mockMvc.perform(get("/api/v1/documents/" + foreignDocument.getId()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/documents/" + teamDocument.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeName").value("Свой Сотрудник"));
    }

    @Test
    @DisplayName("EMPLOYEE получает 403 и во входящих, и в карточке")
    void employeeForbidden() throws Exception {
        authenticate("doc.employee@test.local", "ROLE_EMPLOYEE");

        mockMvc.perform(get("/api/v1/documents/inbox"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/documents/" + teamDocument.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Переход статуса пишет историю с актором и комментарием")
    void transitionWritesHistory() throws Exception {
        authenticate("doc.hr@test.local", "ROLE_HR");

        mockMvc.perform(post("/api/v1/documents/" + teamDocument.getId() + "/status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\",\"comment\":\"Проверено кадровой службой\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.history.length()").value(1))
                .andExpect(jsonPath("$.history[0].fromStatus").value("ON_APPROVAL"))
                .andExpect(jsonPath("$.history[0].toStatus").value("APPROVED"))
                .andExpect(jsonPath("$.history[0].actorName").value("Кадровая Служба"))
                .andExpect(jsonPath("$.history[0].comment").value("Проверено кадровой службой"));

        // История остаётся доступной и при повторном открытии карточки.
        mockMvc.perform(get("/api/v1/documents/" + teamDocument.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history[0].toStatus").value("APPROVED"));
    }

    @Test
    @DisplayName("Недопустимый переход по маршруту отклоняется с 409")
    void invalidTransitionRejected() throws Exception {
        authenticate("doc.hr@test.local", "ROLE_HR");

        mockMvc.perform(post("/api/v1/documents/" + draftDocument.getId() + "/status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isConflict());

        // Отказ не должен был изменить ни статус, ни историю.
        mockMvc.perform(get("/api/v1/documents/" + draftDocument.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.history.length()").value(0));
    }

    @Test
    @DisplayName("PM не архивирует документ: это шаг кадровой службы")
    void pmCannotArchive() throws Exception {
        authenticate("doc.pm@test.local", "ROLE_PM");

        // PM видит документ команды, но архивный переход ему не разрешён.
        Document approved = documentRepository.save(document("3-пр/2099", "Утверждённый приказ",
                employee, DocumentStatus.APPROVED));

        mockMvc.perform(post("/api/v1/documents/" + approved.getId() + "/status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isForbidden());
    }

    private void authenticate(String email, String authority) {
        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a",
                        List.of(new SimpleGrantedAuthority(authority))));
    }

    private Document document(String regNumber, String title, User employee, DocumentStatus status) {
        return Document.builder()
                .regNumber(regNumber)
                .docType(DocumentType.ORDER)
                .title(title)
                .summary("Тестовый документ маршрута")
                .employee(employee)
                .author(hr)
                .documentDate(LocalDate.of(2026, 11, 1))
                .retentionUntil(LocalDate.of(2031, 11, 1))
                .status(status)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private User user(String email, String fullName, Role role, User manager) {
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
                .hireDate(LocalDate.of(2023, 5, 1))
                .active(true)
                .manager(manager)
                .build();
    }
}
