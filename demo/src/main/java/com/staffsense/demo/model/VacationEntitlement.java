package com.staffsense.demo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Право на отпуск за год: сколько дней положено и сколько перенесено.
 *
 * <p>Использование здесь не хранится — оно считается из утверждённых
 * записок, иначе появился бы второй источник правды.
 */
@Entity
@Table(name = "vacation_entitlement")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationEntitlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // «year» — зарезервированное слово в H2 и части диалектов, поэтому
    // колонка называется period_year, а поле остаётся year для читаемости.
    @Column(name = "period_year", nullable = false)
    private Integer year;

    @Column(name = "entitled_days", nullable = false)
    private BigDecimal entitledDays;

    @Column(name = "carried_over_days", nullable = false)
    private BigDecimal carriedOverDays;

    @Column(length = 300)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
