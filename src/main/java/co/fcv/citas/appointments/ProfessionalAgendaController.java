package co.fcv.citas.appointments;

import co.fcv.citas.config.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
import static co.fcv.citas.appointments.AppointmentDtos.*;

@RestController
@RequestMapping("/api/v1/professional/appointments")
public class ProfessionalAgendaController {
    private final AppointmentService service;
    private final ProfessionalRepository professionals;
    public ProfessionalAgendaController(AppointmentService service, ProfessionalRepository professionals) {
        this.service = service; this.professionals = professionals;
    }

    @GetMapping
    public List<ProfessionalAgendaEntry> agenda(Authentication authentication,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Long locationId) {
        Professional professional = currentProfessional(authentication);
        return service.listForProfessional(professional.getId(), date, from, to, locationId);
    }

    @PostMapping("/{id}/close")
    public CloseOutResponse close(Authentication authentication, @PathVariable Long id, @Valid @RequestBody CloseOutRequest request) {
        Professional professional = currentProfessional(authentication);
        return service.closeOut(id, professional.getId(), CurrentUser.id(authentication), request.outcome());
    }

    private Professional currentProfessional(Authentication authentication) {
        return professionals.findByUserIdAndActiveTrue(CurrentUser.id(authentication))
                .orElseThrow(AppointmentService.ForbiddenAppointmentException::new);
    }
}
