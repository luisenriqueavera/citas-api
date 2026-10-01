package co.fcv.citas.catalog;

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
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "USER")
class AdminInsuranceCatalogIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void createsEditsAndTogglesEpsAndPlansWithoutPhysicalDeletion() throws Exception {
        String code = "EPS_TEST_" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/v1/admin/eps").with(user("102").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", code, "name", "EPS Prueba"))))
                .andExpect(status().isCreated());
        long epsId = findEpsId(code);

        mockMvc.perform(patch("/api/v1/admin/eps/{id}", epsId).with(user("102").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "EPS Prueba Renombrada"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/eps/{id}/plans", epsId).with(user("102").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "PLAN_X", "name", "Plan X"))))
                .andExpect(status().isCreated());

        JsonNode eps = findEps(code);
        assertThat(eps.get("name").asText()).isEqualTo("EPS Prueba Renombrada");
        JsonNode plan = eps.get("plans").get(0);
        assertThat(plan.get("name").asText()).isEqualTo("Plan X");

        mockMvc.perform(patch("/api/v1/admin/eps-plans/{id}/active", plan.get("id").asLong()).with(user("102").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("active", false))))
                .andExpect(status().isOk());

        JsonNode reloadedPlan = findEps(code).get("plans").get(0);
        assertThat(reloadedPlan.get("active").asBoolean()).isFalse();
    }

    @Test
    void deactivatingEpsRemovesItsPlanFromPublicCatalogAndRegistration() throws Exception {
        String code = "EPS_OFF_" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/v1/admin/eps").with(user("102").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", code, "name", "EPS Para Desactivar"))))
                .andExpect(status().isCreated());
        long epsId = findEpsId(code);
        mockMvc.perform(post("/api/v1/admin/eps/{id}/plans", epsId).with(user("102").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "PLAN_OFF", "name", "Plan Para Desactivar"))))
                .andExpect(status().isCreated());
        long planId = findEps(code).get("plans").get(0).get("id").asLong();

        assertThat(planVisibleInPublicCatalog(planId)).isTrue();

        mockMvc.perform(patch("/api/v1/admin/eps/{id}/active", epsId).with(user("102").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("active", false))))
                .andExpect(status().isOk());

        assertThat(planVisibleInPublicCatalog(planId)).isFalse();

        String email = "user-" + UUID.randomUUID() + "@demo.invalid";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("firstName", "Ana", "lastName", "Plan", "documentType", "CC",
                                "documentNumber", UUID.randomUUID().toString().replace("-", "").substring(0, 20), "email", email,
                                "phone", "3001234567", "password", "Secure123*", "planId", planId))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_plan"));
    }

    private boolean planVisibleInPublicCatalog(long planId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/insurance-plans")).andExpect(status().isOk()).andReturn();
        JsonNode plans = objectMapper.readTree(result.getResponse().getContentAsString());
        for (JsonNode plan : plans) if (plan.get("id").asLong() == planId) return true;
        return false;
    }

    private long findEpsId(String code) throws Exception { return findEps(code).get("id").asLong(); }

    private JsonNode findEps(String code) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/admin/eps").with(user("102").roles("ADMIN"))).andExpect(status().isOk()).andReturn();
        JsonNode epsList = objectMapper.readTree(result.getResponse().getContentAsString());
        for (JsonNode eps : epsList) if (eps.get("code").asText().equals(code)) return eps;
        throw new AssertionError("eps not found: " + code);
    }
}
