package co.fcv.citas.catalog;

import co.fcv.citas.auth.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminCatalogController {
    private final JdbcTemplate jdbc; private final UserAccountRepository users; private final RoleRepository roles; private final PasswordEncoder encoder;
    public AdminCatalogController(JdbcTemplate jdbc, UserAccountRepository users, RoleRepository roles, PasswordEncoder encoder) {
        this.jdbc = jdbc; this.users = users; this.roles = roles; this.encoder = encoder;
    }
    @PostMapping("/specialties") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createSpecialty(@Valid @RequestBody SpecialtyRequest request) {
        if (request.durationMinutes() != 30 && request.durationMinutes() != 60) throw new InvalidCatalogException("duration must be 30 or 60");
        jdbc.update("insert into specialties (code, name, is_general, duration_minutes, active) values (?, ?, ?, ?, true)", request.code(), request.name(), request.general(), request.durationMinutes());
        return Map.of("code", request.code(), "name", request.name(), "durationMinutes", request.durationMinutes(), "active", true);
    }
    @PatchMapping("/specialties/{id}/active")
    public Map<String, Object> changeSpecialty(@PathVariable Long id, @RequestBody ActiveRequest request) {
        if (jdbc.update("update specialties set active = ? where id = ?", request.active(), id) != 1) throw new InvalidCatalogException("specialty not found");
        return Map.of("id", id, "active", request.active());
    }
    @PatchMapping("/specialties/{id}")
    public Map<String, Object> updateSpecialty(@PathVariable Long id, @Valid @RequestBody SpecialtyUpdateRequest request) {
        if (request.durationMinutes() != 30 && request.durationMinutes() != 60) throw new InvalidCatalogException("duration must be 30 or 60");
        if (jdbc.update("update specialties set name = ?, duration_minutes = ? where id = ?", request.name(), request.durationMinutes(), id) != 1) throw new InvalidCatalogException("specialty not found");
        return Map.of("id", id, "name", request.name(), "durationMinutes", request.durationMinutes());
    }

    @GetMapping("/eps")
    public List<Map<String, Object>> listEps() {
        List<Map<String, Object>> epsRows = jdbc.queryForList("select id, code, name, active from eps order by name");
        List<Map<String, Object>> planRows = jdbc.queryForList("select id, eps_id, code, name, active from eps_plans order by eps_id, name");
        for (Map<String, Object> eps : epsRows) {
            long epsId = ((Number) eps.get("id")).longValue();
            eps.put("plans", planRows.stream().filter(plan -> ((Number) plan.get("eps_id")).longValue() == epsId).toList());
        }
        return epsRows;
    }
    @PostMapping("/eps") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createEps(@Valid @RequestBody EpsRequest request) {
        if (!jdbc.query("select id from eps where code = ?", (rs, row) -> rs.getLong(1), request.code()).isEmpty())
            throw new InvalidCatalogException("eps code already exists");
        jdbc.update("insert into eps (code, name, active) values (?, ?, true)", request.code(), request.name());
        return Map.of("code", request.code(), "name", request.name(), "active", true);
    }
    @PatchMapping("/eps/{id}")
    public Map<String, Object> updateEps(@PathVariable Long id, @Valid @RequestBody EpsNameRequest request) {
        if (jdbc.update("update eps set name = ? where id = ?", request.name(), id) != 1) throw new InvalidCatalogException("eps not found");
        jdbc.update("update eps_plans set eps_name = ? where eps_id = ?", request.name(), id);
        return Map.of("id", id, "name", request.name());
    }
    @PatchMapping("/eps/{id}/active")
    public Map<String, Object> changeEpsActive(@PathVariable Long id, @RequestBody ActiveRequest request) {
        if (jdbc.update("update eps set active = ? where id = ?", request.active(), id) != 1) throw new InvalidCatalogException("eps not found");
        return Map.of("id", id, "active", request.active());
    }
    @PostMapping("/eps/{id}/plans") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createEpsPlan(@PathVariable Long id, @Valid @RequestBody EpsPlanRequest request) {
        List<String> epsNames = jdbc.query("select name from eps where id = ?", (rs, row) -> rs.getString(1), id);
        if (epsNames.isEmpty()) throw new InvalidCatalogException("eps not found");
        if (!jdbc.query("select id from eps_plans where eps_id = ? and code = ?", (rs, row) -> rs.getLong(1), id, request.code()).isEmpty())
            throw new InvalidCatalogException("plan code already exists for this eps");
        jdbc.update("insert into eps_plans (eps_id, code, eps_name, name, active) values (?, ?, ?, ?, true)", id, request.code(), epsNames.get(0), request.name());
        return Map.of("epsId", id, "code", request.code(), "name", request.name(), "active", true);
    }
    @PatchMapping("/eps-plans/{id}")
    public Map<String, Object> updateEpsPlan(@PathVariable Long id, @Valid @RequestBody EpsPlanNameRequest request) {
        if (jdbc.update("update eps_plans set name = ? where id = ?", request.name(), id) != 1) throw new InvalidCatalogException("plan not found");
        return Map.of("id", id, "name", request.name());
    }
    @PatchMapping("/eps-plans/{id}/active")
    public Map<String, Object> changeEpsPlanActive(@PathVariable Long id, @RequestBody ActiveRequest request) {
        if (jdbc.update("update eps_plans set active = ? where id = ?", request.active(), id) != 1) throw new InvalidCatalogException("plan not found");
        return Map.of("id", id, "active", request.active());
    }
    @PostMapping("/professionals") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Map<String, Object> createProfessional(@Valid @RequestBody ProfessionalRequest request) {
        Role role = roles.findByCode("PROFESSIONAL").orElseThrow(() -> new IllegalStateException("PROFESSIONAL role is missing"));
        UserAccount user = new UserAccount(request.firstName(), request.lastName(), request.documentType(), request.documentNumber(), request.email(), request.phone(), encoder.encode(request.temporaryPassword()));
        user.addRole(role); users.saveAndFlush(user);
        jdbc.update("insert into professionals (user_id, professional_code, license_number, active) values (?, ?, ?, true)", user.getId(), request.professionalCode(), request.licenseNumber());
        return Map.of("id", jdbc.queryForObject("select id from professionals where user_id = ?", Long.class, user.getId()), "email", user.getEmail(), "professionalCode", request.professionalCode(), "active", true);
    }
    @GetMapping("/professionals")
    public List<Map<String, Object>> professionals() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            select p.id, u.first_name firstName, u.last_name lastName, u.email,
                   p.professional_code professionalCode, p.license_number licenseNumber,
                   p.active, s.name specialtyName, s.duration_minutes durationMinutes,
                   l.name locationName
              from professionals p
              join users u on u.id = p.user_id
              left join professional_specialties ps on ps.professional_id = p.id and ps.primary_specialty = true and ps.active = true
              left join specialties s on s.id = ps.specialty_id
              left join professional_locations pl on pl.professional_id = p.id and pl.active = true
              left join locations l on l.id = pl.location_id
             order by p.id, l.id
            """);
        Map<Long, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Long id = ((Number) row.get("id")).longValue();
            Map<String, Object> item = grouped.computeIfAbsent(id, ignored -> {
                Map<String, Object> copy = new HashMap<>(row);
                copy.remove("locationName");
                copy.put("locationNames", new ArrayList<String>());
                return copy;
            });
            Object location = row.get("locationName");
            if (location != null) ((List<String>) item.get("locationNames")).add(location.toString());
        }
        return new ArrayList<>(grouped.values());
    }
    @PutMapping("/professionals/{id}/specialties")
    public Map<String, Object> assignSpecialties(@PathVariable Long id, @RequestBody AssignmentRequest request) {
        jdbc.update("delete from professional_specialties where professional_id = ?", id);
        for (Long specialtyId : request.ids()) jdbc.update("insert into professional_specialties (professional_id, specialty_id, primary_specialty, active) values (?, ?, ?, true)", id, specialtyId, specialtyId.equals(request.primaryId()));
        return Map.of("professionalId", id, "specialtyIds", request.ids());
    }
    @PutMapping("/professionals/{id}/locations")
    public Map<String, Object> assignLocations(@PathVariable Long id, @RequestBody AssignmentRequest request) {
        jdbc.update("delete from professional_locations where professional_id = ?", id);
        for (Long locationId : request.ids()) jdbc.update("insert into professional_locations (professional_id, location_id, active) values (?, ?, true)", id, locationId);
        return Map.of("professionalId", id, "locationIds", request.ids());
    }
    @PatchMapping("/professionals/{id}/active")
    public Map<String, Object> changeProfessional(@PathVariable Long id, @RequestBody ActiveRequest request) {
        if (jdbc.update("update professionals set active = ? where id = ?", request.active(), id) != 1) throw new InvalidCatalogException("professional not found");
        return Map.of("id", id, "active", request.active());
    }
    @PostMapping("/automation-accounts") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Map<String, Object> createAutomationAccount(@Valid @RequestBody AutomationAccountRequest request) {
        Role role = roles.findByCode("AUTOMATION").orElseThrow(() -> new IllegalStateException("AUTOMATION role is missing"));
        UserAccount user = new UserAccount(request.firstName(), request.lastName(), request.documentType(), request.documentNumber(), request.email(), request.phone(), encoder.encode(request.temporaryPassword()));
        user.addRole(role); users.saveAndFlush(user);
        return Map.of("id", user.getId(), "email", user.getEmail());
    }

    public record SpecialtyRequest(@NotBlank String code, @NotBlank String name, boolean general, @NotNull Integer durationMinutes) { }
    public record SpecialtyUpdateRequest(@NotBlank String name, @NotNull Integer durationMinutes) { }
    public record ProfessionalRequest(@NotBlank String firstName, @NotBlank String lastName, @NotBlank String documentType, @NotBlank String documentNumber, @Email @NotBlank String email, @NotBlank String phone, @NotBlank String temporaryPassword, @NotBlank String professionalCode, @NotBlank String licenseNumber) { }
    public record AutomationAccountRequest(@NotBlank String firstName, @NotBlank String lastName, @NotBlank String documentType, @NotBlank String documentNumber, @Email @NotBlank String email, @NotBlank String phone, @NotBlank String temporaryPassword) { }
    public record AssignmentRequest(List<Long> ids, Long primaryId) { }
    public record ActiveRequest(boolean active) { }
    public record EpsRequest(@NotBlank String code, @NotBlank String name) { }
    public record EpsNameRequest(@NotBlank String name) { }
    public record EpsPlanRequest(@NotBlank String code, @NotBlank String name) { }
    public record EpsPlanNameRequest(@NotBlank String name) { }
    public static class InvalidCatalogException extends RuntimeException { public InvalidCatalogException(String message) { super(message); } }
}
