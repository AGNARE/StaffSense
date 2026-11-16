package com.staffsense.demo.api.risk;

import com.staffsense.demo.service.risk.RiskCostService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Стоимость под риском для радара рисков.
 *
 * <p>Возвращает сумму и заложенные коэффициенты. Если конфигурация пуста,
 * {@code enabled=false} и {@code total=null} — интерфейс показывает прочерк,
 * а не ноль: «не считали» и «вышло ноль» — разные утверждения.
 */
@RestController
@RequestMapping("/api/v1/risk/cost")
@Tag(name = "Стоимость риска", description = "Стоимость под риском по компании")
public class RiskCostApiController {

    private final RiskCostService costService;

    public RiskCostApiController(RiskCostService costService) {
        this.costService = costService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','EXEC','HR','PM')")
    public CostDto cost() {
        BigDecimal total = costService.total().orElse(null);
        Map<String, Integer> months = new java.util.LinkedHashMap<>();
        costService.monthsByGrade().forEach((grade, value) -> months.put(grade.name(), value));

        return new CostDto(costService.isEnabled(), "KGS", total, months);
    }

    /**
     * @param enabled      включён ли расчёт (заполнена ли конфигурация)
     * @param currency     валюта расчёта
     * @param total        сумма под риском или null, если расчёт отключён
     * @param monthsByGrade заложенные месяцы оклада по грейдам
     */
    public record CostDto(
            boolean enabled,
            String currency,
            BigDecimal total,
            Map<String, Integer> monthsByGrade
    ) {
    }
}
