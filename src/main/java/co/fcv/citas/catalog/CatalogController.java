package co.fcv.citas.catalog;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/catalogs")
public class CatalogController {
    private final JdbcTemplate jdbc;
    public CatalogController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/locations")
    public List<LocationOption> locations() { return jdbc.query("select id, code, name, address, city from locations where active = true order by name", (rs, row) ->
            new LocationOption(rs.getLong("id"), rs.getString("code"), rs.getString("name"), rs.getString("address"), rs.getString("city"))); }
    @GetMapping("/specialties")
    public List<SpecialtyOption> specialties() { return jdbc.query("select id, code, name, is_general, duration_minutes from specialties where active = true order by name", (rs, row) ->
            new SpecialtyOption(rs.getLong("id"), rs.getString("code"), rs.getString("name"), rs.getBoolean("is_general"), rs.getInt("duration_minutes"))); }
    @GetMapping("/appointment-statuses")
    public List<CodeOption> statuses() { return jdbc.query("select id, code, name from appointment_statuses order by id", (rs, row) ->
            new CodeOption(rs.getLong("id"), rs.getString("code"), rs.getString("name"))); }
    @GetMapping("/roles")
    public List<CodeOption> roles() { return jdbc.query("select id, code, code from roles order by id", (rs, row) ->
            new CodeOption(rs.getLong(1), rs.getString(2), rs.getString(3))); }
    @GetMapping("/professionals")
    public List<ProfessionalOption> professionals() { return jdbc.query(
            "select p.id, concat(u.first_name, ' ', u.last_name) name, p.professional_code from professionals p " +
            "join users u on u.id = p.user_id where p.active = true order by name",
            (rs, row) -> new ProfessionalOption(rs.getLong("id"), rs.getString("name"), rs.getString("professional_code"))); }

    public record LocationOption(Long id, String code, String name, String address, String city) { }
    public record SpecialtyOption(Long id, String code, String name, boolean general, int durationMinutes) { }
    public record CodeOption(Long id, String code, String name) { }
    public record ProfessionalOption(Long id, String name, String professionalCode) { }
}
