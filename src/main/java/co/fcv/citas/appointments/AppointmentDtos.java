package co.fcv.citas.appointments;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public final class AppointmentDtos {
    private AppointmentDtos() { }
    public record CreateAppointmentRequest(
            @NotNull Long patientUserId,
            @NotNull Long professionalId,
            @NotNull Long locationId,
            @NotNull Long specialtyId,
            List<Long> slotIds,
            String startAt,
            String reason) { }
    public record AppointmentResponse(Long id, String status, List<Long> slotIds) { }
}
