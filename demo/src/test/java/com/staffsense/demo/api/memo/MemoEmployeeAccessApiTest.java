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

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Сценарий сотрудника в служебных записках.
 *
 * <p>Обещания контракта: сотрудник видит и отзывает только свои записки,
 * но создать свою может; чужую он не открывает даже по прямому id.
 * Руководитель видит записки своей команды, но не соседней.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemoEmployeeAccessApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ServiceMemoRepository memoRepository;

    @Autowired
    private UserRepository userRepository;

    private User pm;
    private User foreignPm;
    private User author;
    private User colleague;
    private User foreignEmployee;
    private ServiceMemo ownMemo;
    private ServiceMemo colleagueMemo;
    private ServiceMemo foreignMemo;

    @BeforeEach
    void setUp() {
        pm = ensureUser("memo.access.pm@test.local", "Руководитель Доступа", Role.PM, null);
        foreignPm = ensureUser("memo.access.foreign.pm@test.local", "Чужой Руководитель", Role.PM, null);

        author = ensureUser("memo.access.emp@test.local", "Сотрудник Доступа", Role.EMPLOYEE, pm);
        colleague = ensureUser("memo.access.colleague@test.local", "Коллега Доступа", Role.EMPLOYEE, pm);
        foreignEmployee = ensureUser("memo.access.foreign@test.local", "Сотрудник Чужой Команды",
                Role.EMPLOYEE, foreignPm);

        ownMemo = memoRepository.save(memo(author, "Отпуск сотрудника"));
        colleagueMemo = memoRepository.save(memo(colleague, "Отпуск коллеги"));
        foreignMemo = memoRepository.save(memo(foreignEmployee, "Отпуск чужой команды"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Сотрудник видит только свои записки")
    void employeeSeesOnlyOwnMemos() throws Exception {
        authenticate("memo.access.emp@test.local", "ROLE_EMPLOYEE");

        mockMvc.perform(get("/api/v1/memos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].userId").value(everyItem(is(author.getId().intValue()))))
                .andExpect(jsonPath("$[*].id").value(hasItem(ownMemo.getId().intValue())))
                .andExpect(jsonPath("$[*].id").value(not(hasItem(colleagueMemo.getId().intValue()))))
                .andExpect(jsonPath("$[*].id").value(not(hasItem(foreignMemo.getId().intValue()))));
    }

    @Test
    @DisplayName("Чужую записку сотрудник не открывает по прямому id")
    void employeeCannotOpenForeignMemoById() throws Exception {
        authenticate("memo.access.emp@test.local", "ROLE_EMPLOYEE");

        mockMvc.perform(get("/api/v1/memos/" + colleagueMemo.getId()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/memos/" + foreignMemo.getId()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/memos/" + ownMemo.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ownMemo.getId().intValue()));
    }

    @Test
    @DisplayName("Сотрудник создаёт свою записку и видит её в списке")
    void employeeCanCreateOwnMemo() throws Exception {
        authenticate("memo.access.emp@test.local", "ROLE_EMPLOYEE");

        long before = memoRepository.findByUserIdOrderByCreatedAtDesc(author.getId()).size();

        mockMvc.perform(post("/api/v1/memos")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"SICK_LEAVE","startDate":"2027-02-01","endDate":"2027-02-03",
                                 "reason":"Больничный"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(author.getId().intValue()))
                .andExpect(jsonPath("$.type").value("SICK_LEAVE"))
                .andExpect(jsonPath("$.status").value("PENDING_PM"))
                .andExpect(jsonPath("$.allowedActions[*]").value(hasItem("cancel")));

        assertEquals(before + 1,
                memoRepository.findByUserIdOrderByCreatedAtDesc(author.getId()).size(),
                "У сотрудника должна появиться ровно одна новая своя записка");
    }

    @Test
    @DisplayName("Руководитель видит записки своей команды, но не чужой")
    void pmSeesOwnTeamMemos() throws Exception {
        authenticate("memo.access.pm@test.local", "ROLE_PM");

        mockMvc.perform(get("/api/v1/memos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem(ownMemo.getId().intValue())))
                .andExpect(jsonPath("$[*].id").value(hasItem(colleagueMemo.getId().intValue())))
                .andExpect(jsonPath("$[*].id").value(not(hasItem(foreignMemo.getId().intValue()))));
    }

    @Test
    @DisplayName("Сотрудник не отзывает чужую записку")
    void employeeCannotCancelForeignMemo() throws Exception {
        authenticate("memo.access.emp@test.local", "ROLE_EMPLOYEE");

        mockMvc.perform(post("/api/v1/memos/" + colleagueMemo.getId() + "/cancel").with(csrf()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/memos/" + ownMemo.getId() + "/cancel").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    // ── вспомогательное ──────────────────────────────────────────────────

    private void authenticate(String email, String authority) {
        var token = new UsernamePasswordAuthenticationToken(
                email, "n/a", List.of(new SimpleGrantedAuthority(authority)));
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    private ServiceMemo memo(User user, String reason) {
        return ServiceMemo.builder()
                .user(user)
                .memoType(MemoType.VACATION)
                .startDate(LocalDate.of(2027, 1, 10))
                .endDate(LocalDate.of(2027, 1, 12))
                .reason(reason)
                .status(MemoStatus.PENDING_PM)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private User ensureUser(String email, String fullName, Role role, User manager) {
        return userRepository.findByEmail(email).orElseGet(() -> userRepository.save(
                User.builder()
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
                        .build()));
    }
}
