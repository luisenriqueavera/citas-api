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
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "USER")
class RescheduleIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserAccountRepository users;
    @Autowired ProfessionalSlotRepository slots;

    @Test
    void requestingRetainsNewSlotWithoutTouchingOldSlotOrStatus() throws Exception {
        long patient = registerUser();
        createBlock("2031-03-01", "09:00", "10:00");
        long oldSlot = bookGeneral(patient, "2031-03-01", 0);
        long newSlot = availableSlotIds("2031-03-01", 1L).get(0);

        MvcResult result = mockMvc.perform(post("/api/v1/me/appointments/{id}/reschedule-requests", lastAppointmentId)
                        .with(user(String.valueOf(patient)).roles("USER")).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("slotIds", List.of(newSlot), "reason", "Cruce de horario"))))
                .andExpect(status().isCreated()).andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("status").asText()).isEqualTo("PENDING");

        assertThat(slots.findById(oldSlot).orElseThrow().getAppointmentId()).isEqualTo(lastAppointmentId);
        assertThat(slots.findById(newSlot).orElseThrow().getAppointmentId()).isEqualTo(lastAppointmentId);
        mockMvc.perform(get("/api/v1/me/appointments").with(user(String.valueOf(patient)).roles("USER")))
                .andExpect(jsonPath("$[0].status").value("APPROVED"));
    }

    @Test
    void approvingReleasesOldSlotKeepsNewAndUpdatesSchedule() throws Exception {
        long patient = registerUser();
        createBlock("2031-03-02", "09:00", "10:00");
        long oldSlot = bookGeneral(patient, "2031-03-02", 0);
        long newSlot = availableSlotIds("2031-03-02", 1L).get(0);
        long requestId = requestReschedule(patient, lastAppointmentId, newSlot);

        mockMvc.perform(post("/api/v1/admin/reschedule-requests/{id}/decision", requestId).with(user("102").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("decision", "APPROVE"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));

        assertThat(slots.findById(oldSlot).orElseThrow().getAppointmentId()).isNull();
        assertThat(slots.findById(newSlot).orElseThrow().getAppointmentId()).isEqualTo(lastAppointmentId);
    }

    @Test
    void rejectingWithoutReasonFailsAndWithReasonKeepsOriginalSlot() throws Exception {
        long patient = registerUser();
        createBlock("2031-03-03", "09:00", "10:00");
        long oldSlot = bookGeneral(patient, "2031-03-03", 0);
        long newSlot = availableSlotIds("2031-03-03", 1L).get(0);
        long requestId = requestReschedule(patient, lastAppointmentId, newSlot);

        mockMvc.perform(post("/api/v1/admin/reschedule-requests/{id}/decision", requestId).with(user("102").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("decision", "REJECT"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_appointment"));

        mockMvc.perform(post("/api/v1/admin/reschedule-requests/{id}/decision", requestId).with(user("102").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("decision", "REJECT", "reason", "Cupo no disponible"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));

        assertThat(slots.findById(oldSlot).orElseThrow().getAppointmentId()).isEqualTo(lastAppointmentId);
        assertThat(slots.findById(newSlot).orElseThrow().getAppointmentId()).isNull();
    }

    @Test
    void adminBandejaListsPendingReschedule() throws Exception {
        long patient = registerUser();
        createBlock("2031-03-04", "09:00", "10:00");
        bookGeneral(patient, "2031-03-04", 0);
        long newSlot = availableSlotIds("2031-03-04", 1L).get(0);
        long requestId = requestReschedule(patient, lastAppointmentId, newSlot);

        mockMvc.perform(get("/api/v1/admin/reschedule-requests/pending").with(user("102").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + requestId + ")]").isNotEmpty());
    }

    @Test
    void concurrentRescheduleAndDirectReservationOfSameSlotAcceptOnlyOne() throws Exception {
        long patient = registerUser();
        long intruder = registerUser();
        createBlock("2031-03-05", "09:00", "10:00");
        bookGeneral(patient, "2031-03-05", 0);
        long newSlot = availableSlotIds("2031-03-05", 1L).get(0);
        long appointmentId = lastAppointmentId;

        String reschedulePayload = objectMapper.writeValueAsString(Map.of("slotIds", List.of(newSlot), "reason", "Cambio de turno"));
        String directPayload = objectMapper.writeValueAsString(Map.of("patientUserId", intruder, "professionalId", 1, "locationId", 1,
                "specialtyId", 1, "slotIds", List.of(newSlot), "reason", "Otra solicitud"));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> rescheduleAttempt = executor.submit(() -> mockMvc.perform(post("/api/v1/me/appointments/{id}/reschedule-requests", appointmentId)
                            .with(user(String.valueOf(patient)).roles("USER")).contentType(MediaType.APPLICATION_JSON).content(reschedulePayload))
                    .andReturn().getResponse().getStatus());
            Future<Integer> directAttempt = executor.submit(() -> mockMvc.perform(post("/api/v1/appointments")
                            .with(user(String.valueOf(intruder)).roles("USER")).contentType(MediaType.APPLICATION_JSON).content(directPayload))
                    .andReturn().getResponse().getStatus());
            List<Integer> results = List.of(rescheduleAttempt.get(), directAttempt.get());
            assertThat(results).containsExactlyInAnyOrder(201, 409);
        } finally {
            executor.shutdownNow();
        }
        assertThat(slots.findById(newSlot).orElseThrow().getAppointmentId()).isNotNull();
    }

    private long lastAppointmentId;

    private long requestReschedule(long patientUserId, long appointmentId, long newSlotId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/me/appointments/{id}/reschedule-requests", appointmentId)
                        .with(user(String.valueOf(patientUserId)).roles("USER")).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("slotIds", List.of(newSlotId), "reason", "Cruce de horario"))))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long bookGeneral(long patientUserId, String date, int slotIndex) throws Exception {
        long slotId = availableSlotIds(date, 1L).get(slotIndex);
        MvcResult result = mockMvc.perform(post("/api/v1/appointments").with(user(String.valueOf(patientUserId)).roles("USER")).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("patientUserId", patientUserId, "professionalId", 1, "locationId", 1,
                                "specialtyId", 1, "slotIds", List.of(slotId), "reason", "Consulta general"))))
                .andExpect(status().isCreated()).andReturn();
        lastAppointmentId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        return slotId;
    }

    private void createBlock(String date, String startTime, String endTime) throws Exception {
        mockMvc.perform(post("/api/v1/professional/availability-blocks").with(user("101").roles("PROFESSIONAL")).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("professionalId", 1, "locationId", 1,
                                "availableDate", date, "startTime", startTime, "endTime", endTime))))
                .andExpect(status().isCreated());
    }

    private List<Long> availableSlotIds(String date, Long specialtyId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/availability").param("date", date)
                        .param("specialtyId", String.valueOf(specialtyId)).param("professionalId", "1"))
                .andExpect(status().isOk()).andReturn();
        JsonNode options = objectMapper.readTree(result.getResponse().getContentAsString());
        List<Long> ids = new java.util.ArrayList<>();
        for (JsonNode option : options) ids.add(option.get("slotIds").get(0).asLong());
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
