package com.staffsense.demo.api.memo;

import com.staffsense.demo.model.GradeLevel;
import com.staffsense.demo.model.MemoStatus;
import com.staffsense.demo.model.MemoType;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.ServiceMemo;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.ServiceMemoRepository;
import com.staffsense.demo.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Контракт служебных записок.
 *
 * <p>Проверяем маршрут согласования с точки зрения клиента: какие действия
 * сервер разрешает автору и руководителю на каждом шаге, и что отказ в визе
 * возвращается ошибкой, а не молчаливым успехом.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemoApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ServiceMemoRepository memoRepository;

    @Autowired
    private UserRepository userRepository;

    private User author;
    private User pm;
    private ServiceMemo pendingPm;

    @BeforeEach
    void setUp() {
        // Сначала руководитель: автор должен быть его подчинённым, иначе
        // «руководитель видит записку» проверяло бы не команду, а отсутствие
        // проверки видимости (так тест и проходил до её появления).
        pm = userRepository.findByEmail("memo.pm@test.local").orElseGet(
                () -> userRepository.save(user("memo.pm@test.local", "Руководитель Тест", Role.PM, null)));
        author = userRepository.findByEmail("memo.author@test.local").orElseGet(
                () -> userRepository.save(user("memo.author@test.local", "Автор Записки", Role.EMPLOYEE, pm)));

        pendingPm = memoRepository.save(ServiceMemo.builder()
                .user(author)
                .memoType(MemoType.VACATION)
                .startDate(LocalDate.of(2026, 12, 1))
                .endDate(LocalDate.of(2026, 12, 5))
                .reason("Отпуск по графику")
                .status(MemoStatus.PENDING_PM)
                .createdAt(LocalDateTime.now())
                .build());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Автор видит свою записку и может её отозвать")
    void authorSeesCancelAction() throws Exception {
        authenticate("memo.author@test.local", "ROLE_EMPLOYEE");

        mockMvc.perform(get("/api/v1/memos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem(pendingPm.getId().intValue())))
                .andExpect(jsonPath("$[?(@.id==" + pendingPm.getId() + ")].allowedActions[*]")
                        .value(hasItem("cancel")));
    }

    @Test
    @DisplayName("Руководитель видит действия визы и отклонения")
    void pmSeesApproveAndReject() throws Exception {
        authenticate("memo.pm@test.local", "ROLE_PM");

        mockMvc.perform(get("/api/v1/memos/" + pendingPm.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedActions[*]").value(hasItem("approve-pm")))
                .andExpect(jsonPath("$.allowedActions[*]").value(hasItem("reject")))
                .andExpect(jsonPath("$.status").value("PENDING_PM"));
    }

    @Test
    @DisplayName("Отклонение без причины отклоняется валидацией")
    void rejectRequiresReason() throws Exception {
        authenticate("memo.pm@test.local", "ROLE_PM");

        mockMvc.perform(post("/api/v1/memos/" + pendingPm.getId() + "/reject")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    private void authenticate(String email, String authority) {
        var token = new UsernamePasswordAuthenticationToken(
                email, "n/a", List.of(new SimpleGrantedAuthority(authority)));
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    private User user(String email, String fullName, Role role, User manager) {
        return User.builder()
                .fullName(fullName)
                .email(email)
                .passwordHash("hash")
                .role(role)
                .department("Engineering")
                .position("Specialist")
                .salary(new BigDecimal("80000"))
                .gradeLevel(GradeLevel.MIDDLE)
                .weeklyCapacityHours(40)
                .hireDate(LocalDate.of(2023, 5, 1))
                .active(true)
                .manager(manager)
                .build();
    }
}
