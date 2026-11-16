package com.staffsense.demo.service.document;

import com.staffsense.demo.model.Document;
import com.staffsense.demo.model.DocumentStatus;
import com.staffsense.demo.model.DocumentType;
import com.staffsense.demo.model.GradeLevel;
import com.staffsense.demo.model.NotificationType;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.DocumentRepository;
import com.staffsense.demo.repository.NotificationRepository;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.service.notification.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Просроченная подпись создаёт уведомление.
 *
 * <p>Планировщик раньше только писал в лог — узнать о просрочке можно было,
 * лишь заглянув в него. Теперь подписанты получают уведомление в системе,
 * но статусы документов остаются прежними: эскалация — решение человека.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SignatureOverdueNotificationTest {

    @Autowired
    private DocumentSignatureSlaScheduler scheduler;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    @DisplayName("Просроченный документ уведомляет подписантов, статус не меняется")
    void overdueCreatesNotification() {
        User hr = userRepository.save(User.builder()
                .fullName("Подписант Просрочки")
                .email("sla.hr@test.local")
                .passwordHash("hash")
                .role(Role.HR)
                .department("HR")
                .position("Specialist")
                .salary(new BigDecimal("80000"))
                .gradeLevel(GradeLevel.MIDDLE)
                .weeklyCapacityHours(40)
                .hireDate(LocalDate.of(2023, 1, 10))
                .active(true)
                .build());

        Document document = documentRepository.save(Document.builder()
                .regNumber("SLA-1/2026")
                .docType(DocumentType.ORDER)
                .title("Просроченный приказ")
                .author(hr)
                .documentDate(LocalDate.now())
                .status(DocumentStatus.ON_APPROVAL)
                .dueAt(LocalDateTime.now().minusHours(5))
                .createdAt(LocalDateTime.now())
                .build());

        scheduler.reportOverdueSignatures();

        long unread = notificationRepository.countByUserIdAndReadAtIsNull(hr.getId());
        assertThat(unread).isGreaterThanOrEqualTo(1);
        assertThat(notificationRepository.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(hr.getId()))
                .anyMatch(n -> n.getType() == NotificationType.SIGNATURE_OVERDUE);

        // Статус не изменился: просрочка — сигнал, а не команда.
        assertThat(documentRepository.findById(document.getId()).orElseThrow().getStatus())
                .isEqualTo(DocumentStatus.ON_APPROVAL);
    }
}
