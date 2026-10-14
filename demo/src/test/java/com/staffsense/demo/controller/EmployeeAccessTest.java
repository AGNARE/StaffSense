package com.staffsense.demo.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Доступ к разделу «Сотрудники компании» (FR-004, ROLE-02).
 *
 * <p>Фильтры безопасности здесь ВКЛЮЧЕНЫ: проверяются правила доступа по URL,
 * а не только аннотации. Раньше роль EXEC (руководство) не была указана в правиле
 * {@code /employees/**}, хотя меню показывало ей ссылку, а слой области видимости
 * ({@code TeamAccessService}) уже разрешал просмотр любого сотрудника. В итоге
 * руководство получало 403 по ссылке из собственного меню.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmployeeAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "exec@staffsense.com", roles = "EXEC")
    @DisplayName("EXEC (руководство) открывает раздел «Сотрудники компании»")
    void execCanOpenEmployees() throws Exception {
        mockMvc.perform(get("/employees"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "hr@staffsense.com", roles = "HR")
    @DisplayName("HR открывает раздел «Сотрудники компании»")
    void hrCanOpenEmployees() throws Exception {
        mockMvc.perform(get("/employees"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "employee@staffsense.com", roles = "EMPLOYEE")
    @DisplayName("EMPLOYEE не имеет доступа к разделу «Сотрудники компании»")
    void employeeCannotOpenEmployees() throws Exception {
        mockMvc.perform(get("/employees"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "exec@staffsense.com", roles = "EXEC")
    @DisplayName("EXEC не имеет доступа к панели управления пользователями")
    void execCannotOpenAdminPanel() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().isForbidden());
    }
}
