package com.staffsense.demo.dto;

import java.time.LocalDateTime;

public class EmployeeRiskDto {
    private Long userId;
    private String fullName;
    private String department;
    private String position;
    private String gradeLevel;
    private double riskScore;
    private String riskLevel;
    private LocalDateTime computedAt;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }
    public String getGradeLevel() { return gradeLevel; }
    public void setGradeLevel(String gradeLevel) { this.gradeLevel = gradeLevel; }
    public double getRiskScore() { return riskScore; }
    public void setRiskScore(double riskScore) { this.riskScore = riskScore; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public LocalDateTime getComputedAt() { return computedAt; }
    public void setComputedAt(LocalDateTime computedAt) { this.computedAt = computedAt; }

    /** Дельта риска к предыдущему снимку, п.п. (null — истории меньше двух точек). */
    private Double trendDelta;

    /** Главный фактор риска для строки списка (null — объяснение не сохранено). */
    private String topFactor;

    public Double getTrendDelta() { return trendDelta; }
    public void setTrendDelta(Double trendDelta) { this.trendDelta = trendDelta; }
    public String getTopFactor() { return topFactor; }
    public void setTopFactor(String topFactor) { this.topFactor = topFactor; }
}
