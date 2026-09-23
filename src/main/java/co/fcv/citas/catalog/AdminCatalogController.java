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
    @PostMapping("/professionals") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Map<String, Object> createProfessional(@Valid @RequestBody ProfessionalRequest request) {
        Role role = roles.findByCode("PROFESSIONAL").orElseThrow(() -> new IllegalStateException("PROFESSIONAL role is missing"));
        UserAccount user = new UserAccount(request.firstName(), request.lastName(), request.documentType(), request.documentNumber(), request.email(), request.phone(), encoder.encode(request.temporaryPassword()));
        user.addRole(role); users.saveAndFlush(user);
        jdbc.update("insert into professionals (user_id, professional_code, license_number, active) values (?, ?, ?, true)", user.getId(), request.professionalCode(), request.licenseNumber());
        return Map.of("id", jdbc.queryForObject("select id from professionals where user_id = ?", Long.class, user.getId()), "email", user.getEmail(), "professionalCode", request.professionalCode(), "active", true);
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
    public record SpecialtyRequest(@NotBlank String code, @NotBlank String name, boolean general, @NotNull Integer durationMinutes) { }
    public record ProfessionalRequest(@NotBlank String firstName, @NotBlank String lastName, @NotBlank String documentType, @NotBlank String documentNumber, @Email @NotBlank String email, @NotBlank String phone, @NotBlank String temporaryPassword, @NotBlank String professionalCode, @NotBlank String licenseNumber) { }
    public record AssignmentRequest(List<Long> ids, Long primaryId) { }
    public record ActiveRequest(boolean active) { }
    public static class InvalidCatalogException extends RuntimeException { public InvalidCatalogException(String message) { super(message); } }
}
