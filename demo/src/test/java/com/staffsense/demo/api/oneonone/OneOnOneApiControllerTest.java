package com.staffsense.demo.api.oneonone;

import com.staffsense.demo.model.GradeLevel;
import com.staffsense.demo.model.MeetingStatus;
import com.staffsense.demo.model.OneOnOneMeeting;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.OneOnOneMeetingRepository;
import com.staffsense.demo.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Контракт «мои встречи 1:1».
 *
 * <p>Проверяем сценарий сотрудника: он видит свои встречи (ближайшая сверху)
 * и не видит чужие; руководитель видит встречи своей команды со своей стороны,
 * но не встречи соседней команды.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OneOnOneApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OneOnOneMeetingRepository meetingRepository;

    @Autowired
    private UserRepository userRepository;

    private User manager;
    private User employee;
    private User otherManager;
    private User otherEmployee;
    private OneOnOneMeeting future;
    private OneOnOneMeeting past;
    private OneOnOneMeeting foreign;

    @BeforeEach
    void setUp() {
        manager = ensureUser("oneonone.manager@test.local", "Руководитель Встреч", Role.PM, null);
        otherManager = ensureUser("oneonone.other.manager@test.local", "Другой Руководитель", Role.PM, null);
        employee = ensureUser("oneonone.emp@test.local", "Сотрудник Встреч", Role.EMPLOYEE, manager);
        otherEmployee = ensureUser("oneonone.other.emp@test.local", "Сотрудник Другой Команды",
                Role.EMPLOYEE, otherManager);

        // Чистим только свои тестовые встречи: иначе повторный прогон
        // накапливал бы строки и «$[0]» перестал быть предсказуемым.
        meetingRepository.deleteAll(meetingRepository.findByEmployeeId(employee.getId()));
        meetingRepository.deleteAll(meetingRepository.findByEmployeeId(otherEmployee.getId()));

        LocalDateTime now = LocalDateTime.now();
        future = meetingRepository.save(meeting(employee, manager, now.plusDays(2), MeetingStatus.PLANNED));
        past = meetingRepository.save(meeting(employee, manager, now.minusDays(5), MeetingStatus.COMPLETED));
        foreign = meetingRepository.save(
                meeting(otherEmployee, otherManager, now.plusDays(1), MeetingStatus.PLANNED));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Сотрудник видит свои встречи, ближайшая — первой")
    void employeeSeesOwnMeetingsUpcomingFirst() throws Exception {
        authenticate("oneonone.emp@test.local", "ROLE_EMPLOYEE");

        mockMvc.perform(get("/api/v1/oneonone/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(future.getId().intValue()))
                .andExpect(jsonPath("$[0].upcoming").value(true))
                .andExpect(jsonPath("$[0].myRole").value("EMPLOYEE"))
                .andExpect(jsonPath("$[0].canManage").value(false))
                .andExpect(jsonPath("$[0].statusTitle").value("Запланирована"))
                .andExpect(jsonPath("$[1].id").value(past.getId().intValue()))
                .andExpect(jsonPath("$[1].upcoming").value(false))
                .andExpect(jsonPath("$[*].id").value(not(hasItem(foreign.getId().intValue()))));
    }

    @Test
    @DisplayName("Руководитель видит встречи команды со своей стороны")
    void managerSeesTeamMeetings() throws Exception {
        authenticate("oneonone.manager@test.local", "ROLE_PM");

        mockMvc.perform(get("/api/v1/oneonone/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(future.getId().intValue()))
                .andExpect(jsonPath("$[0].myRole").value("MANAGER"))
                .andExpect(jsonPath("$[0].canManage").value(true))
                .andExpect(jsonPath("$[*].id").value(not(hasItem(foreign.getId().intValue()))));
    }

    @Test
    @DisplayName("Руководитель не видит встречи соседней команды")
    void otherManagerSeesOnlyOwnTeam() throws Exception {
        authenticate("oneonone.other.manager@test.local", "ROLE_PM");

        mockMvc.perform(get("/api/v1/oneonone/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(foreign.getId().intValue()))
                .andExpect(jsonPath("$[0].myRole").value("MANAGER"));
    }

    @Test
    @DisplayName("limit ограничивает выдачу, не меняя порядок")
    void limitCapsResponse() throws Exception {
        authenticate("oneonone.emp@test.local", "ROLE_EMPLOYEE");

        mockMvc.perform(get("/api/v1/oneonone/my").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(future.getId().intValue()));
    }

    // ── вспомогательное ──────────────────────────────────────────────────

    private void authenticate(String email, String authority) {
        var token = new UsernamePasswordAuthenticationToken(
                email, "n/a", List.of(new SimpleGrantedAuthority(authority)));
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    private OneOnOneMeeting meeting(User employee, User manager, LocalDateTime at, MeetingStatus status) {
        return OneOnOneMeeting.builder()
                .employee(employee)
                .manager(manager)
                .scheduledAt(at)
                .status(status)
                .missed(false)
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
                        .salary(new BigDecimal("90000"))
                        .gradeLevel(GradeLevel.SENIOR)
                        .weeklyCapacityHours(40)
                        .hireDate(LocalDate.of(2022, 3, 1))
                        .active(true)
                        .manager(manager)
                        .build()));
    }
}
