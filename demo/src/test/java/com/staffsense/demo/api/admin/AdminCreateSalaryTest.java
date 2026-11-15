package com.staffsense.demo.api.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Оклад и грейд при создании учётной записи.
 *
 * <p>Без них сотрудник попадал в расчёт стоимости под риском с окладом 0,01
 * и давал нулевой вклад. Проверяем, что переданные значения сохраняются.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminCreateSalaryTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Создание с окладом и грейдом сохраняет их")
    void createsWithSalaryAndGrade() throws Exception {
        authenticateAdmin();

        String body = """
                {
                  "fullName": "Окладная Проверка",
                  "email": "salary.check@test.local",
                  "role": "EMPLOYEE",
                  "department": "Design",
                  "position": "Designer",
                  "salary": 55000,
                  "gradeLevel": "SENIOR"
                }
                """;

        String response = mockMvc.perform(post("/api/v1/admin/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode created = objectMapper.readTree(response);
        assertThat(created.path("user").path("userId").asLong()).isPositive();
    }

    @Test
    @DisplayName("Оклад меньше единицы отклоняется")
    void rejectsTinySalary() throws Exception {
        authenticateAdmin();

        String body = """
                {
                  "fullName": "Плохой Оклад",
                  "email": "bad.salary@test.local",
                  "role": "EMPLOYEE",
                  "department": "Design",
                  "position": "Designer",
                  "salary": 0
                }
                """;

        mockMvc.perform(post("/api/v1/admin/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    private void authenticateAdmin() {
        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin@staffsense.com", "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }
}
