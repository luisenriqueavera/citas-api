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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
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
class ProfessionalAgendaIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserAccountRepository users;

    @Test
    void professionalSeesOnlyOwnApprovedAppointmentsAndNeverAnothersData() throws Exception {
        long patient = registerUser();
        ProfessionalAccount professional2 = createSecondProfessional();

        createBlock(1L, "2031-04-01", "09:00", "10:00");
        createBlock(professional2.professionalId(), "2031-04-01", "09:00", "10:00");

        long slotForProfessional1 = availableSlotId("2031-04-01", 1L, 1L);
        bookGeneral(patient, 1L, slotForProfessional1);
        long slotForProfessional2 = availableSlotId("2031-04-01", 1L, professional2.professionalId());
        bookGeneral(patient, professional2.professionalId(), slotForProfessional2);

        MvcResult asProfessional1 = mockMvc.perform(get("/api/v1/professional/appointments").param("date", "2031-04-01")
                        .with(user("101").roles("PROFESSIONAL")))
                .andExpect(status().isOk()).andReturn();
        JsonNode body1 = objectMapper.readTree(asProfessional1.getResponse().getContentAsString());
        assertThat(body1).hasSize(1);

        MvcResult asProfessional2 = mockMvc.perform(get("/api/v1/professional/appointments").param("date", "2031-04-01")
                        .with(user(String.valueOf(professional2.userId())).roles("PROFESSIONAL")))
                .andExpect(status().isOk()).andReturn();
        JsonNode body2 = objectMapper.readTree(asProfessional2.getResponse().getContentAsString());
        assertThat(body2).hasSize(1);
        assertThat(body1.get(0).get("id").asLong()).isNotEqualTo(body2.get(0).get("id").asLong());
    }

    private record ProfessionalAccount(long userId, long professionalId) { }

    private ProfessionalAccount createSecondProfessional() throws Exception {
        String email = "prof-" + UUID.randomUUID() + "@demo.invalid";
        MvcResult created = mockMvc.perform(post("/api/v1/admin/professionals").with(user("102").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("firstName", "Segundo", "lastName", "Profesional", "documentType", "CC",
                                "documentNumber", UUID.randomUUID().toString().replace("-", "").substring(0, 20), "email", email, "phone", "3000000200",
                                "temporaryPassword", "Secure123*", "professionalCode", "DEMO-PRO-" + UUID.randomUUID().toString().substring(0, 8),
                                "licenseNumber", "DEMO-LIC-" + UUID.randomUUID().toString().substring(0, 8)))))
                .andExpect(status().isCreated()).andReturn();
        long professionalId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(put("/api/v1/admin/professionals/{id}/specialties", professionalId).with(user("102").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("ids", List.of(1), "primaryId", 1))))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/admin/professionals/{id}/locations", professionalId).with(user("102").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("ids", List.of(1)))))
                .andExpect(status().isOk());
        long userId = users.findByEmailIgnoreCase(email).orElseThrow().getId();
        return new ProfessionalAccount(userId, professionalId);
    }

    private void bookGeneral(long patientUserId, long professionalId, long slotId) throws Exception {
        mockMvc.perform(post("/api/v1/appointments").with(user(String.valueOf(patientUserId)).roles("USER")).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("patientUserId", patientUserId, "professionalId", professionalId, "locationId", 1,
                                "specialtyId", 1, "slotIds", List.of(slotId), "reason", "Consulta general"))))
                .andExpect(status().isCreated());
    }

    private void createBlock(Long professionalId, String date, String startTime, String endTime) throws Exception {
        mockMvc.perform(post("/api/v1/professional/availability-blocks").with(user("101").roles("PROFESSIONAL")).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("professionalId", professionalId, "locationId", 1,
                                "availableDate", date, "startTime", startTime, "endTime", endTime))))
                .andExpect(status().isCreated());
    }

    private long availableSlotId(String date, Long specialtyId, Long professionalId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/availability").param("date", date)
                        .param("specialtyId", String.valueOf(specialtyId)).param("professionalId", String.valueOf(professionalId)))
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
