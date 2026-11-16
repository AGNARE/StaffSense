package com.staffsense.demo.service.hr;

import com.staffsense.demo.model.Candidate;
import com.staffsense.demo.model.CandidateStage;
import com.staffsense.demo.model.GradeLevel;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.User;
import com.staffsense.demo.model.Vacancy;
import com.staffsense.demo.model.VacancyStatus;
import com.staffsense.demo.repository.CandidateRepository;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.repository.VacancyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Воронка найма: правила этапов и закрытие вакансии.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HiringServiceTest {

    @Autowired
    private HiringService hiringService;

    @Autowired
    private VacancyRepository vacancyRepository;

    @Autowired
    private CandidateRepository candidateRepository;

    @Autowired
    private UserRepository userRepository;

    private User hr;

    @BeforeEach
    void setUp() {
        hr = userRepository.findByEmail("hire.hr@test.local").orElseGet(() -> userRepository.save(
                User.builder()
                        .fullName("HR Найм")
                        .email("hire.hr@test.local")
                        .passwordHash("hash")
                        .role(Role.HR)
                        .department("HR")
                        .position("Recruiter")
                        .salary(new BigDecimal("90000"))
                        .gradeLevel(GradeLevel.SENIOR)
                        .weeklyCapacityHours(40)
                        .hireDate(LocalDate.of(2023, 5, 1))
                        .active(true)
                        .build()));
    }

    @Test
    @DisplayName("Кандидат проходит этапы по порядку")
    void stageFlow() {
        Vacancy vacancy = hiringService.create("Java Developer", "Engineering", "MIDDLE", 1, hr);
        Candidate candidate = hiringService.addCandidate(vacancy.getId(), "Кандидат Первый", "c1@test.local", "hh");

        assertThat(candidate.getStage()).isEqualTo(CandidateStage.APPLIED);

        hiringService.move(candidate.getId(), CandidateStage.SCREENING);
        hiringService.move(candidate.getId(), CandidateStage.INTERVIEW);
        Candidate offered = hiringService.move(candidate.getId(), CandidateStage.OFFER);

        assertThat(offered.getStage()).isEqualTo(CandidateStage.OFFER);
    }

    @Test
    @DisplayName("Прыжок через этап отклоняется")
    void skipStageRejected() {
        Vacancy vacancy = hiringService.create("QA Engineer", "Engineering", "JUNIOR", 1, hr);
        Candidate candidate = hiringService.addCandidate(vacancy.getId(), "Кандидат Второй", "c2@test.local", null);

        assertThatThrownBy(() -> hiringService.move(candidate.getId(), CandidateStage.OFFER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("недопустим");
    }

    @Test
    @DisplayName("Нанятый кандидат закрывает вакансию на одно место")
    void hiringClosesVacancy() {
        Vacancy vacancy = hiringService.create("DevOps", "Engineering", "SENIOR", 1, hr);
        Candidate candidate = hiringService.addCandidate(vacancy.getId(), "Кандидат Третий", "c3@test.local", null);

        hiringService.move(candidate.getId(), CandidateStage.SCREENING);
        hiringService.move(candidate.getId(), CandidateStage.INTERVIEW);
        hiringService.move(candidate.getId(), CandidateStage.OFFER);
        hiringService.move(candidate.getId(), CandidateStage.HIRED);

        Vacancy updated = vacancyRepository.findById(vacancy.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(VacancyStatus.CLOSED);
    }

    @Test
    @DisplayName("Закрытая вакансия не принимает новых кандидатов")
    void closedVacancyRejectsCandidates() {
        Vacancy vacancy = hiringService.create("Analyst", "Product", "MIDDLE", 1, hr);
        vacancysAreClosed(vacancy);

        assertThatThrownBy(() -> hiringService.addCandidate(vacancy.getId(), "Поздний", null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("не принимает");
    }

    @Test
    @DisplayName("Из финального состояния перехода нет")
    void finalStageIsTerminal() {
        Vacancy vacancy = hiringService.create("Designer", "Design", "MIDDLE", 2, hr);
        Candidate candidate = hiringService.addCandidate(vacancy.getId(), "Отклонённый", null, null);
        hiringService.move(candidate.getId(), CandidateStage.REJECTED);

        assertThatThrownBy(() -> hiringService.move(candidate.getId(), CandidateStage.SCREENING))
                .isInstanceOf(IllegalStateException.class);
    }

    private void vacancysAreClosed(Vacancy vacancy) {
        vacancy.setStatus(VacancyStatus.CLOSED);
        vacancyRepository.save(vacancy);
    }
}
