package com.staffsense.demo.service;

import com.staffsense.demo.model.*;
import com.staffsense.demo.repository.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class CsvExportService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final UserRepository userRepository;
    private final AttritionPredictionRepository predictionRepository;
    private final SurveyResponseRepository surveyResponseRepository;
    private final SurveyAnswerRepository surveyAnswerRepository;
    private final SurveyQuestionRepository surveyQuestionRepository;

    public CsvExportService(UserRepository userRepository,
                            AttritionPredictionRepository predictionRepository,
                            SurveyResponseRepository surveyResponseRepository,
                            SurveyAnswerRepository surveyAnswerRepository,
                            SurveyQuestionRepository surveyQuestionRepository) {
        this.userRepository = userRepository;
        this.predictionRepository = predictionRepository;
        this.surveyResponseRepository = surveyResponseRepository;
        this.surveyAnswerRepository = surveyAnswerRepository;
        this.surveyQuestionRepository = surveyQuestionRepository;
    }

    /**
     * Writes BOM (byte order mark) for Excel compatibility with UTF-8.
     */
    private void writeBom(PrintWriter writer) {
        writer.write('﻿');
    }

    /**
     * Escapes a CSV value: wraps in double quotes if it contains a comma, double quote, or newline.
     * Doubles any internal double quotes.
     */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Exports all active employees to CSV.
     */
    public void exportEmployeesToCsv(HttpServletResponse response) {
        try {
            response.setContentType("text/csv; charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=employees.csv");

            PrintWriter writer = response.getWriter();
            writeBom(writer);

            // Header
            writer.println("Full Name,Email,Role,Department,Position,Grade,Salary,Hire Date,Active,Manager");

            // Data
            List<User> employees = userRepository.findByActive(true);
            for (User emp : employees) {
                StringBuilder line = new StringBuilder();
                line.append(escapeCsv(emp.getFullName())).append(",");
                line.append(escapeCsv(emp.getEmail())).append(",");
                line.append(escapeCsv(emp.getRole().name())).append(",");
                line.append(escapeCsv(emp.getDepartment())).append(",");
                line.append(escapeCsv(emp.getPosition())).append(",");
                line.append(escapeCsv(emp.getGradeLevel().name())).append(",");
                line.append(emp.getSalary() != null ? emp.getSalary().toPlainString() : "").append(",");
                line.append(emp.getHireDate() != null ? emp.getHireDate().format(DATE_FORMATTER) : "").append(",");
                line.append(emp.isActive() ? "Yes" : "No").append(",");
                line.append(escapeCsv(emp.getManager() != null ? emp.getManager().getFullName() : ""));
                writer.println(line);
            }

            writer.flush();
        } catch (Exception e) {
            throw new RuntimeException("Failed to export employees CSV", e);
        }
    }

    /**
     * Exports risk data for active EMPLOYEE users to CSV.
     */
    public void exportRisksToCsv(HttpServletResponse response) {
        try {
            response.setContentType("text/csv; charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=risks.csv");

            PrintWriter writer = response.getWriter();
            writeBom(writer);

            // Header
            writer.println("Full Name,Department,Position,Grade,Risk Score,Risk Level,Last Computed");

            // Data
            List<User> employees = userRepository.findActiveByRole(true, Role.EMPLOYEE);
            for (User emp : employees) {
                Optional<AttritionPrediction> latestPred = predictionRepository.findAll().stream()
                        .filter(p -> p.getUser().getId().equals(emp.getId()))
                        .max(Comparator.comparing(AttritionPrediction::getComputedAt));

                StringBuilder line = new StringBuilder();
                line.append(escapeCsv(emp.getFullName())).append(",");
                line.append(escapeCsv(emp.getDepartment())).append(",");
                line.append(escapeCsv(emp.getPosition())).append(",");
                line.append(escapeCsv(emp.getGradeLevel().name())).append(",");

                if (latestPred.isPresent()) {
                    AttritionPrediction pred = latestPred.get();
                    line.append(String.format("%.1f", pred.getRiskScore())).append(",");
                    line.append(escapeCsv(pred.getRiskLevel().name())).append(",");
                    line.append(pred.getComputedAt() != null ? pred.getComputedAt().format(DATETIME_FORMATTER) : "");
                } else {
                    line.append(",").append(",");
                }

                writer.println(line);
            }

            writer.flush();
        } catch (Exception e) {
            throw new RuntimeException("Failed to export risks CSV", e);
        }
    }

    /**
     * Exports survey results for a given survey to CSV.
     */
    public void exportSurveyResultsToCsv(Long surveyId, HttpServletResponse response) {
        try {
            response.setContentType("text/csv; charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=survey-" + surveyId + "-results.csv");

            PrintWriter writer = response.getWriter();
            writeBom(writer);

            // Header
            writer.println("User,Question,Answer,Submitted At");

            // Data
            List<SurveyResponse> responses = surveyResponseRepository.findBySurveyId(surveyId);
            for (SurveyResponse resp : responses) {
                String userName = "Anonymous";
                if (resp.getUserId() != null) {
                    userName = userRepository.findById(resp.getUserId())
                            .map(User::getFullName)
                            .orElse("Anonymous");
                }

                List<SurveyAnswer> answers = surveyAnswerRepository.findByResponseId(resp.getId());
                for (SurveyAnswer answer : answers) {
                    StringBuilder line = new StringBuilder();
                    line.append(escapeCsv(userName)).append(",");
                    line.append(escapeCsv(answer.getQuestion() != null ? answer.getQuestion().getText() : "")).append(",");

                    String answerValue = "";
                    if (answer.getLikertValue() != null) {
                        answerValue = String.valueOf(answer.getLikertValue());
                    } else if (answer.getTextValue() != null) {
                        answerValue = answer.getTextValue();
                    }
                    line.append(escapeCsv(answerValue)).append(",");
                    line.append(resp.getSubmittedAt() != null ? resp.getSubmittedAt().format(DATETIME_FORMATTER) : "");

                    writer.println(line);
                }
            }

            writer.flush();
        } catch (Exception e) {
            throw new RuntimeException("Failed to export survey results CSV", e);
        }
    }
}
