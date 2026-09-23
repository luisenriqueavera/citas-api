package co.fcv.citas.availability;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.*;

@RestController
@RequestMapping({"/api/v1/professional/availability-blocks", "/api/professional/availability-blocks"})
public class AvailabilityController {
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private final JdbcTemplate jdbc;
    public AvailabilityController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public BlockResponse create(@Valid @RequestBody BlockRequest request) {
        validateBlock(request);
        Integer assigned = jdbc.queryForObject("select count(*) from professional_locations where professional_id = ? and location_id = ? and active = true", Integer.class, request.professionalId(), request.locationId());
        if (assigned == null || assigned == 0) throw new AvailabilityException("professional is not assigned to location");
        Integer active = jdbc.queryForObject("select count(*) from professionals where id = ? and active = true", Integer.class, request.professionalId());
        if (active == null || active == 0) throw new AvailabilityException("professional is inactive");
        if (jdbc.queryForObject("select count(*) from availability_blocks where professional_id = ? and available_date = ? and active = true and start_time < ? and end_time > ?", Integer.class,
                request.professionalId(), request.availableDate(), request.endTime(), request.startTime()) > 0) throw new AvailabilityException("availability overlaps an existing block");
        jdbc.update("insert into availability_blocks (professional_id, location_id, available_date, start_time, end_time, active) values (?, ?, ?, ?, ?, true)", request.professionalId(), request.locationId(), request.availableDate(), request.startTime(), request.endTime());
        Long blockId = jdbc.queryForObject("select id from availability_blocks where professional_id = ? and available_date = ? and start_time = ? and end_time = ? order by id desc limit 1", Long.class, request.professionalId(), request.availableDate(), request.startTime(), request.endTime());
        LocalDateTime cursor = LocalDateTime.of(request.availableDate(), request.startTime());
        LocalDateTime end = LocalDateTime.of(request.availableDate(), request.endTime());
        while (cursor.isBefore(end)) {
            jdbc.update("insert into professional_slots (availability_block_id, professional_id, location_id, start_at, end_at) values (?, ?, ?, ?, ?)", blockId, request.professionalId(), request.locationId(), cursor, cursor.plusMinutes(30));
            cursor = cursor.plusMinutes(30);
        }
        return new BlockResponse(blockId, request.professionalId(), request.locationId(), request.availableDate(), request.startTime(), request.endTime(), true);
    }

    @GetMapping
    public List<BlockResponse> list(@RequestParam Long professionalId, @RequestParam(required = false) LocalDate date, @RequestParam(required = false) Long locationId) {
        StringBuilder sql = new StringBuilder("select id, professional_id, location_id, available_date, start_time, end_time, active from availability_blocks where professional_id = ?");
        List<Object> args = new ArrayList<>(List.of(professionalId));
        if (date != null) { sql.append(" and available_date = ?"); args.add(date); }
        if (locationId != null) { sql.append(" and location_id = ?"); args.add(locationId); }
        sql.append(" order by available_date, start_time");
        return jdbc.query(sql.toString(), (rs, row) -> new BlockResponse(rs.getLong("id"), rs.getLong("professional_id"), rs.getLong("location_id"), rs.getObject("available_date", LocalDate.class), rs.getObject("start_time", LocalTime.class), rs.getObject("end_time", LocalTime.class), rs.getBoolean("active")), args.toArray());
    }

    @PatchMapping("/{id}")
    public BlockResponse update(@PathVariable Long id, @Valid @RequestBody BlockRequest request) {
        Integer committed = jdbc.queryForObject("select count(*) from professional_slots where availability_block_id = ? and appointment_id is not null", Integer.class, id);
        if (committed != null && committed > 0) throw new AvailabilityException("block has committed slots");
        validateBlock(request);
        if (jdbc.update("update availability_blocks set available_date = ?, start_time = ?, end_time = ? where id = ?", request.availableDate(), request.startTime(), request.endTime(), id) != 1) throw new AvailabilityException("block not found");
        jdbc.update("delete from professional_slots where availability_block_id = ?", id);
        LocalDateTime cursor = LocalDateTime.of(request.availableDate(), request.startTime()), end = LocalDateTime.of(request.availableDate(), request.endTime());
        while (cursor.isBefore(end)) { jdbc.update("insert into professional_slots (availability_block_id, professional_id, location_id, start_at, end_at) select id, professional_id, location_id, ?, ? from availability_blocks where id = ?", cursor, cursor.plusMinutes(30), id); cursor = cursor.plusMinutes(30); }
        return list(request.professionalId(), request.availableDate(), request.locationId()).stream().filter(b -> b.id().equals(id)).findFirst().orElseThrow();
    }

    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        Integer committed = jdbc.queryForObject("select count(*) from professional_slots where availability_block_id = ? and appointment_id is not null", Integer.class, id);
        if (committed != null && committed > 0) throw new AvailabilityException("block has committed slots");
        jdbc.update("delete from professional_slots where availability_block_id = ?", id);
        if (jdbc.update("delete from availability_blocks where id = ?", id) != 1) throw new AvailabilityException("block not found");
    }

    private void validateBlock(BlockRequest r) {
        if (!r.availableDate().isAfter(LocalDate.now(BOGOTA)) || !r.startTime().isBefore(r.endTime()) || r.startTime().getMinute() % 30 != 0 || r.endTime().getMinute() % 30 != 0 || r.startTime().getSecond() != 0 || r.endTime().getSecond() != 0) throw new AvailabilityException("block must be future and aligned to 30 minutes");
        if (Duration.between(r.startTime(), r.endTime()).toMinutes() % 30 != 0) throw new AvailabilityException("block duration must be multiple of 30 minutes");
    }
    public record BlockRequest(@NotNull Long professionalId, @NotNull Long locationId, @NotNull LocalDate availableDate, @NotNull LocalTime startTime, @NotNull LocalTime endTime) { }
    public record BlockResponse(Long id, Long professionalId, Long locationId, LocalDate availableDate, LocalTime startTime, LocalTime endTime, boolean active) { }
    public static class AvailabilityException extends RuntimeException { public AvailabilityException(String message) { super(message); } }
}
