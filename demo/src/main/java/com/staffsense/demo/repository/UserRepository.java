package com.staffsense.demo.repository;

import com.staffsense.demo.model.GradeLevel;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    List<User> findByActive(boolean active);

    List<User> findByRole(Role role);

    @Query("SELECT u FROM User u WHERE u.active = :active")
    Page<User> findByActivePaged(@Param("active") boolean active, Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.active = :active AND u.role = :role")
    Page<User> findByActiveAndRolePaged(@Param("active") boolean active, @Param("role") Role role, Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.department = :department AND u.active = :active")
    Page<User> findByDepartmentAndActivePaged(@Param("department") String department, @Param("active") boolean active, Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.active = :active AND u.role = :role")
    List<User> findActiveByRole(@Param("active") boolean active, @Param("role") Role role);

    List<User> findByGradeLevelAndActive(GradeLevel gradeLevel, boolean active);

    List<User> findByDepartmentAndActive(String department, boolean active);

    // ── Ограничение видимости «своей командой» (manager_id) ──────────────

    /** Активные сотрудники, у которых руководитель — managerId. */
    @Query("SELECT u FROM User u WHERE u.active = :active AND u.manager.id = :managerId")
    List<User> findActiveByManager(@Param("managerId") Long managerId, @Param("active") boolean active);

    /** То же, но с пагинацией. */
    @Query("SELECT u FROM User u WHERE u.active = :active AND u.manager.id = :managerId")
    Page<User> findByManagerAndActivePaged(@Param("managerId") Long managerId,
                                           @Param("active") boolean active,
                                           Pageable pageable);

    /** То же с фильтром по подразделению. */
    @Query("SELECT u FROM User u WHERE u.active = :active AND u.manager.id = :managerId AND u.department = :department")
    Page<User> findByManagerAndDepartmentAndActivePaged(@Param("managerId") Long managerId,
                                                        @Param("department") String department,
                                                        @Param("active") boolean active,
                                                        Pageable pageable);

    /** Сотрудники команды с фильтром по роли — для страницы рисков (PM). */
    @Query("SELECT u FROM User u WHERE u.active = :active AND u.role = :role AND u.manager.id = :managerId")
    Page<User> findByManagerAndActiveAndRolePaged(@Param("managerId") Long managerId,
                                                  @Param("active") boolean active,
                                                  @Param("role") Role role,
                                                  Pageable pageable);

    // ── Сортировка по риску на уровне запроса ────────────────────────────
    //
    //  Раньше страница выбиралась по алфавиту, а сортировка по риску делалась
    //  в памяти уже после пагинации: на 21 сотруднике это незаметно, на 500 —
    //  первая страница содержала бы не самых рискованных.
    //
    //  COALESCE(p.riskScore, -1) кладёт сотрудников без прогноза в конец:
    //  риск у них неизвестен, а не нулевой.
    //
    //  LEFT JOIN на прогнозы корректен, пока BR-010 хранит ровно один
    //  актуальный прогноз на сотрудника (история — отдельная таблица).

    /** Страница сотрудников по убыванию риска увольнения. */
    @Query(value = """
            SELECT u FROM User u
            LEFT JOIN AttritionPrediction p ON p.user = u
            WHERE u.active = :active AND u.role = :role
            ORDER BY COALESCE(p.riskScore, -1) DESC, u.fullName ASC
            """,
            countQuery = "SELECT COUNT(u) FROM User u WHERE u.active = :active AND u.role = :role")
    Page<User> findByActiveAndRoleOrderByRisk(@Param("active") boolean active,
                                              @Param("role") Role role,
                                              Pageable pageable);

    /** То же для руководителя: только сотрудники его команды. */
    @Query(value = """
            SELECT u FROM User u
            LEFT JOIN AttritionPrediction p ON p.user = u
            WHERE u.active = :active AND u.role = :role AND u.manager.id = :managerId
            ORDER BY COALESCE(p.riskScore, -1) DESC, u.fullName ASC
            """,
            countQuery = """
            SELECT COUNT(u) FROM User u
            WHERE u.active = :active AND u.role = :role AND u.manager.id = :managerId
            """)
    Page<User> findByManagerAndActiveAndRoleOrderByRisk(@Param("managerId") Long managerId,
                                                        @Param("active") boolean active,
                                                        @Param("role") Role role,
                                                        Pageable pageable);
    /**
     * Поиск сотрудников для каталога: фильтр по отделу, поиск по имени/отделу,
     * при необходимости — только своя команда (для PM).
     *
     * <p>Поиск сделан одним запросом с всегда заданными параметрами
     * ({@code pattern} = {@code %%} для «без поиска», {@code department} = '' для
     * «все отделы»), чтобы не строить JPQL строками и не ловить разное поведение
     * при NULL-параметрах в PostgreSQL.
     */
    @Query("""
            SELECT u FROM User u
            WHERE u.active = true AND u.role = com.staffsense.demo.model.Role.EMPLOYEE
              AND (:managerId IS NULL OR u.manager.id = :managerId)
              AND (:department = '' OR u.department = :department)
              AND (LOWER(u.fullName) LIKE :pattern OR LOWER(u.department) LIKE :pattern)
            ORDER BY u.fullName ASC
            """)
    Page<User> searchEmployees(@Param("managerId") Long managerId,
                               @Param("department") String department,
                               @Param("pattern") String pattern,
                               Pageable pageable);
}