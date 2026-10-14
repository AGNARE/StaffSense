package com.staffsense.demo.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Факт участия сотрудника в опросе (FR-022, ERR-012).
 *
 * <p>Нужен, потому что у анонимного опроса ответ не связывается с сотрудником
 * (BR-011): в {@code survey_responses} поле {@code user_id} остаётся пустым, и по
 * ответам нельзя определить, проходил ли сотрудник опрос. Из-за этого повторный
 * переход на страницу опроса позволял отвечать сколько угодно раз и накручивать
 * результаты.
 *
 * <p>Здесь хранится только факт участия и время — без ответов, поэтому
 * анонимность ответов не нарушается.
 */
@Entity
@Table(name = "survey_participation",
       uniqueConstraints = @UniqueConstraint(
               name = "uq_survey_participation_survey_user",
               columnNames = {"survey_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurveyParticipation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "survey_id", nullable = false)
    private Survey survey;

    /** Кто участвовал; сами ответы при этом остаются анонимными. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;
}
