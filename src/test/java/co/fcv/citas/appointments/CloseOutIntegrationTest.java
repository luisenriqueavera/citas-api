package co.fcv.citas.appointments;

import co.fcv.citas.auth.UserAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "USER")
class CloseOutIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserAccountRepository users;
    @Autowired JdbcTemplate jdbc;

    @Test
    void closingOwnPastApprovedAppointmentSucceedsAndRecordsHistory() throws Exception {
        long patient = registerUser();
        long appointmentId = bookGeneral(patient, "2031-05-01");
        backdate(appointmentId);

        mockMvc.perform(post("/api/v1/professional/appointments/{id}/close", appointmentId).with(user("101").roles("PROFESSIONAL")).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("outcome", "COMPLETED"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));

        Integer historyCount = jdbc.queryForObject("select count(*) from appointment_status_history where appointment_id = ? and change_source = 'PROFESSIONAL'", Integer.class, appointmentId);
        assertThat(historyCount).isEqualTo(1);
    }

    @Test
    void closingAnotherProfessionalsAppointmentIsForbidden() throws Exception {
        long patient = registerUser();
        long professional2UserId = createSecondProfessional();
        long appointmentId = bookGeneral(patient, "2031-05-02");
        backdate(appointmentId);

        mockMvc.perform(post("/api/v1/professional/appointments/{id}/close", appointmentId).with(user(String.valueOf(professional2UserId)).roles("PROFESSIONAL")).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("outcome", "COMPLETED"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void closingAFutureAppointmentIsRejected() throws Exception {
        long patient = registerUser();
        long appointmentId = bookGeneral(patient, "2031-05-03");

        mockMvc.perform(post("/api/v1/professional/appointments/{id}/close", appointmentId).with(user("101").roles("PROFESSIONAL")).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("outcome", "COMPLETED"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("invalid_transition"));
    }

    private void backdate(long appointmentId) {
        LocalDateTime pastStart = LocalDateTime.of(2020, 1, 1, 10, 0);
        jdbc.update("update appointments set scheduled_start_at = ?, scheduled_end_at = ? where id = ?", pastStart, pastStart.plusMinutes(30), appointmentId);
    }

    private long createSecondProfessional() throws Exception {
        String email = "prof-" + UUID.randomUUID() + "@demo.invalid";
        MvcResult created = mockMvc.perform(post("/api/v1/admin/professionals").with(user("102").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("firstName", "Segundo", "lastName", "Profesional", "documentType", "CC",
                                "documentNumber", UUID.randomUUID().toString().replace("-", "").substring(0, 20), "email", email, "phone", "3000000200",
                                "temporaryPassword", "Secure123*", "professionalCode", "DEMO-PRO-" + UUID.randomUUID().toString().substring(0, 8),
                                "licenseNumber", "DEMO-LIC-" + UUID.randomUUID().toString().substring(0, 8)))))
                .andExpect(status().isCreated()).andReturn();
        return users.findByEmailIgnoreCase(email).orElseThrow().getId();
    }

    private long bookGeneral(long patientUserId, String date) throws Exception {
        createBlock(date, "09:00", "10:00");
        long slotId = availableSlotId(date, 1L);
        MvcResult result = mockMvc.perform(post("/api/v1/appointments").with(user(String.valueOf(patientUserId)).roles("USER")).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("patientUserId", patientUserId, "professionalId", 1, "locationId", 1,
                                "specialtyId", 1, "slotIds", List.of(slotId), "reason", "Consulta general"))))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void createBlock(String date, String startTime, String endTime) throws Exception {
        mockMvc.perform(post("/api/v1/professional/availability-blocks").with(user("101").roles("PROFESSIONAL")).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("professionalId", 1, "locationId", 1,
                                "availableDate", date, "startTime", startTime, "endTime", endTime))))
                .andExpect(status().isCreated());
    }

    private long availableSlotId(String date, Long specialtyId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/availability").param("date", date)
                        .param("specialtyId", String.valueOf(specialtyId)).param("professionalId", "1"))
                .andExpect(status().isOk()).andReturn();
        JsonNode options = objectMapper.readTree(result.getResponse().getContentAsString());
        return options.get(0).get("slotIds").get(0).asLong();
    }

    private long registerUser() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@demo.invalid";
        String document = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("firstName", "Ana", "lastName", "Prueba", "documentType", "CC",
                                "documentNumber", document, "email", email, "phone", "3001234567", "password", "Secure123*"))))
                .andExpect(status().isCreated());
        return users.findByEmailIgnoreCase(email).orElseThrow().getId();
    }
}
