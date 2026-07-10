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
        model.addAttribute("employees", userRepository.findAll().stream().filter(User::isActive).toList());
        return "surveys/new";
    }

    @PostMapping("/surveys/manage/new")
    public String createSurvey(@RequestParam String title,
                               @RequestParam String surveyType,
                               @RequestParam String targetScope,
                               @RequestParam String startDate,
                               @RequestParam String endDate,
                               @RequestParam(defaultValue = "false") boolean isAnonymous,
                               @RequestParam(required = false) String template,
                               @RequestParam(required = false) List<String> questionText,
                               @RequestParam(required = false) List<String> questionType,
                               Authentication auth,
                               RedirectAttributes redirectAttrs) {
        User creator = userRepository.findByEmail(auth.getName()).orElseThrow();
        Survey survey = Survey.builder()
                .title(title)
                .createdBy(creator)
                .surveyType(SurveyType.valueOf(surveyType))
                .targetScope(TargetScope.valueOf(targetScope))
                .startDate(LocalDate.parse(startDate))
                .endDate(LocalDate.parse(endDate))
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
    public String editSurveyForm(@PathVariable Long id, Model model, Authentication auth) {
        User currentUser = userRepository.findByEmail(auth.getName()).orElseThrow();
        Survey survey = surveyService.findById(id);
        List<SurveyQuestion> questions = surveyService.getQuestions(id);
        model.addAttribute("survey", survey);
        model.addAttribute("questions", questions);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("scopes", TargetScope.values());
        model.addAttribute("surveyTypes", SurveyType.values());
        model.addAttribute("employees", userRepository.findAll().stream().filter(User::isActive).toList());
        return "surveys/edit";
    }

    @PostMapping("/surveys/manage/{id}/edit")
    public String updateSurvey(@PathVariable Long id,
                               @RequestParam String title,
                               @RequestParam String surveyType,
                               @RequestParam String targetScope,
                               @RequestParam String startDate,
                               @RequestParam String endDate,
                               @RequestParam(defaultValue = "false") boolean isAnonymous,
                               @RequestParam(required = false) List<String> questionText,
                               @RequestParam(required = false) List<String> questionType,
                               Authentication auth,
                               RedirectAttributes redirectAttrs) {
        Survey survey = surveyService.findById(id);
        survey.setTitle(title);
        survey.setSurveyType(SurveyType.valueOf(surveyType));
        survey.setTargetScope(TargetScope.valueOf(targetScope));
        survey.setStartDate(LocalDate.parse(startDate));
        survey.setEndDate(LocalDate.parse(endDate));
        survey.setAnonymous(isAnonymous);
        surveyService.update(survey, questionText, questionType);
        redirectAttrs.addFlashAttribute("success", "Опрос '" + title + "' обновлён.");
        return "redirect:/surveys/manage";
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
