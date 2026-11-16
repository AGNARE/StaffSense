package com.staffsense.demo.api.hr;

import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.service.hr.VacationBalanceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Баланс отпусков: начисление права и остатки.
 *
 * <p>Начисление — действие кадровой службы, чтение открыто управленческим
 * ролям. Сотрудник видит свой баланс через отдельную ручку.
 */
@RestController
@RequestMapping("/api/v1/hr/vacations")
@Tag(name = "Отпуска", description = "Право на отпуск и остатки по сотрудникам")
public class VacationApiController {

    private final VacationBalanceService balanceService;
    private final UserRepository userRepository;

    public VacationApiController(VacationBalanceService balanceService,
                                 UserRepository userRepository) {
        this.balanceService = balanceService;
        this.userRepository = userRepository;
    }

    /** Баланс по всем сотрудникам за год. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','HR','EXEC')")
    public List<VacationBalanceService.Balance> list(
            @RequestParam(required = false) Integer year) {
        return balanceService.forYear(year != null ? year : LocalDate.now().getYear());
    }

    /** Свой баланс — доступен любому авторизованному. */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public VacationBalanceService.Balance mine(Authentication auth,
                                               @RequestParam(required = false) Integer year) {
        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new com.staffsense.demo.exception.ResourceNotFoundException(
                        "Пользователь не найден: " + auth.getName()));
        return balanceService.forUser(user, year != null ? year : LocalDate.now().getYear());
    }

    /** Начислить право на отпуск сотруднику за год. */
    @PostMapping("/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public VacationBalanceService.Balance grant(@PathVariable Long userId,
                                                @RequestBody GrantRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new com.staffsense.demo.exception.ResourceNotFoundException(
                        "Сотрудник не найден: " + userId));

        int year = request.year() != null ? request.year() : LocalDate.now().getYear();
        balanceService.grant(user, year, request.entitledDays(),
                request.carriedOverDays(), request.note());
        return balanceService.forUser(user, year);
    }

    /** Тело начисления. */
    public record GrantRequest(
            Integer year,
            @DecimalMin(value = "0", message = "Дней не может быть меньше нуля")
            BigDecimal entitledDays,
            @DecimalMin(value = "0", message = "Перенос не может быть отрицательным")
            BigDecimal carriedOverDays,
            String note
    ) {
    }
}
