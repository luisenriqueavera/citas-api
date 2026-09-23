package co.fcv.citas.appointments;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDateTime;
import java.util.*;
import static co.fcv.citas.appointments.AppointmentDtos.*;

@Service
public class AppointmentService {
    private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);
    private final AppointmentRepository appointments;
    private final ProfessionalSlotRepository slots;
    private final SpecialtyRepository specialties;
    private final AppointmentStatusRepository statuses;
    private final JdbcTemplate jdbc;

    public AppointmentService(AppointmentRepository appointments, ProfessionalSlotRepository slots,
                              SpecialtyRepository specialties, AppointmentStatusRepository statuses, JdbcTemplate jdbc) {
        this.appointments = appointments; this.slots = slots; this.specialties = specialties; this.statuses = statuses;
        this.jdbc = jdbc;
    }

    @Transactional
    public AppointmentResponse reserve(CreateAppointmentRequest request) {
        List<Long> requestedIds = request.slotIds() == null ? List.of() : request.slotIds().stream().distinct().sorted().toList();
        if (requestedIds.isEmpty() && request.startAt() != null) {
            LocalDateTime start = LocalDateTime.parse(request.startAt());
            requestedIds = jdbc.query("select ps.id from professional_slots ps join availability_blocks b on b.id = ps.availability_block_id where ps.professional_id = ? and ps.start_at = ? and ps.appointment_id is null order by ps.id",
                    (rs, row) -> rs.getLong(1), request.professionalId(), start);
            int duration = specialties.findById(request.specialtyId()).map(s -> s.isGeneral() ? 30 : 60).orElse(0);
            if (duration == 60 && requestedIds.size() < 2) throw new SlotAlreadyReservedException();
            if (duration == 60) requestedIds = requestedIds.subList(0, 2);
        }
        if (requestedIds.isEmpty()) throw new InvalidAppointmentException("slotIds or startAt is required");
        if (requestedIds.size() != request.slotIds().size()) throw new InvalidAppointmentException("duplicate slots");
        Specialty specialty = specialties.findById(request.specialtyId()).filter(Specialty::isActive)
                .orElseThrow(() -> new InvalidAppointmentException("specialty is not active"));

        // The pessimistic lock serializes competing transactions before checking availability.
        List<ProfessionalSlot> lockedSlots = slots.findAllByIdForUpdate(requestedIds);
        if (lockedSlots.size() != requestedIds.size()) throw new InvalidAppointmentException("slot not found");
        if (lockedSlots.stream().anyMatch(slot -> slot.getAppointmentId() != null)) {
            log.warn("Reservation rejected: slots={} already reserved", requestedIds);
            throw new SlotAlreadyReservedException();
        }
        if (lockedSlots.stream().anyMatch(slot -> !slot.getStartAt().isBefore(slot.getEndAt()))) {
            throw new InvalidAppointmentException("invalid slot range");
        }

        AppointmentStatus status = statuses.findByCode(specialty.isGeneral() ? "APPROVED" : "REQUESTED")
                .orElseThrow(() -> new IllegalStateException("appointment status is missing"));
        ProfessionalSlot first = lockedSlots.get(0);
        ProfessionalSlot last = lockedSlots.get(lockedSlots.size() - 1);
        Appointment appointment = appointments.saveAndFlush(new Appointment(request.patientUserId(), request.professionalId(),
                request.locationId(), request.specialtyId(), status.getId(), request.reason(), first.getStartAt(), last.getEndAt()));
        lockedSlots.forEach(slot -> slot.reserve(appointment.getId()));
        slots.saveAllAndFlush(lockedSlots);
        jdbc.update("insert into appointment_status_history (appointment_id, status_id, changed_by_user_id, change_source, reason) values (?, ?, ?, ?, ?)",
                appointment.getId(), status.getId(), request.patientUserId(), specialty.isGeneral() ? "SYSTEM" : "USER", request.reason());
        log.info("Reservation accepted: appointmentId={}, status={}, slots={}", appointment.getId(), status.getCode(), requestedIds);
        return new AppointmentResponse(appointment.getId(), status.getCode(), requestedIds);
    }

    public static class SlotAlreadyReservedException extends RuntimeException { }
    public static class InvalidAppointmentException extends RuntimeException {
        public InvalidAppointmentException(String message) { super(message); }
    }
}
