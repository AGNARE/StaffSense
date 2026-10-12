package com.staffsense.demo.service;

import com.staffsense.demo.model.*;
import com.staffsense.demo.repository.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class CsvExportService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    /**
     * Символы, с которых начинаются формулы в Excel/LibreOffice/Google Sheets.
     * Значение, начинающееся с любого из них, экранируется апострофом — иначе
     * CSV-инъекция (например {@code =HYPERLINK(...)} или {@code =cmd|'/c calc'!A0}).
     */
    private static final String FORMULA_PREFIXES = "=+-@\t\r";

    private final UserRepository userRepository;
    private final AttritionPredictionRepository predictionRepository;
    private final SurveyResponseRepository surveyResponseRepository;
    private final SurveyAnswerRepository surveyAnswerRepository;
    private final SurveyQuestionRepository surveyQuestionRepository;
    private final TeamAccessService teamAccessService;

    public CsvExportService(UserRepository userRepository,
                            AttritionPredictionRepository predictionRepository,
                            SurveyResponseRepository surveyResponseRepository,
                            SurveyAnswerRepository surveyAnswerRepository,
                            SurveyQuestionRepository surveyQuestionRepository,
                            TeamAccessService teamAccessService) {
        this.userRepository = userRepository;
        this.predictionRepository = predictionRepository;
        this.surveyResponseRepository = surveyResponseRepository;
        this.surveyAnswerRepository = surveyAnswerRepository;
        this.surveyQuestionRepository = surveyQuestionRepository;
        this.teamAccessService = teamAccessService;
    }

    /**
     * Writes BOM (byte order mark) for Excel compatibility with UTF-8.
     */
    private void writeBom(PrintWriter writer) {
        writer.write('\uFEFF');
    }

    /**
     * Экранирует значение CSV:
     * <ol>
     *   <li>нейтрализует формулы (префикс {@code '} для {@code = + - @ TAB CR});</li>
     *   <li>заключает в кавычки при наличии запятой, кавычки или перевода строки;</li>
     *   <li>удваивает внутренние кавычки.</li>
     * </ol>
     */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        String safe = value;
        if (!safe.isEmpty() && FORMULA_PREFIXES.indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    /**
     * Экспорт сотрудников. PM получает только свою команду (плюс себя),
     * ADMIN/HR — всех активных.
     */
    public void exportEmployeesToCsv(HttpServletResponse response, User actor) {
        try {
            response.setContentType("text/csv; charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=employees.csv");

            PrintWriter writer = response.getWriter();
            writeBom(writer);

            writer.println("Full Name,Email,Role,Department,Position,Grade,Salary,Hire Date,Active,Manager");

            List<User> employees = visibleEmployees(actor);
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
     * Экспорт рисков. PM получает только свою команду.
     */
    public void exportRisksToCsv(HttpServletResponse response, User actor) {
        try {
            response.setContentType("text/csv; charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=risks.csv");

            PrintWriter writer = response.getWriter();
            writeBom(writer);

            writer.println("Full Name,Department,Position,Grade,Risk Score,Risk Level,Last Computed");

            List<User> employees = userRepository.findActiveByRole(true, Role.EMPLOYEE);
            for (User emp : employees) {
                if (!teamAccessService.canViewEmployee(actor, emp)) {
                    continue;
                }
                Optional<AttritionPrediction> latestPred =
                        predictionRepository.findFirstByUserIdOrderByComputedAtDesc(emp.getId());

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
     * Экспорт результатов опроса.
     */
    public void exportSurveyResultsToCsv(Long surveyId, HttpServletResponse response) {
        try {
            response.setContentType("text/csv; charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=survey-" + surveyId + "-results.csv");

            PrintWriter writer = response.getWriter();
            writeBom(writer);

            writer.println("User,Question,Answer,Submitted At");

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

    /** Список сотрудников, видимых пользователю (PM — только своя команда). */
    private List<User> visibleEmployees(User actor) {
        if (actor != null && actor.getRole() == Role.PM) {
            List<User> team = new ArrayList<>(userRepository.findActiveByManager(actor.getId(), true));
            team.add(actor);
            return team;
        }
        return userRepository.findByActive(true);
    }
}
