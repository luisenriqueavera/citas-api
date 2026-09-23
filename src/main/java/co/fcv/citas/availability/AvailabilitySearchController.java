package co.fcv.citas.availability;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/availability")
public class AvailabilitySearchController {
    private final JdbcTemplate jdbc;
    public AvailabilitySearchController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping
    public List<AvailabilityOption> search(@RequestParam LocalDate date, @RequestParam(required = false) Long locationId,
                                           @RequestParam(required = false) Long specialtyId, @RequestParam(required = false) Long professionalId) {
        StringBuilder sql = new StringBuilder("select ps.id slot_id, ps.professional_id, ps.location_id, ps.start_at, ps.end_at, p.professional_code, s.id specialty_id, s.name specialty_name, s.is_general, s.duration_minutes, l.name location_name from professional_slots ps join professionals p on p.id = ps.professional_id join professional_specialties pa on pa.professional_id = p.id and pa.active = true join specialties s on s.id = pa.specialty_id and s.active = true join professional_locations pl on pl.professional_id = p.id and pl.location_id = ps.location_id and pl.active = true join locations l on l.id = ps.location_id and l.active = true where p.active = true and cast(ps.start_at as date) = ? and ps.appointment_id is null");
        List<Object> args = new ArrayList<>(List.of(date));
        if (locationId != null) { sql.append(" and ps.location_id = ?"); args.add(locationId); }
        if (specialtyId != null) { sql.append(" and s.id = ?"); args.add(specialtyId); }
        if (professionalId != null) { sql.append(" and ps.professional_id = ?"); args.add(professionalId); }
        sql.append(" order by ps.start_at");
        List<Row> rows = jdbc.query(sql.toString(), (rs, row) -> new Row(rs.getLong("slot_id"), rs.getLong("professional_id"), rs.getLong("location_id"), rs.getObject("start_at", LocalDateTime.class), rs.getObject("end_at", LocalDateTime.class), rs.getString("professional_code"), rs.getLong("specialty_id"), rs.getString("specialty_name"), rs.getBoolean("is_general"), rs.getInt("duration_minutes"), rs.getString("location_name")), args.toArray());
        List<AvailabilityOption> result = new ArrayList<>();
        for (Row row : rows) {
            if (row.durationMinutes == 60) {
                Row next = rows.stream().filter(candidate -> candidate.professionalId == row.professionalId && candidate.locationId == row.locationId && candidate.specialtyId == row.specialtyId && candidate.start.equals(row.end)).findFirst().orElse(null);
                if (next == null) continue;
                result.add(new AvailabilityOption(row.slotId + "-" + next.slotId, row.professionalId, row.locationId, row.specialtyId, row.specialtyName, row.professionalCode, row.locationName, row.start, next.end, List.of(row.slotId, next.slotId), false));
            } else result.add(new AvailabilityOption(String.valueOf(row.slotId), row.professionalId, row.locationId, row.specialtyId, row.specialtyName, row.professionalCode, row.locationName, row.start, row.end, List.of(row.slotId), true));
        }
        return result;
    }
    private record Row(long slotId, long professionalId, long locationId, LocalDateTime start, LocalDateTime end, String professionalCode, long specialtyId, String specialtyName, boolean general, int durationMinutes, String locationName) { }
    public record AvailabilityOption(String id, Long professionalId, Long locationId, Long specialtyId, String specialtyName, String professionalCode, String locationName, LocalDateTime startAt, LocalDateTime endAt, List<Long> slotIds, boolean general) { }
}
