package com.staffsense.demo.repository;

import com.staffsense.demo.model.AttritionPrediction;
import com.staffsense.demo.model.GradeLevel;
import com.staffsense.demo.model.RiskLevel;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверяет, что сортировка риска выполняется на уровне запроса.
 *
 * <p>До этого изменения страница выбиралась по алфавиту и сортировалась по
 * риску в памяти: на 21 сотруднике это незаметно, на 500 — первая страница
 * показывала бы не самых рискованных. Тест фиксирует порядок и то, что
 * сотрудники без прогноза уходят в конец.
 */
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@TestPropertySource(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect")
class UserRepositoryRiskOrderTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AttritionPredictionRepository predictionRepository;

    private User highRisk;
    private User mediumRisk;
    private User withoutPrediction;

    @BeforeEach
    void seed() {
        // Без прогноза — проверяем, что попадает в конец, а не в начало
        withoutPrediction = userRepository.save(employee("Абрамов Пётр"));
        mediumRisk = userRepository.save(employee("Белов Иван"));
        highRisk = userRepository.save(employee("Ветров Олег"));

        predictionRepository.save(prediction(mediumRisk, 41.5, RiskLevel.MEDIUM));
        predictionRepository.save(prediction(highRisk, 87.0, RiskLevel.HIGH));
    }

    @Test
    @DisplayName("Порядок: высокий риск → средний → без прогноза")
    void ordersByRiskDescending() {
        Page<User> page = userRepository.findByActiveAndRoleOrderByRisk(
                true, Role.EMPLOYEE, PageRequest.of(0, 10));

        assertThat(page.getContent())
                .extracting(User::getId)
                .containsExactly(highRisk.getId(), mediumRisk.getId(), withoutPrediction.getId());
    }

    @Test
    @DisplayName("Пагинация сохраняет порядок: вторая страница — не алфавитный остаток")
    void paginationKeepsOrder() {
        Page<User> first = userRepository.findByActiveAndRoleOrderByRisk(
                true, Role.EMPLOYEE, PageRequest.of(0, 1));
        Page<User> second = userRepository.findByActiveAndRoleOrderByRisk(
                true, Role.EMPLOYEE, PageRequest.of(1, 1));
        Page<User> third = userRepository.findByActiveAndRoleOrderByRisk(
                true, Role.EMPLOYEE, PageRequest.of(2, 1));

        assertThat(first.getContent()).extracting(User::getId).containsExactly(highRisk.getId());
        assertThat(second.getContent()).extracting(User::getId).containsExactly(mediumRisk.getId());
        assertThat(third.getContent()).extracting(User::getId).containsExactly(withoutPrediction.getId());
        assertThat(first.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("Фильтр по руководителю видит только свою команду")
    void managerScopeFilters() {
        User manager = userRepository.save(manager());

        User inTeam = employee("Громов Андрей");
        inTeam.setManager(manager);
        inTeam = userRepository.save(inTeam);
        predictionRepository.save(prediction(inTeam, 66.0, RiskLevel.HIGH));

        Page<User> page = userRepository.findByManagerAndActiveAndRoleOrderByRisk(
                manager.getId(), true, Role.EMPLOYEE, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(User::getId).containsExactly(inTeam.getId());
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    private User employee(String fullName) {
        return User.builder()
                .fullName(fullName)
                .email(fullName.replace(" ", ".").toLowerCase() + "@test.local")
                .passwordHash("hash")
                .role(Role.EMPLOYEE)
                .department("Engineering")
                .position("Developer")
                .salary(new BigDecimal("90000"))
                .gradeLevel(GradeLevel.MIDDLE)
                .weeklyCapacityHours(40)
                .hireDate(LocalDate.of(2023, 1, 10))
                .active(true)
                .build();
    }

    private User manager() {
        return User.builder()
                .fullName("Козлов Дмитрий")
                .email("manager@test.local")
                .passwordHash("hash")
                .role(Role.PM)
                .department("Product")
                .position("PM")
                .salary(new BigDecimal("130000"))
                .gradeLevel(GradeLevel.SENIOR)
                .weeklyCapacityHours(40)
                .hireDate(LocalDate.of(2021, 6, 10))
                .active(true)
                .build();
    }

    private AttritionPrediction prediction(User user, double score, RiskLevel level) {
        return AttritionPrediction.builder()
                .user(user)
                .computedAt(LocalDateTime.now())
                .riskScore(score)
                .riskLevel(level)
                .build();
    }
}
