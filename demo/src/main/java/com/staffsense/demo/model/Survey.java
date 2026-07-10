package com.staffsense.demo.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "surveys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Survey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false)
    private String title;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "target_scope", nullable = false, length = 20)
    private TargetScope targetScope;

    @Column(name = "target_department", length = 100)
    private String targetDepartment;

    @Column(name = "target_manager_id")
    private Long targetManagerId;

    @Column(name = "target_user_id")
    private Long targetUserId;

    @NotNull
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @NotNull
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "survey_type", nullable = false, length = 20)
    @Builder.Default
    private SurveyType surveyType = SurveyType.CUSTOM;

    @NotNull
    @Column(name = "is_anonymous", nullable = false)
    private boolean anonymous;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SurveyStatus status = SurveyStatus.DRAFT;
}
