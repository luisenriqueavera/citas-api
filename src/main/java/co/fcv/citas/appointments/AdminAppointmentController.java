package co.fcv.citas.appointments;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/appointments")
public class AdminAppointmentController {
    private final JdbcTemplate jdbc; private final AppointmentRepository appointments; private final AppointmentStatusRepository statuses; private final ProfessionalSlotRepository slots;
    public AdminAppointmentController(JdbcTemplate jdbc, AppointmentRepository appointments, AppointmentStatusRepository statuses, ProfessionalSlotRepository slots) {
        this.jdbc = jdbc; this.appointments = appointments; this.statuses = statuses; this.slots = slots;
    }
    @GetMapping("/pending")
    public List<Map<String, Object>> pending() {
        return jdbc.queryForList("select a.id, a.patient_user_id patientUserId, a.professional_id professionalId, a.location_id locationId, a.specialty_id specialtyId, a.scheduled_start_at startAt, a.scheduled_end_at endAt, a.reason from appointments a join appointment_statuses st on st.id = a.status_id join specialties sp on sp.id = a.specialty_id where st.code = 'REQUESTED' and sp.is_general = false order by a.scheduled_start_at");
    }
    @PostMapping("/{id}/decision") @Transactional
    public Map<String, Object> decide(@PathVariable Long id, @Valid @RequestBody DecisionRequest request) {
        Appointment appointment = appointments.findById(id).orElseThrow(() -> new AppointmentService.InvalidAppointmentException("appointment not found"));
        if (appointment.getStatusId() == null) throw new AppointmentService.InvalidAppointmentException("appointment has no status");
        if ("REJECT".equals(request.decision()) && (request.reason() == null || request.reason().isBlank())) throw new AppointmentService.InvalidAppointmentException("rejection reason is required");
        String code = "APPROVE".equals(request.decision()) ? "APPROVED" : "REJECTED";
        AppointmentStatus status = statuses.findByCode(code).orElseThrow(() -> new IllegalStateException("status is missing"));
        appointment.changeStatus(status.getId()); appointments.saveAndFlush(appointment);
        if ("REJECT".equals(request.decision())) {
            jdbc.update("update professional_slots set appointment_id = null where appointment_id = ?", id);
        }
        jdbc.update("insert into appointment_status_history (appointment_id, status_id, changed_by_user_id, change_source, reason) values (?, ?, ?, 'ADMIN', ?)", id, status.getId(), request.adminUserId(), request.reason());
        return Map.of("appointmentId", id, "status", code, "slotsReleased", "REJECT".equals(request.decision()));
    }
    public record DecisionRequest(@NotBlank String decision, Long adminUserId, String reason) { }
}
