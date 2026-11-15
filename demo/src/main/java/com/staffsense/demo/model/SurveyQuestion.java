package com.staffsense.demo.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

@Entity
@Table(name = "survey_questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurveyQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "survey_id", nullable = false)
    private Survey survey;

    @NotBlank
    @Size(max = 500)
    @Column(nullable = false, length = 500)
    private String text;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestionType type;

    @NotNull
    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    /**
     * Обязательность ответа — признак конструктора опроса (FR-022).
     *
     * <p>Необязательный вопрос сотрудник может пропустить. Значение по
     * умолчанию — {@code true}: у вопросов, созданных до появления признака,
     * поведение прохождения не меняется (миграция V13 ставит тот же DEFAULT).
     */
    @Column(name = "is_required", nullable = false)
    @Builder.Default
    private boolean required = true;
}
