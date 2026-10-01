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
import java.time.LocalDate;
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
class CancellationIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserAccountRepository users;
    @Autowired ProfessionalSlotRepository slots;

    @Test
    void cancellingReleasesSlotsAndRecordsHistory() throws Exception {
        long patient = registerUser();
        Booking booking = bookGeneral(patient, "2031-02-10");

        mockMvc.perform(post("/api/v1/me/appointments/{id}/cancel", booking.appointmentId()).with(user(String.valueOf(patient)).roles("USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(slots.findById(booking.slotId()).orElseThrow().getAppointmentId()).isNull();

        mockMvc.perform(get("/api/v1/me/appointments/{id}/history", booking.appointmentId()).with(user(String.valueOf(patient)).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.status == 'CANCELLED' && @.changeSource == 'USER')]").isNotEmpty());
    }

    @Test
    void cancellingSomeoneElsesAppointmentIsForbidden() throws Exception {
        long owner = registerUser();
        long intruder = registerUser();
        Booking booking = bookGeneral(owner, "2031-02-11");

        mockMvc.perform(post("/api/v1/me/appointments/{id}/cancel", booking.appointmentId()).with(user(String.valueOf(intruder)).roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancellingAlreadyCancelledAppointmentIsRejected() throws Exception {
        long patient = registerUser();
        Booking booking = bookGeneral(patient, "2031-02-12");

        mockMvc.perform(post("/api/v1/me/appointments/{id}/cancel", booking.appointmentId()).with(user(String.valueOf(patient)).roles("USER")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/me/appointments/{id}/cancel", booking.appointmentId()).with(user(String.valueOf(patient)).roles("USER")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("invalid_transition"));
    }

    @Test
    void cancellingUnknownAppointmentReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/me/appointments/{id}/cancel", 999999).with(user("100").roles("USER")))
                .andExpect(status().isNotFound());
    }

    private record Booking(long appointmentId, long slotId) { }

    private Booking bookGeneral(long patientUserId, String date) throws Exception {
        createBlock(LocalDate.parse(date), "09:00", "10:00");
        long slotId = availableSlotIds(LocalDate.parse(date), 1L).get(0);
        MvcResult result = mockMvc.perform(post("/api/v1/appointments").with(user(String.valueOf(patientUserId)).roles("USER")).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("patientUserId", patientUserId, "professionalId", 1, "locationId", 1,
                                "specialtyId", 1, "slotIds", List.of(slotId), "reason", "Consulta general"))))
                .andExpect(status().isCreated()).andReturn();
        long appointmentId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        return new Booking(appointmentId, slotId);
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
        List<Long> ids = new java.util.ArrayList<>();
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
