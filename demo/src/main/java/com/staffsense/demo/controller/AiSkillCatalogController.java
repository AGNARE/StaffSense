package com.staffsense.demo.controller;

import com.staffsense.demo.model.AiSkill;
import com.staffsense.demo.model.SkillRole;
import com.staffsense.demo.service.AiSkillCatalogService;
import com.staffsense.demo.service.TeamAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Каталог ИИ-скиллов — вкладка в боковом меню (FR-033, UC-016).
 *
 * <p>Доступен любому аутентифицированному сотруднику: просмотр карточек
 * с названием, описанием и ролью, скачивание файла и переход по ссылке
 * в репозиторий GitHub, если скилл там опубликован.
 */
@Controller
@RequiredArgsConstructor
public class AiSkillCatalogController {

    private final AiSkillCatalogService catalogService;
    private final TeamAccessService teamAccessService;

    @GetMapping("/skills")
    public String catalog(@RequestParam(value = "role", required = false) SkillRole role,
                          Model model,
                          Authentication auth) {
        model.addAttribute("currentUser", teamAccessService.requireCurrentUser(auth));
        model.addAttribute("skills", catalogService.catalog(role));
        model.addAttribute("roles", SkillRole.values());
        model.addAttribute("selectedRole", role);
        return "skills/index";
    }

    @GetMapping("/skills/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        AiSkillCatalogService.SkillFile file = catalogService.file(id);
        String encoded = URLEncoder.encode(file.name(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.data());
    }
}
