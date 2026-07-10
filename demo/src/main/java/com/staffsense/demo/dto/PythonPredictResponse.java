package com.staffsense.demo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PythonPredictResponse {

    @JsonProperty("userId")
    private Long userId;

    @JsonProperty("riskScore")
    private double riskScore;

    @JsonProperty("riskLevel")
    private String riskLevel;

    @JsonProperty("topRiskFactors")
    private List<RiskFactorDto> topRiskFactors;

    @JsonProperty("aiActionPlan")
    private String aiActionPlan;

    @JsonProperty("featuresSnapshot")
    private Map<String, Object> featuresSnapshot;

    public static class RiskFactorDto {
        @JsonProperty("factor")
        private String factor;
        @JsonProperty("impact")
        private double impact;
        @JsonProperty("description")
        private String description;

        public String getFactor() { return factor; }
        public void setFactor(String factor) { this.factor = factor; }
        public double getImpact() { return impact; }
        public void setImpact(double impact) { this.impact = impact; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public double getRiskScore() { return riskScore; }
    public void setRiskScore(double riskScore) { this.riskScore = riskScore; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public List<RiskFactorDto> getTopRiskFactors() { return topRiskFactors; }
    public void setTopRiskFactors(List<RiskFactorDto> topRiskFactors) { this.topRiskFactors = topRiskFactors; }
    public String getAiActionPlan() { return aiActionPlan; }
    public void setAiActionPlan(String aiActionPlan) { this.aiActionPlan = aiActionPlan; }
    public Map<String, Object> getFeaturesSnapshot() { return featuresSnapshot; }
    public void setFeaturesSnapshot(Map<String, Object> featuresSnapshot) { this.featuresSnapshot = featuresSnapshot; }
}
