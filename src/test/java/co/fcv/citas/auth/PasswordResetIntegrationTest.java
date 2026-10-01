package co.fcv.citas.auth;

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
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "USER")
class PasswordResetIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    @Test
    void requestingForKnownEmailReturnsDevTokenAndForUnknownEmailDoesNot() throws Exception {
        String email = registerUser();

        MvcResult known = mockMvc.perform(post("/api/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isOk()).andReturn();
        JsonNode knownBody = objectMapper.readTree(known.getResponse().getContentAsString());
        assertThat(knownBody.get("devToken").asText()).isNotBlank();

        MvcResult unknown = mockMvc.perform(post("/api/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "no-existe-" + UUID.randomUUID() + "@demo.invalid"))))
                .andExpect(status().isOk()).andReturn();
        JsonNode unknownBody = objectMapper.readTree(unknown.getResponse().getContentAsString());
        assertThat(unknownBody.has("devToken") && !unknownBody.get("devToken").isNull()).isFalse();
    }

    @Test
    void confirmingWithValidTokenChangesPasswordAndRejectsReuse() throws Exception {
        String email = registerUser();
        String token = requestToken(email);

        mockMvc.perform(post("/api/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", token, "newPassword", "NuevaClave123*"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "NuevaClave123*"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());

        mockMvc.perform(post("/api/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", token, "newPassword", "OtraClave456*"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_reset_token"));
    }

    @Test
    void confirmingWithExpiredTokenIsRejected() throws Exception {
        String email = registerUser();
        String token = requestToken(email);
        jdbc.update("update password_reset_tokens set expires_at = ? where token_hash = " +
                "(select token_hash from password_reset_tokens order by id desc limit 1)", Instant.now().minusSeconds(60));

        mockMvc.perform(post("/api/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", token, "newPassword", "NuevaClave123*"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_reset_token"));
    }

    private String requestToken(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("devToken").asText();
    }

    private String registerUser() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@demo.invalid";
        String document = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("firstName", "Ana", "lastName", "Prueba", "documentType", "CC",
                                "documentNumber", document, "email", email, "phone", "3001234567", "password", "Secure123*"))))
                .andExpect(status().isCreated());
        return email;
    }
}
