package co.fcv.citas.appointments;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import static co.fcv.citas.appointments.AppointmentDtos.*;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {
    private final AppointmentService service;
    public AppointmentController(AppointmentService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse reserve(@Valid @RequestBody CreateAppointmentRequest request) {
        return service.reserve(request);
    }
}
