package com.staffsense.demo.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Личное дело сотрудника: персональные данные, полученные из госсистемы
 * «Тундук / Кызмат» (эмуляция), плюс согласие на обработку персональных данных.
 *
 * <p>Связь с {@link User} — один к одному. Персональные данные намеренно
 * вынесены из {@code users}, чтобы ограничить доступ: паспорт, ПИН и фото
 * видят только HR и ADMIN (BR-025), а корпоративные поля остаются в {@code users}.
 */
@Entity
@Table(name = "employee_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeDocuments {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /** ИНН гражданина Кыргызской Республики — 14 цифр. */
    @NotBlank
    @Size(max = 14)
    @Column(name = "inn", nullable = false, unique = true, length = 14)
    private String inn;

    /** ПИН (персональный идентификационный номер). */
    @Size(max = 20)
    @Column(name = "pin", length = 20)
    private String pin;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10)
    private Gender gender;

    @Size(max = 4)
    @Column(name = "passport_series", length = 4)
    private String passportSeries;

    @Size(max = 6)
    @Column(name = "passport_number", length = 6)
    private String passportNumber;

    @Size(max = 255)
    @Column(name = "passport_issued_by", length = 255)
    private String passportIssuedBy;

    @Column(name = "passport_issue_date")
    private LocalDate passportIssueDate;

    @Size(max = 500)
    @Column(name = "registration_address", length = 500)
    private String registrationAddress;

    /** Ссылка на фотографию. В демонстрационной версии — плейсхолдер. */
    @Size(max = 500)
    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    /** Данные подтверждены госсистемой (а не введены вручную). */
    @Column(name = "tunduk_verified", nullable = false)
    @Builder.Default
    private boolean tundukVerified = false;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    /** BR-024: момент получения согласия кандидата на обработку персональных данных. */
    @Column(name = "consent_given_at")
    private LocalDateTime consentGivenAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * BR-025: маскированное представление паспорта для ролей без прав
     * на персональные данные, а также для журналов.
     */
    @Transient
    public String getMaskedPassport() {
        if (passportNumber == null || passportNumber.isBlank()) {
            return "—";
        }
        String series = (passportSeries != null && !passportSeries.isBlank()) ? passportSeries : "****";
        String tail = passportNumber.length() > 2
                ? passportNumber.substring(passportNumber.length() - 2)
                : passportNumber;
        return series + " ****" + tail;
    }

    /** Маскированный ИНН: видны только последние 4 цифры. */
    @Transient
    public String getMaskedInn() {
        if (inn == null || inn.length() < 4) {
            return "—";
        }
        return "**********" + inn.substring(inn.length() - 4);
    }
}
