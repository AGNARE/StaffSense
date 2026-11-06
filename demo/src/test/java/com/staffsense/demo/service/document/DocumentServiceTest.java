package com.staffsense.demo.service.document;

import com.staffsense.demo.model.Document;
import com.staffsense.demo.model.DocumentStatus;
import com.staffsense.demo.model.DocumentType;
import com.staffsense.demo.repository.DocumentRepository;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.service.DocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Правила реестра документов.
 *
 * <p>Проверяем то, ради чего домен и заводился: срок хранения зависит от типа,
 * номер уникален и последователен, а недопустимый переход статуса — ошибка,
 * а не молчаливая запись.
 */
class DocumentServiceTest {

    private DocumentRepository repository;
    private DocumentService service;

    @BeforeEach
    void setUp() {
        repository = mock(DocumentRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        service = new DocumentService(repository, userRepository);
        when(repository.existsByRegNumber(any())).thenReturn(false);
    }

    @Test
    @DisplayName("Приказ хранится 5 лет от даты документа")
    void orderRetention() {
        LocalDate date = LocalDate.of(2026, 11, 1);
        assertThat(service.retentionUntil(DocumentType.ORDER, date))
                .isEqualTo(LocalDate.of(2031, 11, 1));
    }

    @Test
    @DisplayName("Справка хранится 3 года, договор — постоянно")
    void certificateAndContractRetention() {
        LocalDate date = LocalDate.of(2026, 11, 1);
        assertThat(service.retentionUntil(DocumentType.CERTIFICATE, date))
                .isEqualTo(LocalDate.of(2029, 11, 1));
        assertThat(service.retentionUntil(DocumentType.CONTRACT, date)).isNull();
    }

    @Test
    @DisplayName("Номер последователен в пределах типа")
    void registrationNumbers() {
        when(repository.countByDocType(DocumentType.ORDER)).thenReturn(0L);
        assertThat(service.nextRegNumber(DocumentType.ORDER, LocalDate.of(2026, 5, 1)))
                .isEqualTo("1-пр/2026");

        when(repository.countByDocType(DocumentType.ORDER)).thenReturn(1L);
        assertThat(service.nextRegNumber(DocumentType.ORDER, LocalDate.of(2026, 6, 1)))
                .isEqualTo("2-пр/2026");
    }

    @Test
    @DisplayName("Недопустимый переход статуса отклоняется")
    void invalidTransitionRejected() {
        Document document = Document.builder()
                .id(1L)
                .status(DocumentStatus.DRAFT)
                .build();
        when(repository.findById(1L)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> service.changeStatus(1L, DocumentStatus.ARCHIVED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("недопустим");
    }

    @Test
    @DisplayName("Разрешённый переход применяется и сохраняется")
    void validTransitionApplied() {
        Document document = Document.builder()
                .id(2L)
                .status(DocumentStatus.DRAFT)
                .build();
        when(repository.findById(2L)).thenReturn(Optional.of(document));
        when(repository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Document updated = service.changeStatus(2L, DocumentStatus.ON_APPROVAL);

        assertThat(updated.getStatus()).isEqualTo(DocumentStatus.ON_APPROVAL);
        assertThat(updated.getUpdatedAt()).isNotNull();
    }
}
