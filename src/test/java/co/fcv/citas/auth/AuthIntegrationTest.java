package co.fcv.citas.auth;

import co.fcv.citas.insurance.UserInsuranceAffiliationRepository;
import co.fcv.citas.appointments.AppointmentRepository;
import co.fcv.citas.appointments.ProfessionalSlotRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.util.Map;
import java.util.List;
import java.util.concurrent.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserAccountRepository users;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired UserInsuranceAffiliationRepository affiliations;
    @Autowired AppointmentRepository appointments;
    @Autowired ProfessionalSlotRepository professionalSlots;

    @Test
    void concurrentReservationsOfSameSlotAcceptOneAndRejectTheOther() throws Exception {
        String firstEmail = uniqueEmail(); String secondEmail = uniqueEmail();
        registerUser(firstEmail, uniqueDocument()); registerUser(secondEmail, uniqueDocument());
        Long firstUser = users.findByEmailIgnoreCase(firstEmail).orElseThrow().getId();
        Long secondUser = users.findByEmailIgnoreCase(secondEmail).orElseThrow().getId();
        long appointmentsBefore = appointments.count();
        String firstRequest = appointment(firstUser, 1L, List.of(1L));
        String secondRequest = appointment(secondUser, 1L, List.of(1L));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ReservationAttempt> first = executor.submit(() -> reserve(firstRequest));
            Future<ReservationAttempt> second = executor.submit(() -> reserve(secondRequest));
            List<ReservationAttempt> results = List.of(first.get(), second.get());
            System.out.printf("Reservation attempts for slots [1]: %s%n", results);
            assertThat(results).extracting(ReservationAttempt::status).containsExactlyInAnyOrder(201, 409);
            assertThat(results).filteredOn(attempt -> attempt.status() == 201).singleElement()
                    .extracting(ReservationAttempt::body).asString().contains("APPROVED");
        } finally {
            executor.shutdownNow();
        }
        assertThat(professionalSlots.findAllById(List.of(1L))).singleElement()
                .extracting(slot -> slot.getAppointmentId()).isNotNull();
        assertThat(professionalSlots.countReservedAppointments(1L)).isOne();
        assertThat(appointments.count()).isEqualTo(appointmentsBefore + 1);
    }

    @Test
    void specializedReservationIsRequestedAndRetainsItsSlots() throws Exception {
        String email = uniqueEmail(); registerUser(email, uniqueDocument());
        Long user = users.findByEmailIgnoreCase(email).orElseThrow().getId();
        MvcResult result = mockMvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON)
                        .content(appointment(user, 2L, List.of(2L, 3L))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("REQUESTED")).andReturn();
        long appointmentId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        assertThat(professionalSlots.findAllById(List.of(2L, 3L))).allSatisfy(slot -> assertThat(slot.getAppointmentId()).isEqualTo(appointmentId));
    }

    @Test
    void registersWithoutOptionalInsurancePlan() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(register(email, uniqueDocument())))
                .andExpect(status().isCreated());
        UserAccount stored = users.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(affiliations.countByUserId(stored.getId())).isZero();
    }

    @Test
    void registersWithActivePlanThroughForeignKey() throws Exception {
        String email = uniqueEmail();
        String body = objectMapper.writeValueAsString(Map.of("firstName", "Ana", "lastName", "Plan", "documentType", "CC",
                "documentNumber", uniqueDocument(), "email", email, "phone", "3001234567", "password", "Secure123*", "planId", 1));
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        UserAccount stored = users.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(affiliations.countByUserId(stored.getId())).isOne();
    }

    @Test
    void rejectsUnknownOrInactivePlanWithoutCreatingUser() throws Exception {
        String email = uniqueEmail();
        String body = objectMapper.writeValueAsString(Map.of("firstName", "Ana", "lastName", "Plan", "documentType", "CC",
                "documentNumber", uniqueDocument(), "email", email, "phone", "3001234567", "password", "Secure123*", "planId", 999999));
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_plan"));
        assertThat(users.findByEmailIgnoreCase(email)).isEmpty();

        String inactiveEmail = uniqueEmail();
        String inactiveBody = body.replace(email, inactiveEmail).replace("999999", "4");
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(inactiveBody))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_plan"));
        assertThat(users.findByEmailIgnoreCase(inactiveEmail)).isEmpty();
    }

    @Test
    void registersUserWithUniqueEmailDocumentAndHashedPassword() throws Exception {
        String email = uniqueEmail(); String document = uniqueDocument();
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(register(email, document)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.email").value(email)).andExpect(jsonPath("$.roles[0]").value("USER"));
        UserAccount stored = users.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(stored.getPasswordHash()).isNotEqualTo("Secure123*");
        assertThat(passwordEncoder.matches("Secure123*", stored.getPasswordHash())).isTrue();
        assertThat(stored.getRoles()).extracting(Role::getCode).containsExactly("USER");
    }

    @Test
    void rejectsDuplicateEmailAndDuplicateDocument() throws Exception {
        String email = uniqueEmail(); String document = uniqueDocument();
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(register(email, document))).andExpect(status().isCreated());
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(register(email, uniqueDocument()))).andExpect(status().isConflict());
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(register(uniqueEmail(), document))).andExpect(status().isConflict());
    }

    @Test
    void loginIssuesAccessAndRefreshTokens() throws Exception {
        String email = uniqueEmail(); registerUser(email, uniqueDocument());
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login(email, "Secure123*")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty()).andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer")).andReturn();
        assertThat(refreshTokens.count()).isPositive();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.get("accessToken").asText()).isNotEqualTo(response.get("refreshToken").asText());
    }

    @Test
    void rejectsInvalidLogin() throws Exception {
        String email = uniqueEmail(); registerUser(email, uniqueDocument());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login(email, "not-the-password")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login("unknown@demo.invalid", "any-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rotatesRefreshTokenAndRejectsInvalidOrReusedToken() throws Exception {
        String email = uniqueEmail(); registerUser(email, uniqueDocument());
        String original = tokenForLogin(email, "refreshToken");
        MvcResult refresh = mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refresh(original)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty()).andExpect(jsonPath("$.refreshToken").isNotEmpty()).andReturn();
        String replacement = objectMapper.readTree(refresh.getResponse().getContentAsString()).get("refreshToken").asText();
        assertThat(replacement).isNotEqualTo(original);
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refresh(original))).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refresh("not-a-jwt"))).andExpect(status().isUnauthorized());
    }

    private void registerUser(String email, String document) throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(register(email, document))).andExpect(status().isCreated());
    }
    private String tokenForLogin(String email, String field) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login(email, "Secure123*"))).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get(field).asText();
    }
    private String register(String email, String document) throws Exception {
        return objectMapper.writeValueAsString(Map.of("firstName", "Ana", "lastName", "Prueba", "documentType", "CC", "documentNumber", document, "email", email, "phone", "3001234567", "password", "Secure123*"));
    }
    private String login(String email, String password) throws Exception { return objectMapper.writeValueAsString(Map.of("email", email, "password", password)); }
    private String refresh(String token) throws Exception { return objectMapper.writeValueAsString(Map.of("refreshToken", token)); }
    private ReservationAttempt reserve(String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        return new ReservationAttempt(result.getResponse().getStatus(), result.getResponse().getContentAsString());
    }
    private record ReservationAttempt(int status, String body) { }
    private String appointment(Long userId, Long specialtyId, List<Long> slotIds) throws Exception {
        return objectMapper.writeValueAsString(Map.of("patientUserId", userId, "professionalId", 1, "locationId", 1,
                "specialtyId", specialtyId, "slotIds", slotIds, "reason", "Prueba de concurrencia"));
    }
    private String uniqueEmail() { return "user-" + UUID.randomUUID() + "@demo.invalid"; }
    private String uniqueDocument() { return UUID.randomUUID().toString().replace("-", "").substring(0, 20); }
}
