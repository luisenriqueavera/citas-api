package co.fcv.citas.appointments;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class S3SchedulingIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ProfessionalSlotRepository slots;

    @Test
    void exposesActiveCatalogsAndTwoConsecutiveSlotsForSixtyMinuteSpecialty() throws Exception {
        mockMvc.perform(get("/api/v1/catalogs/locations")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").isNumber());
        mockMvc.perform(get("/api/v1/catalogs/specialties")).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.durationMinutes == 60)]").isNotEmpty());
        mockMvc.perform(get("/api/v1/availability").param("date", "2030-01-15").param("specialtyId", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].slotIds.length()").value(2));
    }

    @Test
    void specializedDecisionApproveKeepsSlotsAndRejectReleasesThem() throws Exception {
        String approved = objectMapper.writeValueAsString(Map.of("patientUserId", 100, "professionalId", 1, "locationId", 1, "specialtyId", 2, "slotIds", List.of(5, 6), "reason", "Solicitud S3"));
        String approvedResponse = mockMvc.perform(post("/api/v1/appointments").contentType(MediaType.APPLICATION_JSON).content(approved))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("REQUESTED")).andReturn().getResponse().getContentAsString();
        long approvedId = objectMapper.readTree(approvedResponse).get("id").asLong();
        mockMvc.perform(post("/api/v1/admin/appointments/{id}/decision", approvedId).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("decision", "APPROVE", "adminUserId", 100))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        assertThat(slots.findAllById(List.of(5L, 6L))).allSatisfy(slot -> assertThat(slot.getAppointmentId()).isEqualTo(approvedId));

        String rejected = objectMapper.writeValueAsString(Map.of("patientUserId", 100, "professionalId", 1, "locationId", 1, "specialtyId", 2, "slotIds", List.of(7, 8), "reason", "Otra solicitud S3"));
        String rejectedResponse = mockMvc.perform(post("/api/v1/appointments").contentType(MediaType.APPLICATION_JSON).content(rejected))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("REQUESTED")).andReturn().getResponse().getContentAsString();
        long rejectedId = objectMapper.readTree(rejectedResponse).get("id").asLong();
        mockMvc.perform(post("/api/v1/admin/appointments/{id}/decision", rejectedId).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("decision", "REJECT", "adminUserId", 100))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_appointment"));
        mockMvc.perform(post("/api/v1/admin/appointments/{id}/decision", rejectedId).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("decision", "REJECT", "adminUserId", 100, "reason", "No pertinencia"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.slotsReleased").value(true));
        assertThat(slots.findAllById(List.of(7L, 8L))).allSatisfy(slot -> assertThat(slot.getAppointmentId()).isNull());
    }
}
