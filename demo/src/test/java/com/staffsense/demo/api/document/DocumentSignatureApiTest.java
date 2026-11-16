package com.staffsense.demo.api.document;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staffsense.demo.model.Document;
import com.staffsense.demo.model.DocumentSignature;
import com.staffsense.demo.model.DocumentStatus;
import com.staffsense.demo.model.DocumentType;
import com.staffsense.demo.model.GradeLevel;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.SignatureDecision;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.DocumentRepository;
import com.staffsense.demo.repository.DocumentSignatureRepository;
import com.staffsense.demo.repository.DocumentStatusHistoryRepository;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.service.document.DocumentSignatureService;
import com.staffsense.demo.service.document.DocumentSignatureSlaScheduler;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Подпись документа и SLA глазами клиента.
 *
 * <p>Проверяем то, ради чего подпись выносилась в отдельную сущность: решение
 * переводит документ по маршруту и остаётся записью в реестре, повторная
 * подпись — 409, автор не подписывает сам себя — 403, сотруднику подпись
 * закрыта, а просроченный документ виден в очереди флагом и отрицательным
 * остатком часов.
 *
 * <p>Документы создаются сразу в нужном статусе: тест про подпись не должен
 * проверять ещё и регистрацию — для неё есть тесты реестра.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentSignatureApiTest {

    /** Префикс своих документов: чужие тестовые данные не трогаем. */
    private static final String REG_PREFIX = "SIG-";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentStatusHistoryRepository historyRepository;

    @Autowired
    private DocumentSignatureRepository signatureRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DocumentSignatureService signatureService;

    @Autowired
    private DocumentSignatureSlaScheduler scheduler;

    private User hr;
    private User authorHr;
    private User pm;
    private User otherPm;
    private User employee;
    private User foreignEmployee;

    private Document approvable;
    private Document ownDocument;
    private Document okToReject;
    private Document overdue;
    private Document fresh;
    private Document foreign;
    private Document draft;

    @BeforeEach
    void setUp() {
        cleanUp();

        hr = user("sig.hr@test.local", "Кадровая Служба", Role.HR, null);
        // Отдельный автор: подписант и автор — разные люди, иначе подпись запрещена.
        authorHr = user("sig.author@test.local", "Автор Приказов", Role.HR, null);
        pm = user("sig.pm@test.local", "Руководитель Подписи", Role.PM, null);
        otherPm = user("sig.other.pm@test.local", "Чужой Руководитель", Role.PM, null);
        employee = user("sig.employee@test.local", "Свой Сотрудник", Role.EMPLOYEE, pm);
        foreignEmployee = user("sig.foreign@test.local", "Чужой Сотрудник", Role.EMPLOYEE, otherPm);

        approvable = documentRepository.save(document("SIG-1/2099", "Приказ на подпись",
                employee, authorHr, DocumentStatus.ON_APPROVAL, null));
        ownDocument = documentRepository.save(document("SIG-2/2099", "Приказ самого подписанта",
                employee, hr, DocumentStatus.ON_APPROVAL, null));
        okToReject = documentRepository.save(document("SIG-3/2099", "Приказ на отклонение",
                employee, authorHr, DocumentStatus.ON_APPROVAL, null));
        overdue = documentRepository.save(document("SIG-4/2099", "Просроченный приказ",
                employee, authorHr, DocumentStatus.ON_APPROVAL,
                LocalDateTime.now().minusDays(3)));
        fresh = documentRepository.save(document("SIG-5/2099", "Приказ со сроком впереди",
                employee, authorHr, DocumentStatus.ON_APPROVAL,
                LocalDateTime.now().plusHours(10)));
        foreign = documentRepository.save(document("SIG-6/2099", "Приказ чужой команды",
                foreignEmployee, authorHr, DocumentStatus.ON_APPROVAL, null));
        draft = documentRepository.save(document("SIG-7/2099", "Черновик приказа",
                employee, authorHr, DocumentStatus.DRAFT, null));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        cleanUp();
    }

    @Test
    @DisplayName("Подпись переводит документ в APPROVED и пишет запись с автором и комментарием")
    void signatureApprovesDocumentAndWritesRecord() throws Exception {
        authenticate("sig.hr@test.local", "ROLE_HR");

        mockMvc.perform(post("/api/v1/documents/" + approvable.getId() + "/sign")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\",\"comment\":\"Проверено кадровой службой\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.canSign").value(false))
                .andExpect(jsonPath("$.signatures.length()").value(1))
                .andExpect(jsonPath("$.signatures[0].decision").value("APPROVED"))
                .andExpect(jsonPath("$.signatures[0].decisionTitle").value("Подписано"))
                .andExpect(jsonPath("$.signatures[0].signerName").value("Кадровая Служба"))
                .andExpect(jsonPath("$.signatures[0].comment").value("Проверено кадровой службой"))
                .andExpect(jsonPath("$.signatures[0].signedAt").isNotEmpty())
                // Подпись — ещё и шаг маршрута: история должна её знать.
                .andExpect(jsonPath("$.history[0].toStatus").value("APPROVED"))
                .andExpect(jsonPath("$.history[0].actorName").value("Кадровая Служба"));

        List<DocumentSignature> signatures =
                signatureRepository.findByDocumentIdOrderBySignedAtAscIdAsc(approvable.getId());
        assertThat(signatures).hasSize(1);
        DocumentSignature stored = signatures.get(0);
        assertThat(stored.getDecision()).isEqualTo(SignatureDecision.APPROVED);
        assertThat(stored.getComment()).isEqualTo("Проверено кадровой службой");
        assertThat(stored.getSignedAt()).isNotNull();
        assertThat(stored.getSigner().getId()).isEqualTo(hr.getId());

        // Карточка после перезагрузки показывает ту же подпись.
        mockMvc.perform(get("/api/v1/documents/" + approvable.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signatures[0].decision").value("APPROVED"));
    }

    @Test
    @DisplayName("Отклонение возвращает документ в черновик, повторная подпись — 409")
    void repeatedSignatureIsRejected() throws Exception {
        authenticate("sig.hr@test.local", "ROLE_HR");

        mockMvc.perform(post("/api/v1/documents/" + okToReject.getId() + "/sign")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECTED\",\"comment\":\"Нужны правки\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.signatures[0].decision").value("REJECTED"));

        // Возврат на согласование — обычный шаг маршрута, подпись при этом
        // не «сгорает»: тот же человек второй раз документ не подписывает.
        mockMvc.perform(post("/api/v1/documents/" + okToReject.getId() + "/status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ON_APPROVAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ON_APPROVAL"))
                .andExpect(jsonPath("$.dueAt").isNotEmpty());

        mockMvc.perform(post("/api/v1/documents/" + okToReject.getId() + "/sign")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isConflict());

        // Отказ ничего не записал: подпись по-прежнему одна.
        assertThat(signatureRepository.findByDocumentIdOrderBySignedAtAscIdAsc(okToReject.getId()))
                .hasSize(1);
        assertThat(documentRepository.findById(okToReject.getId()).orElseThrow().getStatus())
                .isEqualTo(DocumentStatus.ON_APPROVAL);
    }

    @Test
    @DisplayName("Документ не на согласовании подписать нельзя — 409")
    void signingDraftIsConflict() throws Exception {
        authenticate("sig.hr@test.local", "ROLE_HR");

        mockMvc.perform(post("/api/v1/documents/" + draft.getId() + "/sign")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isConflict());

        assertThat(signatureRepository.findByDocumentIdOrderBySignedAtAscIdAsc(draft.getId()))
                .isEmpty();
    }

    @Test
    @DisplayName("Автор не может подписать собственный документ — 403")
    void authorCannotSignOwnDocument() throws Exception {
        authenticate("sig.hr@test.local", "ROLE_HR");

        mockMvc.perform(post("/api/v1/documents/" + ownDocument.getId() + "/sign")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isForbidden());

        // Карточка тоже не предлагает кнопку: флаг считает сервер.
        mockMvc.perform(get("/api/v1/documents/" + ownDocument.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canSign").value(false));

        assertThat(signatureRepository.findByDocumentIdOrderBySignedAtAscIdAsc(ownDocument.getId()))
                .isEmpty();
        assertThat(documentRepository.findById(ownDocument.getId()).orElseThrow().getStatus())
                .isEqualTo(DocumentStatus.ON_APPROVAL);
    }

    @Test
    @DisplayName("Просроченный документ идёт первым в очереди, с флагом и отрицательным остатком")
    void overdueDocumentAppearsInQueueWithFlag() throws Exception {
        authenticate("sig.hr@test.local", "ROLE_HR");

        String body = mockMvc.perform(get("/api/v1/documents/signature-queue"))
                .andExpect(status().isOk())
                // Просроченный — самый ранний срок, значит первый.
                .andExpect(jsonPath("$[0].id").value(overdue.getId().intValue()))
                .andExpect(jsonPath("$[0].overdue").value(true))
                .andExpect(jsonPath("$[0].dueAt").isNotEmpty())
                // Документ со сроком впереди — не просрочен.
                .andExpect(jsonPath("$[?(@.id == " + fresh.getId() + ")].overdue", hasItem(false)))
                .andExpect(jsonPath("$[?(@.id == " + fresh.getId() + ")].remainingHours",
                        hasItem(greaterThan(0))))
                .andReturn().getResponse().getContentAsString();

        // Остаток часов — число, а не строка: клиент форматирует его сам.
        List<Map<String, Object>> items = objectMapper.readValue(body, new TypeReference<>() {
        });
        Number overdueHours = (Number) items.get(0).get("remainingHours");
        // Три дня просрочки — 72 часа и больше по модулю.
        assertThat(overdueHours.longValue()).isLessThanOrEqualTo(-72L);
    }

    @Test
    @DisplayName("Переход в «На согласовании» ставит срок SLA, подпись его снимает")
    void dueAtIsSetOnApprovalAndClearedBySignature() throws Exception {
        authenticate("sig.hr@test.local", "ROLE_HR");

        mockMvc.perform(post("/api/v1/documents/" + draft.getId() + "/status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ON_APPROVAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ON_APPROVAL"))
                .andExpect(jsonPath("$.dueAt").isNotEmpty())
                .andExpect(jsonPath("$.overdue").value(false))
                .andExpect(jsonPath("$.canSign").value(true));

        // SLA по умолчанию — 24 часа (documents.signature.sla-hours).
        LocalDateTime dueAt = documentRepository.findById(draft.getId()).orElseThrow().getDueAt();
        assertThat(dueAt)
                .isAfter(LocalDateTime.now().plusHours(23))
                .isBefore(LocalDateTime.now().plusHours(25));

        mockMvc.perform(post("/api/v1/documents/" + draft.getId() + "/sign")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // Документ больше не ждёт подписи — срок ему не нужен.
        assertThat(documentRepository.findById(draft.getId()).orElseThrow().getDueAt()).isNull();
    }

    @Test
    @DisplayName("PM подписывает документы своей команды, но не чужие")
    void pmSignsOnlyTeamDocuments() throws Exception {
        authenticate("sig.pm@test.local", "ROLE_PM");

        mockMvc.perform(post("/api/v1/documents/" + foreign.getId() + "/sign")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/documents/" + approvable.getId() + "/sign")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\",\"comment\":\"Согласовано руководителем\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.signatures[0].signerName").value("Руководитель Подписи"));

        // В очереди PM чужих документов нет вовсе.
        mockMvc.perform(get("/api/v1/documents/signature-queue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + foreign.getId() + ")]").isEmpty());
    }

    @Test
    @DisplayName("overdue() отдаёт просроченные, а планировщик только считает — статусы не меняет")
    void overdueListAndSchedulerDoNotChangeStatuses() {
        List<Document> overdueDocuments = signatureService.overdue();

        assertThat(overdueDocuments).extracting(Document::getId)
                .contains(overdue.getId())
                .doesNotContain(fresh.getId(), approvable.getId());

        // Плановая задача — сигнал, а не действие: документ остаётся там, где
        // его оставил человек, даже если срок давно прошёл.
        scheduler.reportOverdueSignatures();

        assertThat(documentRepository.findById(overdue.getId()).orElseThrow().getStatus())
                .isEqualTo(DocumentStatus.ON_APPROVAL);
        assertThat(signatureRepository.findByDocumentIdOrderBySignedAtAscIdAsc(overdue.getId()))
                .isEmpty();
    }

    @Test
    @DisplayName("EMPLOYEE получает 403 и на подписи, и в очереди")
    void employeeForbidden() throws Exception {
        authenticate("sig.employee@test.local", "ROLE_EMPLOYEE");

        mockMvc.perform(get("/api/v1/documents/signature-queue"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/documents/" + approvable.getId() + "/sign")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isForbidden());

        assertThat(signatureRepository.findByDocumentIdOrderBySignedAtAscIdAsc(approvable.getId()))
                .isEmpty();
    }

    // ── вспомогательное ──────────────────────────────────────────────────

    /**
     * Удаляем только свои документы (по префиксу номера) — вместе с их
     * подписями и историей. Глобальная очистка реестра ломала бы данные,
     * которые готовит {@code DataInitializer} для других тестов.
     */
    private void cleanUp() {
        List<Document> mine = documentRepository.findAll().stream()
                .filter(document -> document.getRegNumber() != null
                        && document.getRegNumber().startsWith(REG_PREFIX))
                .toList();
        if (mine.isEmpty()) {
            return;
        }
        List<Long> ids = mine.stream().map(Document::getId).toList();
        signatureRepository.deleteAll(signatureRepository.findAll().stream()
                .filter(signature -> ids.contains(signature.getDocument().getId()))
                .toList());
        ids.forEach(id -> historyRepository.deleteAll(
                historyRepository.findByDocumentIdOrderByCreatedAtAscIdAsc(id)));
        documentRepository.deleteAll(mine);
    }

    private void authenticate(String email, String authority) {
        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a",
                        List.of(new SimpleGrantedAuthority(authority))));
    }

    private User user(String email, String fullName, Role role, User manager) {
        return userRepository.findByEmail(email).orElseGet(() -> userRepository.save(User.builder()
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
                .build()));
    }

    private Document document(String regNumber, String title, User employee, User author,
                              DocumentStatus status, LocalDateTime dueAt) {
        return Document.builder()
                .regNumber(regNumber)
                .docType(DocumentType.ORDER)
                .title(title)
                .summary("Тестовый документ подписи")
                .employee(employee)
                .author(author)
                .documentDate(LocalDate.of(2026, 11, 1))
                .retentionUntil(LocalDate.of(2031, 11, 1))
                .status(status)
                .dueAt(dueAt)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
