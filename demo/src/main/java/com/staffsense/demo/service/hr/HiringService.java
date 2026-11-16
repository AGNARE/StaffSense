package com.staffsense.demo.service.hr;

import com.staffsense.demo.exception.ResourceNotFoundException;
import com.staffsense.demo.model.Candidate;
import com.staffsense.demo.model.CandidateStage;
import com.staffsense.demo.model.User;
import com.staffsense.demo.model.Vacancy;
import com.staffsense.demo.model.VacancyStatus;
import com.staffsense.demo.repository.CandidateRepository;
import com.staffsense.demo.repository.VacancyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Воронка найма: вакансии, кандидаты и переходы по этапам.
 *
 * <p>Правила:
 * <ul>
 *   <li>кандидатов принимает только открытая вакансия — черновик и
 *       приостановленная не должны накапливать отклики;</li>
 *   <li>переходы этапов описаны в {@link CandidateStage}: назад и из
 *       финальных состояний не ходим;</li>
 *   <li>найм последнего кандидата закрывает вакансию автоматически —
 *       иначе она висит открытой и портит метрику time-to-hire.</li>
 * </ul>
 */
@Service
public class HiringService {

    private static final Logger log = LoggerFactory.getLogger(HiringService.class);

    private final VacancyRepository vacancyRepository;
    private final CandidateRepository candidateRepository;

    public HiringService(VacancyRepository vacancyRepository,
                         CandidateRepository candidateRepository) {
        this.vacancyRepository = vacancyRepository;
        this.candidateRepository = candidateRepository;
    }

    /** Сводка воронки по вакансии. */
    public record Funnel(
            Long vacancyId,
            String title,
            String status,
            String statusTitle,
            int openings,
            long hired,
            Map<String, Long> byStage,
            long totalCandidates,
            Double averageDaysToHire,
            List<Candidate> candidates
    ) {
    }

    public List<Vacancy> vacancies() {
        return vacancyRepository.findAllByOrderByCreatedAtDesc();
    }

    public Vacancy vacancy(Long id) {
        return vacancyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Вакансия не найдена: " + id));
    }

    @Transactional
    public Vacancy create(String title, String department, String gradeLevel, int openings, User author) {
        Vacancy vacancy = Vacancy.builder()
                .title(title.trim())
                .department(department)
                .gradeLevel(gradeLevel)
                .status(VacancyStatus.OPEN)
                .openings(openings)
                .createdBy(author)
                .createdAt(LocalDateTime.now())
                .openedAt(LocalDateTime.now())
                .build();

        Vacancy saved = vacancyRepository.save(vacancy);
        log.info("Вакансия открыта: {} ({}), мест {}", saved.getTitle(), saved.getDepartment(), openings);
        return saved;
    }

    /** Добавляет кандидата на первый этап. */
    @Transactional
    public Candidate addCandidate(Long vacancyId, String fullName, String email, String source) {
        Vacancy vacancy = vacancy(vacancyId);
        if (!vacancy.getStatus().acceptsCandidates()) {
            throw new IllegalStateException("Вакансия не принимает кандидатов: " + vacancy.getStatus().getTitle());
        }

        Candidate candidate = Candidate.builder()
                .vacancy(vacancy)
                .fullName(fullName.trim())
                .email(email)
                .source(source)
                .stage(CandidateStage.APPLIED)
                .appliedAt(LocalDateTime.now())
                .stageChangedAt(LocalDateTime.now())
                .build();

        return candidateRepository.save(candidate);
    }

    /** Переводит кандидата на следующий этап с проверкой маршрута. */
    @Transactional
    public Candidate move(Long candidateId, CandidateStage target) {
        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Кандидат не найден: " + candidateId));

        if (!candidate.getStage().canMoveTo(target)) {
            throw new IllegalStateException("Переход «" + candidate.getStage().getTitle()
                    + "» → «" + target.getTitle() + "» недопустим");
        }

        candidate.setStage(target);
        candidate.setStageChangedAt(LocalDateTime.now());
        Candidate saved = candidateRepository.save(candidate);

        if (target == CandidateStage.HIRED) {
            closeIfFilled(saved.getVacancy());
        }
        return saved;
    }

    /** Сводка по вакансии: сколько на каждом этапе и сколько дней до найма. */
    public Funnel funnel(Long vacancyId) {
        Vacancy vacancy = vacancy(vacancyId);
        List<Candidate> candidates = candidateRepository.findByVacancyOrderByAppliedAtDesc(vacancy);

        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        for (CandidateStage stage : CandidateStage.values()) {
            counts.put(stage.name(), candidateRepository.countByVacancyAndStage(vacancy, stage));
        }

        long hired = counts.getOrDefault(CandidateStage.HIRED.name(), 0L);
        double averageDays = candidates.stream()
                .filter(c -> c.getStage() == CandidateStage.HIRED && c.getStageChangedAt() != null)
                .mapToLong(c -> Duration.between(c.getAppliedAt(), c.getStageChangedAt()).toDays())
                .average()
                .orElse(0.0);

        return new Funnel(vacancy.getId(), vacancy.getTitle(), vacancy.getStatus().name(),
                vacancy.getStatus().getTitle(), vacancy.getOpenings(), hired, counts,
                candidates.size(), candidates.stream().anyMatch(c -> c.getStage() == CandidateStage.HIRED)
                        ? averageDays : null, candidates);
    }

    /** Закрывает вакансию, если мест больше не осталось. */
    private void closeIfFilled(Vacancy vacancy) {
        long hired = candidateRepository.countByVacancyAndStage(vacancy, CandidateStage.HIRED);
        if (hired >= vacancy.getOpenings()) {
            vacancy.setStatus(VacancyStatus.CLOSED);
            vacancy.setClosedAt(LocalDateTime.now());
            vacancyRepository.save(vacancy);
            log.info("Вакансия закрыта: {} — нанято {}", vacancy.getTitle(), hired);
        }
    }
}
