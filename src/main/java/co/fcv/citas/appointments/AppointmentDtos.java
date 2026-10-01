package co.fcv.citas.appointments;

import jakarta.validation.constraints.NotBlank;
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

    public record AppointmentDetailResponse(Long id, Long professionalId, Long locationId, Long specialtyId,
            String status, String scheduledStartAt, String scheduledEndAt, Integer durationMinutes,
            String reason, String rejectionReason) { }
    public record AppointmentHistoryEntry(String status, String changeSource, String reason, String changedAt) { }
    public record CancelResponse(Long id, String status) { }
    public record RescheduleRequestPayload(List<Long> slotIds, String startAt, String reason) { }
    public record RescheduleRequestResponse(Long id, Long appointmentId, String status, List<Long> slotIds) { }
    public record RescheduleDecisionRequest(@NotBlank String decision, String reason) { }
    public record RescheduleDecisionResponse(Long id, Long appointmentId, String status) { }
    public record CloseOutRequest(@NotBlank String outcome) { }
    public record CloseOutResponse(Long id, String status) { }
    public record ProfessionalAgendaEntry(Long id, Long patientUserId, String patientName, Long locationId,
            Long specialtyId, String status, String scheduledStartAt, String scheduledEndAt,
            Integer durationMinutes, String reason) { }
}
