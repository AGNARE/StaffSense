package com.staffsense.demo.service.hr;

import com.staffsense.demo.dto.hr.HireEmployeeForm;
import com.staffsense.demo.dto.hr.HireResult;
import com.staffsense.demo.model.EmployeeDocuments;
import com.staffsense.demo.model.GradeLevel;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.TundukVerificationResult;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.EmployeeDocumentsRepository;
import com.staffsense.demo.repository.TundukVerificationLogRepository;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.service.tunduk.MockTundukClient;
import com.staffsense.demo.service.tunduk.TundukStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тесты приёма сотрудника (модуль «Тундук»).
 *
 * <p>Проверяют бизнес-правила BR-023 (реактивация вместо дубля),
 * BR-024 (обязательное согласие на ПДн), BR-026 (аудит обращений)
 * и обработку ошибок ERR-013 / ERR-014.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HireEmployeeServiceTest {

    private static final String KNOWN_INN = "12345678901234";

    @Autowired
    private HireEmployeeService hireService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmployeeDocumentsRepository documentsRepository;

    @Autowired
    private TundukVerificationLogRepository verificationLogRepository;

    private User actor() {
        return userRepository.findByEmail("hr@staffsense.com").orElseThrow();
    }

    private HireEmployeeForm form(String inn, String email, boolean consent) {
        return HireEmployeeForm.builder()
                .inn(inn)
                .consentGiven(consent)
                .tundukVerified(true)
                .fullName("Тестовый Кандидат")
                .birthDate(LocalDate.of(1990, 5, 12))
                .passportSeries("ID12")
                .passportNumber("345678")
                .pin("11001990051234")
                .email(email)
                .role(Role.EMPLOYEE)
                .department("Engineering")
                .position("Java Developer")
                .gradeLevel(GradeLevel.MIDDLE)
                .salary(new BigDecimal("90000"))
                .weeklyCapacityHours(40)
                .hireDate(LocalDate.of(2026, 10, 1))
                .build();
    }

    @Test
    void verifyReturnsPersonDataForKnownInn() {
        var result = hireService.verify(KNOWN_INN, true, actor());

        assertEquals(TundukStatus.FOUND, result.status());
        assertNotNull(result.data());
        assertNotNull(result.data().fullName());
    }

    @Test
    void verifyWritesAuditLog() {
        long before = verificationLogRepository.count();
        hireService.verify(KNOWN_INN, true, actor());
        assertTrue(verificationLogRepository.count() > before, "Обращение должно попасть в журнал (BR-026)");
    }

    @Test
    void verifyRejectsInvalidInnWithAudit() {
        long before = verificationLogRepository.count();
        var result = hireService.verify("123", true, actor());

        assertEquals(TundukStatus.INVALID, result.status());
        assertTrue(verificationLogRepository.count() > before);
    }

    @Test
    void verifyReportsNotFoundAndUnavailable() {
        assertEquals(TundukStatus.NOT_FOUND, hireService.verify("11111111111111", true, actor()).status());
        assertEquals(TundukStatus.UNAVAILABLE,
                hireService.verify(MockTundukClient.UNAVAILABLE_INN, true, actor()).status());
    }

    @Test
    void hireCreatesEmployeeWithPersonalDocuments() {
        String email = "new.candidate@staffsense.com";
        HireResult result = hireService.hire(form(KNOWN_INN, email, true), actor());

        assertFalse(result.alreadyExisted());
        assertFalse(result.reactivated());
        assertNotNull(result.user().getId());
        assertEquals(email, result.user().getEmail());

        EmployeeDocuments docs = documentsRepository.findByUserId(result.user().getId()).orElseThrow();
        assertEquals(KNOWN_INN, docs.getInn());
        assertTrue(docs.isTundukVerified());
        assertNotNull(docs.getConsentGivenAt(), "Согласие на ПДн должно быть зафиксировано (BR-024)");
        assertNotNull(docs.getVerifiedAt());
    }

    @Test
    void hireRequiresConsent() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> hireService.hire(form(KNOWN_INN, "no.consent@staffsense.com", false), actor()));
        assertTrue(ex.getMessage().contains("согласие"));
    }

    @Test
    void hireRejectsInvalidInn() {
        assertThrows(IllegalArgumentException.class,
                () -> hireService.hire(form("123", "bad.inn@staffsense.com", true), actor()));
    }

    @Test
    void secondHireDoesNotCreateDuplicate() {
        String email = "duplicate.check@staffsense.com";
        HireResult first = hireService.hire(form(KNOWN_INN, email, true), actor());
        HireResult second = hireService.hire(form(KNOWN_INN, email, true), actor());

        assertEquals(first.user().getId(), second.user().getId(), "Дубль создаваться не должен (BR-023)");
        assertTrue(second.alreadyExisted());
        assertEquals(1, documentsRepository.findByInn(KNOWN_INN).stream()
                .filter(d -> d.getUser().getId().equals(first.user().getId()))
                .count());
    }

    @Test
    void hireReactivatesDismissedEmployee() {
        String email = "reactivate.check@staffsense.com";
        HireResult first = hireService.hire(form(KNOWN_INN, email, true), actor());

        // Увольняем
        User employee = userRepository.findById(first.user().getId()).orElseThrow();
        employee.setActive(false);
        userRepository.saveAndFlush(employee);

        HireResult second = hireService.hire(form(KNOWN_INN, email, true), actor());

        assertEquals(first.user().getId(), second.user().getId());
        assertTrue(second.reactivated(), "Уволенный сотрудник должен быть реактивирован (BR-023)");
        assertTrue(userRepository.findById(first.user().getId()).orElseThrow().isActive());
    }

    @Test
    void hireRejectsEmailAlreadyUsedByAnotherEmployee() {
        String takenEmail = userRepository.findByEmail("ivan@staffsense.com").orElseThrow().getEmail();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> hireService.hire(form("12345678901242", takenEmail, true), actor()));
        assertTrue(ex.getMessage().contains("Email"));
    }

    @Test
    void auditLogStoresResultOfLookup() {
        hireService.verify("11111111111111", true, actor());

        boolean hasNotFound = verificationLogRepository.findByInnOrderByRequestedAtDesc("11111111111111")
                .stream()
                .anyMatch(l -> l.getResult() == TundukVerificationResult.NOT_FOUND);
        assertTrue(hasNotFound, "В журнале должен быть результат NOT_FOUND");
    }

    /**
     * BR-024: персональные данные не запрашиваются до получения согласия.
     * Раньше согласие проверялось только на шаге сохранения, а верификация
     * по ИНН (обращение к госсистеме за ПДн) выполнялась без него.
     */
    @Test
    void verifyRequiresConsentAndWritesNothingToAuditLog() {
        long before = verificationLogRepository.count();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> hireService.verify(KNOWN_INN, false, actor()));

        assertTrue(ex.getMessage().contains("согласие"), "Отказ должен объяснять причину (BR-024)");
        assertEquals(before, verificationLogRepository.count(),
                "Обращения к госсистеме не было — журнал пополняться не должен");
    }
}
