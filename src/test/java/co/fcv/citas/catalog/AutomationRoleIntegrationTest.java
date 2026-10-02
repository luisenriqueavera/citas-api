package co.fcv.citas.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * S5 risk mitigation (riesgo #3, s5-untrusted-content-and-residual-risks.md): a dedicated
 * low-privilege AUTOMATION role can read the two n8n reporting endpoints but nothing else
 * under /api/v1/admin/**, instead of reusing a full ADMIN token.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "USER")
class AutomationRoleIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void adminCanCreateAnAutomationAccount() throws Exception {
        String email = "automation-" + UUID.randomUUID() + "@demo.invalid";
        mockMvc.perform(post("/api/v1/admin/automation-accounts").with(user("102").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "n8n", "lastName", "Automation",
                                "documentType", "CC", "documentNumber", UUID.randomUUID().toString().replace("-", "").substring(0, 20),
                                "email", email, "phone", "3000000000", "temporaryPassword", "Secure123*"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void nonAdminCannotCreateAnAutomationAccount() throws Exception {
        mockMvc.perform(post("/api/v1/admin/automation-accounts").with(user("1").roles("USER")).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "n8n", "lastName", "Automation",
                                "documentType", "CC", "documentNumber", UUID.randomUUID().toString().replace("-", "").substring(0, 20),
                                "email", "blocked-" + UUID.randomUUID() + "@demo.invalid", "phone", "3000000000", "temporaryPassword", "Secure123*"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void automationRoleCanReadTheTwoReportingEndpointsOnly() throws Exception {
        mockMvc.perform(get("/api/v1/admin/appointments/upcoming-reminders").with(user("900").roles("AUTOMATION")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/appointments/daily-summary").with(user("900").roles("AUTOMATION")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/professionals").with(user("900").roles("AUTOMATION")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/appointments/pending").with(user("900").roles("AUTOMATION")))
                .andExpect(status().isForbidden());
    }
}
