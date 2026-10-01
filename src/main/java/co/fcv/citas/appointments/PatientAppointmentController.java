package co.fcv.citas.appointments;

import co.fcv.citas.config.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
import static co.fcv.citas.appointments.AppointmentDtos.*;

@RestController
@RequestMapping("/api/v1/me/appointments")
public class PatientAppointmentController {
    private final AppointmentService service;
    public PatientAppointmentController(AppointmentService service) { this.service = service; }

    @GetMapping
    public List<AppointmentDetailResponse> list(Authentication authentication,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return service.listForPatient(CurrentUser.id(authentication), status, from, to);
    }

    @GetMapping("/{id}/history")
    public List<AppointmentHistoryEntry> history(Authentication authentication, @PathVariable Long id) {
        return service.history(id, CurrentUser.id(authentication));
    }

    @PostMapping("/{id}/cancel")
    public CancelResponse cancel(Authentication authentication, @PathVariable Long id) {
        return service.cancel(id, CurrentUser.id(authentication));
    }

    @PostMapping("/{id}/reschedule-requests") @ResponseStatus(HttpStatus.CREATED)
    public RescheduleRequestResponse requestReschedule(Authentication authentication, @PathVariable Long id,
            @Valid @RequestBody RescheduleRequestPayload payload) {
        return service.requestReschedule(id, CurrentUser.id(authentication), payload);
    }
}
