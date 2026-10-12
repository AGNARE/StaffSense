package com.staffsense.demo.controller;

import com.staffsense.demo.model.*;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.service.CsvExportService;
import com.staffsense.demo.service.SurveyService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class SurveyController {

    private final SurveyService surveyService;
    private final UserRepository userRepository;
    private final CsvExportService csvExportService;

    public SurveyController(SurveyService surveyService, UserRepository userRepository,
                            CsvExportService csvExportService) {
        this.surveyService = surveyService;
        this.userRepository = userRepository;
        this.csvExportService = csvExportService;
    }

    // --- Management ---

    @GetMapping("/surveys/manage")
    public String manageSurveys(@RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "10") int size,
                                Model model, Authentication auth) {
        User currentUser = userRepository.findByEmail(auth.getName()).orElseThrow();
        Pageable pageable = PageRequest.of(page, size, Sort.by("startDate").descending());
        Page<Survey> surveyPage = surveyService.findAll(pageable);
        Map<Long, Long> responseCounts = new java.util.HashMap<>();
        for (Survey s : surveyPage.getContent()) {
            responseCounts.put(s.getId(), surveyService.getResponseCount(s.getId()));
        }
        model.addAttribute("surveyPage", surveyPage);
        model.addAttribute("surveys", surveyPage.getContent());
        model.addAttribute("responseCounts", responseCounts);
        model.addAttribute("currentUser", currentUser);
        return "surveys/manage";
    }

    @GetMapping("/surveys/manage/new")
    public String newSurveyForm(Model model, Authentication auth) {
        User currentUser = userRepository.findByEmail(auth.getName()).orElseThrow();
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("scopes", TargetScope.values());
        model.addAttribute("surveyTypes", SurveyType.values());
        addTargetingOptions(model);
        return "surveys/new";
    }

    /** Списки для выбора параметров охвата (отдел / руководитель / сотрудник). */
    private void addTargetingOptions(Model model) {
        List<User> active = userRepository.findByActive(true);
        model.addAttribute("departments", active.stream()
                .map(User::getDepartment)
                .filter(d -> d != null && !d.isBlank())
                .distinct()
                .sorted()
                .toList());
        model.addAttribute("managers", active.stream()
                .filter(u -> u.getRole() == Role.PM || u.getRole() == Role.ADMIN || u.getRole() == Role.HR)
                .toList());
        model.addAttribute("employees", active);
    }

    @PostMapping("/surveys/manage/new")
    public String createSurvey(@RequestParam String title,
                               @RequestParam String surveyType,
                               @RequestParam String targetScope,
                               @RequestParam(required = false) String targetDepartment,
                               @RequestParam(required = false) Long targetManagerId,
                               @RequestParam(required = false) Long targetUserId,
                               @RequestParam String startDate,
                               @RequestParam String endDate,
                               @RequestParam(defaultValue = "false") boolean isAnonymous,
                               @RequestParam(required = false) String template,
                               @RequestParam(required = false) List<String> questionText,
                               @RequestParam(required = false) List<String> questionType,
                               Authentication auth,
                               RedirectAttributes redirectAttrs) {
        User creator = userRepository.findByEmail(auth.getName()).orElseThrow();
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        TargetScope scope = TargetScope.valueOf(targetScope);

        // Спецификация §6.4: end_date >= start_date. Иначе опрос создавался
        // «мёртвым»: getVisibleForEmployee не показывал его никому.
        if (end.isBefore(start)) {
            redirectAttrs.addFlashAttribute("error",
                    "Дата окончания не может быть раньше даты начала.");
            return "redirect:/surveys/manage/new";
        }

        // Параметры охвата ранее не принимались контроллером, из-за чего
        // DEPARTMENT/TEAM/INDIVIDUAL-опросы не показывались ни одному сотруднику.
        String targetingError = validateTargeting(scope, targetDepartment, targetManagerId, targetUserId);
        if (targetingError != null) {
            redirectAttrs.addFlashAttribute("error", targetingError);
            return "redirect:/surveys/manage/new";
        }

        Survey survey = Survey.builder()
                .title(title)
                .createdBy(creator)
                .surveyType(SurveyType.valueOf(surveyType))
                .targetScope(scope)
                .targetDepartment(scope == TargetScope.DEPARTMENT ? trimToNull(targetDepartment) : null)
                .targetManagerId(scope == TargetScope.TEAM ? targetManagerId : null)
                .targetUserId(scope == TargetScope.INDIVIDUAL ? targetUserId : null)
                .startDate(start)
                .endDate(end)
                .anonymous(isAnonymous)
                .status(SurveyStatus.DRAFT)
                .build();
        survey = surveyService.save(survey);

        // If a template was selected (not "custom"), load questions from template
        if (template != null && !template.isEmpty() && !"custom".equals(template)) {
            surveyService.saveTemplateQuestions(survey, template);
        } else if (questionText != null && questionType != null) {
            // Save custom questions from form
            surveyService.saveCustomQuestions(survey, questionText, questionType);
        }

        redirectAttrs.addFlashAttribute("success", "Опрос '" + title + "' создан (черновик). Добавьте вопросы и активируйте.");
        return "redirect:/surveys/manage";
    }

    // --- Edit DRAFT survey ---

    @GetMapping("/surveys/manage/{id}/edit")
    public String editSurveyForm(@PathVariable Long id, Model model, Authentication auth,
                                 RedirectAttributes redirectAttrs) {
        Survey survey = surveyService.findById(id);

        // BR-013: редактирование только для черновика.
        if (survey.getStatus() != SurveyStatus.DRAFT) {
            redirectAttrs.addFlashAttribute("error",
                    "Редактирование доступно только для опроса в статусе DRAFT.");
            return "redirect:/surveys/manage";
        }

        User currentUser = userRepository.findByEmail(auth.getName()).orElseThrow();
        List<SurveyQuestion> questions = surveyService.getQuestions(id);
        model.addAttribute("survey", survey);
        model.addAttribute("questions", questions);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("scopes", TargetScope.values());
        model.addAttribute("surveyTypes", SurveyType.values());
        addTargetingOptions(model);
        return "surveys/edit";
    }

    @PostMapping("/surveys/manage/{id}/edit")
    public String updateSurvey(@PathVariable Long id,
                               @RequestParam String title,
                               @RequestParam String surveyType,
                               @RequestParam String targetScope,
                               @RequestParam(required = false) String targetDepartment,
                               @RequestParam(required = false) Long targetManagerId,
                               @RequestParam(required = false) Long targetUserId,
                               @RequestParam String startDate,
                               @RequestParam String endDate,
                               @RequestParam(defaultValue = "false") boolean isAnonymous,
                               @RequestParam(required = false) List<String> questionText,
                               @RequestParam(required = false) List<String> questionType,
                               Authentication auth,
                               RedirectAttributes redirectAttrs) {
        Survey survey = surveyService.findById(id);
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        TargetScope scope = TargetScope.valueOf(targetScope);

        // Спецификация §6.4: end_date >= start_date.
        if (end.isBefore(start)) {
            redirectAttrs.addFlashAttribute("error",
                    "Дата окончания не может быть раньше даты начала.");
            return "redirect:/surveys/manage/" + id + "/edit";
        }

        String targetingError = validateTargeting(scope, targetDepartment, targetManagerId, targetUserId);
        if (targetingError != null) {
            redirectAttrs.addFlashAttribute("error", targetingError);
            return "redirect:/surveys/manage/" + id + "/edit";
        }

        survey.setTitle(title);
        survey.setSurveyType(SurveyType.valueOf(surveyType));
        survey.setTargetScope(scope);
        survey.setTargetDepartment(scope == TargetScope.DEPARTMENT ? trimToNull(targetDepartment) : null);
        survey.setTargetManagerId(scope == TargetScope.TEAM ? targetManagerId : null);
        survey.setTargetUserId(scope == TargetScope.INDIVIDUAL ? targetUserId : null);
        survey.setStartDate(start);
        survey.setEndDate(end);
        survey.setAnonymous(isAnonymous);

        try {
            surveyService.update(survey, questionText, questionType);
            redirectAttrs.addFlashAttribute("success", "Опрос '" + title + "' обновлён.");
        } catch (IllegalStateException e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/surveys/manage";
    }

    /**
     * Проверяет, что для выбранного охвата задан соответствующий параметр.
     * Без этого опрос создавался, но не показывался ни одному сотруднику
     * (getVisibleForEmployee отфильтровывал его по isTargeted).
     */
    private String validateTargeting(TargetScope scope, String department, Long managerId, Long userId) {
        return switch (scope) {
            case DEPARTMENT -> isBlank(department) ? "Для охвата DEPARTMENT выберите отдел." : null;
            case TEAM -> managerId == null ? "Для охвата TEAM выберите руководителя." : null;
            case INDIVIDUAL -> userId == null ? "Для охвата INDIVIDUAL выберите сотрудника." : null;
            case COMPANY -> null;
        };
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    // --- Analytics / Results ---

    @GetMapping("/surveys/manage/{id}/export/csv")
    public void exportCsv(@PathVariable Long id, HttpServletResponse response) {
        csvExportService.exportSurveyResultsToCsv(id, response);
    }

    @GetMapping("/surveys/manage/{id}/results")
    public String surveyResults(@PathVariable Long id, Model model, Authentication auth) {
        User currentUser = userRepository.findByEmail(auth.getName()).orElseThrow();
        Survey survey = surveyService.findById(id);
        SurveyService.SurveyAnalytics analytics = surveyService.getAnalytics(id);
        long responseCount = surveyService.getResponseCount(id);
        double averageScore = analytics.questions().stream()
                .mapToDouble(SurveyService.SurveyQuestionAnalytics::average)
                .average().orElse(0.0);
        model.addAttribute("survey", survey);
        model.addAttribute("analytics", analytics);
        model.addAttribute("responseCount", responseCount);
        model.addAttribute("averageScore", averageScore);
        model.addAttribute("currentUser", currentUser);
        return "surveys/results";
    }

    @PostMapping("/surveys/manage/{id}/activate")
    public String activate(@PathVariable Long id) {
        surveyService.activate(id);
        return "redirect:/surveys/manage";
    }

    @PostMapping("/surveys/manage/{id}/close")
    public String close(@PathVariable Long id) {
        surveyService.close(id);
        return "redirect:/surveys/manage";
    }

    // --- Employee takes survey ---

    @GetMapping("/surveys/take/{id}")
    public String takeSurvey(@PathVariable Long id, Model model, Authentication auth) {
        Survey survey = surveyService.findById(id);
        List<SurveyQuestion> questions = surveyService.getQuestions(id);
        User currentUser = userRepository.findByEmail(auth.getName()).orElseThrow();
        model.addAttribute("survey", survey);
        model.addAttribute("questions", questions);
        model.addAttribute("currentUser", currentUser);
        return "surveys/take";
    }

    @PostMapping("/surveys/take/{id}")
    public String submitSurvey(@PathVariable Long id,
                               @RequestParam Map<String, String> params,
                               Authentication auth,
                               RedirectAttributes redirectAttrs) {
        Survey survey = surveyService.findById(id);
        User user = userRepository.findByEmail(auth.getName()).orElseThrow();

        Map<Long, String> answers = new HashMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getKey().startsWith("question_")) {
                try {
                    Long qId = Long.parseLong(entry.getKey().replace("question_", ""));
                    answers.put(qId, entry.getValue());
                } catch (NumberFormatException ignored) {}
            }
        }
        surveyService.submitResponse(survey, user, answers);
        redirectAttrs.addFlashAttribute("success", "Спасибо! Ваш ответ сохранён.");
        return "redirect:/dashboard/personal";
    }
}
