package com.staffsense.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PythonPredictRequest {

    @JsonProperty("userId")
    private Long userId;

    @JsonProperty("salary_ratio")
    private double salaryRatio;

    @JsonProperty("workload_index")
    private double workloadIndex;

    @JsonProperty("kpi_norm")
    private double kpiNorm;

    @JsonProperty("tenure_months_norm")
    private double tenureMonthsNorm;

    @JsonProperty("satisfaction_norm")
    private double satisfactionNorm;

    @JsonProperty("missed_1on1_ratio")
    private double missed1on1Ratio;

    @JsonProperty("overtime_norm")
    private double overtimeNorm;

    // Getters and setters
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public double getSalaryRatio() { return salaryRatio; }
    public void setSalaryRatio(double salaryRatio) { this.salaryRatio = salaryRatio; }
    public double getWorkloadIndex() { return workloadIndex; }
    public void setWorkloadIndex(double workloadIndex) { this.workloadIndex = workloadIndex; }
    public double getKpiNorm() { return kpiNorm; }
    public void setKpiNorm(double kpiNorm) { this.kpiNorm = kpiNorm; }
    public double getTenureMonthsNorm() { return tenureMonthsNorm; }
    public void setTenureMonthsNorm(double tenureMonthsNorm) { this.tenureMonthsNorm = tenureMonthsNorm; }
    public double getSatisfactionNorm() { return satisfactionNorm; }
    public void setSatisfactionNorm(double satisfactionNorm) { this.satisfactionNorm = satisfactionNorm; }
    public double getMissed1on1Ratio() { return missed1on1Ratio; }
    public void setMissed1on1Ratio(double missed1on1Ratio) { this.missed1on1Ratio = missed1on1Ratio; }
    public double getOvertimeNorm() { return overtimeNorm; }
    public void setOvertimeNorm(double overtimeNorm) { this.overtimeNorm = overtimeNorm; }
}
