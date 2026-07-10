package com.staffsense.demo.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 150)
    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @NotBlank
    @Email
    @Size(max = 150)
    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @NotBlank
    @Size(max = 255)
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String department;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String position;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private User manager;

    @NotNull
    @DecimalMin(value = "0.01", message = "Salary must be greater than zero")
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal salary;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "grade_level", nullable = false, length = 20)
    private GradeLevel gradeLevel;

    @NotNull
    @Min(1)
    @Column(name = "weekly_capacity_hours", nullable = false)
    @Builder.Default
    private Integer weeklyCapacityHours = 40;

    @NotNull
    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    @NotNull
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return this.passwordHash;
    }

    @Override
    public String getUsername() {
        return this.email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return Boolean.TRUE.equals(active);
    }

    @Override
    public boolean isAccountNonLocked() {
        return Boolean.TRUE.equals(active);
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return Boolean.TRUE.equals(active);
    }

    @Override
    public boolean isEnabled() {
        return Boolean.TRUE.equals(active);
    }
}
