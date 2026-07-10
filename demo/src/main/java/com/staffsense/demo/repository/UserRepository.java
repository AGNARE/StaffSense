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
}
