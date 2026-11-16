package com.staffsense.demo.api.hr;

import com.staffsense.demo.model.Candidate;
import com.staffsense.demo.model.CandidateStage;
import com.staffsense.demo.model.User;
import com.staffsense.demo.model.Vacancy;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.service.hr.HiringService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Воронка найма: вакансии, кандидаты, этапы.
 *
 * <p>Чтение открыто управленческим ролям, изменения — HR и администратору:
 * наём это кадровое действие, а не наблюдение.
 */
@RestController
@RequestMapping("/api/v1/hiring")
@Tag(name = "Найм", description = "Вакансии, кандидаты и воронка")
public class HiringApiController {

    private final HiringService hiringService;
    private final UserRepository userRepository;

    public HiringApiController(HiringService hiringService, UserRepository userRepository) {
        this.hiringService = hiringService;
        this.userRepository = userRepository;
    }

    /** Все вакансии. */
    @GetMapping("/vacancies")
    @PreAuthorize("hasAnyRole('ADMIN','HR','EXEC')")
    public List<VacancyDto> vacancies() {
        return hiringService.vacancies().stream().map(VacancyDto::from).toList();
    }

    /** Открыть вакансию. */
    @PostMapping("/vacancies")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public VacancyDto createVacancy(@Valid @RequestBody CreateVacancyRequest request, Authentication auth) {
        User author = userRepository.findByEmail(auth.getName()).orElse(null);
        return VacancyDto.from(hiringService.create(request.title(), request.department(),
                request.gradeLevel(), request.openings(), author));
    }

    /** Воронка по вакансии. */
    @GetMapping("/vacancies/{id}/funnel")
    @PreAuthorize("hasAnyRole('ADMIN','HR','EXEC')")
    public FunnelDto funnel(@PathVariable Long id) {
        return FunnelDto.from(hiringService.funnel(id));
    }

    /** Добавить кандидата. */
    @PostMapping("/vacancies/{id}/candidates")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public CandidateDto addCandidate(@PathVariable Long id, @Valid @RequestBody CreateCandidateRequest request) {
        return CandidateDto.from(hiringService.addCandidate(id, request.fullName(), request.email(), request.source()));
    }

    /** Перевести кандидата на другой этап. */
    @PostMapping("/candidates/{id}/stage")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public CandidateDto move(@PathVariable Long id, @RequestBody MoveRequest request) {
        CandidateStage target;
        try {
            target = CandidateStage.valueOf(request.stage().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Неизвестный этап: " + request.stage());
        }
        return CandidateDto.from(hiringService.move(id, target));
    }

    // ── DTO ──────────────────────────────────────────────────────────────

    public record VacancyDto(Long id, String title, String department, String gradeLevel,
                             String status, String statusTitle, int openings, LocalDateTime createdAt) {
        static VacancyDto from(Vacancy vacancy) {
            return new VacancyDto(vacancy.getId(), vacancy.getTitle(), vacancy.getDepartment(),
                    vacancy.getGradeLevel(), vacancy.getStatus().name(), vacancy.getStatus().getTitle(),
                    vacancy.getOpenings(), vacancy.getCreatedAt());
        }
    }

    public record CandidateDto(Long id, Long vacancyId, String fullName, String email,
                               String stage, String stageTitle, String source,
                               LocalDateTime appliedAt, LocalDateTime stageChangedAt) {
        static CandidateDto from(Candidate candidate) {
            return new CandidateDto(candidate.getId(), candidate.getVacancy().getId(),
                    candidate.getFullName(), candidate.getEmail(), candidate.getStage().name(),
                    candidate.getStage().getTitle(), candidate.getSource(),
                    candidate.getAppliedAt(), candidate.getStageChangedAt());
        }
    }

    public record FunnelDto(Long vacancyId, String title, String status, String statusTitle,
                            int openings, long hired, Map<String, Long> byStage,
                            long totalCandidates, Double averageDaysToHire,
                            List<CandidateDto> candidates) {
        static FunnelDto from(HiringService.Funnel funnel) {
            return new FunnelDto(funnel.vacancyId(), funnel.title(), funnel.status(),
                    funnel.statusTitle(), funnel.openings(), funnel.hired(), funnel.byStage(),
                    funnel.totalCandidates(), funnel.averageDaysToHire(),
                    funnel.candidates().stream().map(CandidateDto::from).toList());
        }
    }

    public record CreateVacancyRequest(
            @NotBlank(message = "Название обязательно")
            @Size(max = 200, message = "Название не длиннее 200 символов") String title,
            @NotBlank(message = "Отдел обязателен")
            @Size(max = 100, message = "Отдел не длиннее 100 символов") String department,
            String gradeLevel,
            @Min(value = 1, message = "Мест должно быть хотя бы одно")
            @Max(value = 100, message = "Слишком много мест") int openings
    ) {
    }

    public record CreateCandidateRequest(
            @NotBlank(message = "Имя обязательно")
            @Size(max = 200, message = "Имя не длиннее 200 символов") String fullName,
            @Size(max = 150, message = "Email не длиннее 150 символов") String email,
            String source
    ) {
    }

    public record MoveRequest(String stage) {
    }
}
