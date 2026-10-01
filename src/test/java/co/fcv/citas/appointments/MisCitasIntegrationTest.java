package co.fcv.citas.appointments;

import co.fcv.citas.auth.UserAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;
import java.time.LocalDate;
import java.util.ArrayList;
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
class MisCitasIntegrationTest {
    @Autowired org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserAccountRepository users;

    @Test
    void listsOnlyOwnAppointmentsWithStatusAndDateFilters() throws Exception {
        long patientA = registerUser();
        long patientB = registerUser();
        LocalDate date = LocalDate.parse("2031-02-01");
        createBlock(date, "09:00", "10:00");
        List<Long> generalSlots = availableSlotIds(date, 1L);

        bookGeneral(patientA, generalSlots);
        List<Long> otherSlots = availableSlotIds(date, 1L);
        bookGeneral(patientB, otherSlots);

        MvcResult result = mockMvc.perform(get("/api/v1/me/appointments").with(user(String.valueOf(patientA)).roles("USER")))
                .andExpect(status().isOk()).andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body).hasSize(1);
        assertThat(body.get(0).get("status").asText()).isEqualTo("APPROVED");

        mockMvc.perform(get("/api/v1/me/appointments").param("status", "REQUESTED").with(user(String.valueOf(patientA)).roles("USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/api/v1/me/appointments").param("from", "2031-03-01").with(user(String.valueOf(patientA)).roles("USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void surfacesRejectionReasonForRejectedSpecializedAppointment() throws Exception {
        long patient = registerUser();
        LocalDate date = LocalDate.parse("2031-02-02");
        createBlock(date, "09:00", "10:00");
        List<Long> cardiologySlots = availableSlotIds(date, 2L);

        MvcResult created = mockMvc.perform(post("/api/v1/appointments").with(user(String.valueOf(patient)).roles("USER")).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("patientUserId", patient, "professionalId", 1, "locationId", 1,
                                "specialtyId", 2, "slotIds", cardiologySlots, "reason", "Control"))))
                .andExpect(status().isCreated()).andReturn();
        long appointmentId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/v1/admin/appointments/{id}/decision", appointmentId).with(user("102").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("decision", "REJECT", "reason", "No hay cupo disponible"))))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/v1/me/appointments").param("status", "REJECTED").with(user(String.valueOf(patient)).roles("USER")))
                .andExpect(status().isOk()).andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body).hasSize(1);
        assertThat(body.get(0).get("rejectionReason").asText()).isEqualTo("No hay cupo disponible");
    }

    private void bookGeneral(long patientUserId, List<Long> slotIds) throws Exception {
        List<Long> single = List.of(slotIds.get(0));
        mockMvc.perform(post("/api/v1/appointments").with(user(String.valueOf(patientUserId)).roles("USER")).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("patientUserId", patientUserId, "professionalId", 1, "locationId", 1,
                                "specialtyId", 1, "slotIds", single, "reason", "Consulta general"))))
                .andExpect(status().isCreated());
    }

    private void createBlock(LocalDate date, String startTime, String endTime) throws Exception {
        mockMvc.perform(post("/api/v1/professional/availability-blocks").with(user("101").roles("PROFESSIONAL")).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("professionalId", 1, "locationId", 1,
                                "availableDate", date.toString(), "startTime", startTime, "endTime", endTime))))
                .andExpect(status().isCreated());
    }

    private List<Long> availableSlotIds(LocalDate date, Long specialtyId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/availability").param("date", date.toString())
                        .param("specialtyId", String.valueOf(specialtyId)).param("professionalId", "1"))
                .andExpect(status().isOk()).andReturn();
        JsonNode options = objectMapper.readTree(result.getResponse().getContentAsString());
        List<Long> ids = new ArrayList<>();
        for (JsonNode slotId : options.get(0).get("slotIds")) ids.add(slotId.asLong());
        return ids;
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
