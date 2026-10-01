package co.fcv.citas.appointments;

import co.fcv.citas.notifications.AppointmentWebhookNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static co.fcv.citas.appointments.AppointmentDtos.*;

@Service
public class AppointmentService {
    private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private final AppointmentRepository appointments;
    private final ProfessionalSlotRepository slots;
    private final SpecialtyRepository specialties;
    private final AppointmentStatusRepository statuses;
    private final RescheduleRequestRepository rescheduleRequests;
    private final AppointmentWebhookNotifier webhookNotifier;
    private final JdbcTemplate jdbc;

    public AppointmentService(AppointmentRepository appointments, ProfessionalSlotRepository slots,
                              SpecialtyRepository specialties, AppointmentStatusRepository statuses,
                              RescheduleRequestRepository rescheduleRequests, AppointmentWebhookNotifier webhookNotifier,
                              JdbcTemplate jdbc) {
        this.appointments = appointments; this.slots = slots; this.specialties = specialties; this.statuses = statuses;
        this.rescheduleRequests = rescheduleRequests; this.webhookNotifier = webhookNotifier;
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

    public List<AppointmentDetailResponse> listForPatient(Long patientUserId, String statusCode, LocalDate from, LocalDate to) {
        StringBuilder sql = new StringBuilder("select a.id, a.professional_id, a.location_id, a.specialty_id, st.code status, " +
                "a.scheduled_start_at, a.scheduled_end_at, s.duration_minutes, a.reason, " +
                "(select h.reason from appointment_status_history h join appointment_statuses rs on rs.id = h.status_id " +
                " where h.appointment_id = a.id and rs.code = 'REJECTED' order by h.changed_at desc limit 1) rejection_reason " +
                "from appointments a join appointment_statuses st on st.id = a.status_id join specialties s on s.id = a.specialty_id " +
                "where a.patient_user_id = ?");
        List<Object> args = new ArrayList<>(List.of(patientUserId));
        if (statusCode != null) { sql.append(" and st.code = ?"); args.add(statusCode); }
        if (from != null) { sql.append(" and a.scheduled_start_at >= ?"); args.add(from.atStartOfDay()); }
        if (to != null) { sql.append(" and a.scheduled_start_at < ?"); args.add(to.plusDays(1).atStartOfDay()); }
        sql.append(" order by a.scheduled_start_at desc");
        return jdbc.query(sql.toString(), (rs, row) -> new AppointmentDetailResponse(
                rs.getLong("id"), rs.getLong("professional_id"), rs.getLong("location_id"), rs.getLong("specialty_id"),
                rs.getString("status"), rs.getObject("scheduled_start_at", LocalDateTime.class).toString(),
                rs.getObject("scheduled_end_at", LocalDateTime.class).toString(), rs.getInt("duration_minutes"),
                rs.getString("reason"), rs.getString("rejection_reason")), args.toArray());
    }

    public List<AppointmentHistoryEntry> history(Long appointmentId, Long requesterUserId) {
        Appointment appointment = appointments.findById(appointmentId).orElseThrow(AppointmentNotFoundException::new);
        if (!appointment.getPatientUserId().equals(requesterUserId)) throw new ForbiddenAppointmentException();
        return jdbc.query("select st.code status, h.change_source, h.reason, h.changed_at from appointment_status_history h " +
                "join appointment_statuses st on st.id = h.status_id where h.appointment_id = ? order by h.changed_at",
                (rs, row) -> new AppointmentHistoryEntry(rs.getString("status"), rs.getString("change_source"),
                        rs.getString("reason"), rs.getObject("changed_at", LocalDateTime.class).toString()), appointmentId);
    }

    @Transactional
    public CancelResponse cancel(Long appointmentId, Long requesterUserId) {
        Appointment appointment = appointments.findById(appointmentId).orElseThrow(AppointmentNotFoundException::new);
        if (!appointment.getPatientUserId().equals(requesterUserId)) throw new ForbiddenAppointmentException();
        AppointmentStatus currentStatus = statuses.findById(appointment.getStatusId()).orElseThrow(() -> new IllegalStateException("status is missing"));
        if (!Set.of("REQUESTED", "APPROVED").contains(currentStatus.getCode())) throw new InvalidTransitionException("appointment is not cancellable");
        if (!appointment.getScheduledStartAt().isAfter(LocalDateTime.now(BOGOTA))) throw new InvalidTransitionException("appointment already started");

        List<Long> slotIds = slots.findAllByAppointmentId(appointmentId).stream().map(ProfessionalSlot::getId).sorted().toList();
        List<ProfessionalSlot> lockedSlots = slots.findAllByIdForUpdate(slotIds);
        lockedSlots.forEach(ProfessionalSlot::release);
        slots.saveAllAndFlush(lockedSlots);

        AppointmentStatus cancelled = statuses.findByCode("CANCELLED").orElseThrow(() -> new IllegalStateException("status is missing"));
        appointment.changeStatus(cancelled.getId());
        appointments.saveAndFlush(appointment);
        jdbc.update("insert into appointment_status_history (appointment_id, status_id, changed_by_user_id, change_source, reason) values (?, ?, ?, 'USER', ?)",
                appointmentId, cancelled.getId(), requesterUserId, null);
        webhookNotifier.notifyStatusChange(new AppointmentWebhookNotifier.AppointmentStatusEvent(
                appointmentId, currentStatus.getCode(), "CANCELLED", "USER", null, Instant.now().toString()));
        log.info("Appointment cancelled: appointmentId={}, slots={}", appointmentId, slotIds);
        return new CancelResponse(appointmentId, "CANCELLED");
    }

    @Transactional
    public RescheduleRequestResponse requestReschedule(Long appointmentId, Long requesterUserId, RescheduleRequestPayload payload) {
        Appointment appointment = appointments.findById(appointmentId).orElseThrow(AppointmentNotFoundException::new);
        if (!appointment.getPatientUserId().equals(requesterUserId)) throw new ForbiddenAppointmentException();
        AppointmentStatus currentStatus = statuses.findById(appointment.getStatusId()).orElseThrow(() -> new IllegalStateException("status is missing"));
        if (!"APPROVED".equals(currentStatus.getCode())) throw new InvalidTransitionException("only approved appointments can be rescheduled");
        if (!appointment.getScheduledStartAt().isAfter(LocalDateTime.now(BOGOTA))) throw new InvalidTransitionException("appointment already started");

        List<Long> requestedIds = payload.slotIds() == null ? List.of() : payload.slotIds().stream().distinct().sorted().toList();
        if (requestedIds.isEmpty() && payload.startAt() != null) {
            LocalDateTime start = LocalDateTime.parse(payload.startAt());
            requestedIds = jdbc.query("select ps.id from professional_slots ps join availability_blocks b on b.id = ps.availability_block_id " +
                    "where ps.professional_id = ? and ps.start_at = ? and ps.appointment_id is null order by ps.id",
                    (rs, row) -> rs.getLong(1), appointment.getProfessionalId(), start);
            Specialty specialty = specialties.findById(appointment.getSpecialtyId()).orElseThrow(() -> new IllegalStateException("specialty is missing"));
            int duration = specialty.isGeneral() ? 30 : 60;
            if (duration == 60 && requestedIds.size() < 2) throw new SlotAlreadyReservedException();
            if (duration == 60) requestedIds = requestedIds.subList(0, 2);
        }
        if (requestedIds.isEmpty()) throw new InvalidAppointmentException("slotIds or startAt is required");

        List<ProfessionalSlot> lockedSlots = slots.findAllByIdForUpdate(requestedIds);
        if (lockedSlots.size() != requestedIds.size()) throw new InvalidAppointmentException("slot not found");
        if (lockedSlots.stream().anyMatch(slot -> !slot.getProfessionalId().equals(appointment.getProfessionalId())))
            throw new InvalidAppointmentException("slot does not belong to the appointment's professional");
        if (lockedSlots.stream().anyMatch(slot -> slot.getAppointmentId() != null)) throw new SlotAlreadyReservedException();

        lockedSlots.forEach(slot -> slot.reserve(appointmentId));
        slots.saveAllAndFlush(lockedSlots);

        RescheduleRequest request = rescheduleRequests.saveAndFlush(new RescheduleRequest(appointmentId, requesterUserId));
        List<Long> finalRequestedIds = requestedIds;
        finalRequestedIds.forEach(slotId -> jdbc.update("insert into reschedule_request_slots (reschedule_request_id, slot_id) values (?, ?)", request.getId(), slotId));

        String note = (payload.reason() == null || payload.reason().isBlank()) ? "Solicitud de reprogramación" : "Solicitud de reprogramación: " + payload.reason();
        jdbc.update("insert into appointment_status_history (appointment_id, status_id, changed_by_user_id, change_source, reason) values (?, ?, ?, 'USER', ?)",
                appointmentId, currentStatus.getId(), requesterUserId, note);
        log.info("Reschedule requested: appointmentId={}, requestId={}, newSlots={}", appointmentId, request.getId(), requestedIds);
        return new RescheduleRequestResponse(request.getId(), appointmentId, request.getStatus(), requestedIds);
    }

    public List<Map<String, Object>> listPendingReschedules() {
        return jdbc.queryForList("select r.id, r.appointment_id, r.requested_by_user_id, r.reason, r.created_at, " +
                "a.professional_id, a.location_id, a.specialty_id, a.scheduled_start_at old_start_at, a.scheduled_end_at old_end_at " +
                "from reschedule_requests r join appointments a on a.id = r.appointment_id where r.status = 'PENDING' order by r.created_at");
    }

    @Transactional
    public RescheduleDecisionResponse decideReschedule(Long rescheduleRequestId, Long adminUserId, String decision, String reason) {
        if (!"APPROVE".equals(decision) && !"REJECT".equals(decision)) throw new InvalidAppointmentException("decision must be APPROVE or REJECT");
        if ("REJECT".equals(decision) && (reason == null || reason.isBlank())) throw new InvalidAppointmentException("rejection reason is required");
        RescheduleRequest request = rescheduleRequests.findByIdAndStatus(rescheduleRequestId, "PENDING").orElseThrow(AppointmentNotFoundException::new);
        Appointment appointment = appointments.findById(request.getAppointmentId()).orElseThrow(AppointmentNotFoundException::new);

        List<Long> newSlotIds = jdbc.query("select slot_id from reschedule_request_slots where reschedule_request_id = ? order by slot_id",
                (rs, row) -> rs.getLong(1), rescheduleRequestId);
        List<Long> oldSlotIds = jdbc.query("select ps.id from professional_slots ps where ps.appointment_id = ? " +
                "and ps.id not in (select slot_id from reschedule_request_slots where reschedule_request_id = ?) order by ps.id",
                (rs, row) -> rs.getLong(1), appointment.getId(), rescheduleRequestId);

        List<Long> unionIds = new ArrayList<>(new TreeSet<>(Stream.concat(oldSlotIds.stream(), newSlotIds.stream()).toList()));
        List<ProfessionalSlot> lockedSlots = slots.findAllByIdForUpdate(unionIds);
        Map<Long, ProfessionalSlot> byId = lockedSlots.stream().collect(Collectors.toMap(ProfessionalSlot::getId, slot -> slot));

        String resultStatus;
        if ("APPROVE".equals(decision)) {
            oldSlotIds.forEach(id -> byId.get(id).release());
            ProfessionalSlot first = byId.get(newSlotIds.get(0));
            ProfessionalSlot last = byId.get(newSlotIds.get(newSlotIds.size() - 1));
            appointment.reschedule(first.getLocationId(), first.getStartAt(), last.getEndAt());
            appointments.saveAndFlush(appointment);
            request.approve(adminUserId);
            resultStatus = "APPROVED";
        } else {
            newSlotIds.forEach(id -> byId.get(id).release());
            request.reject(adminUserId, reason);
            resultStatus = "REJECTED";
        }
        slots.saveAllAndFlush(lockedSlots);
        rescheduleRequests.saveAndFlush(request);
        jdbc.update("insert into appointment_status_history (appointment_id, status_id, changed_by_user_id, change_source, reason) values (?, ?, ?, 'ADMIN', ?)",
                appointment.getId(), appointment.getStatusId(), adminUserId,
                "APPROVE".equals(decision) ? "Reprogramación aprobada" : ("Reprogramación rechazada: " + reason));
        webhookNotifier.notifyStatusChange(new AppointmentWebhookNotifier.AppointmentStatusEvent(
                appointment.getId(), "APPROVED", "APPROVED", "ADMIN", "RESCHEDULE_" + resultStatus + (reason != null ? ": " + reason : ""), Instant.now().toString()));
        log.info("Reschedule decided: requestId={}, appointmentId={}, decision={}", rescheduleRequestId, appointment.getId(), resultStatus);
        return new RescheduleDecisionResponse(request.getId(), appointment.getId(), resultStatus);
    }

    public List<ProfessionalAgendaEntry> listForProfessional(Long professionalId, LocalDate date, LocalDate from, LocalDate to, Long locationId) {
        StringBuilder sql = new StringBuilder("select a.id, a.patient_user_id, concat(u.first_name, ' ', u.last_name) patient_name, " +
                "a.location_id, a.specialty_id, st.code status, a.scheduled_start_at, a.scheduled_end_at, s.duration_minutes, a.reason " +
                "from appointments a join appointment_statuses st on st.id = a.status_id join specialties s on s.id = a.specialty_id " +
                "join users u on u.id = a.patient_user_id where a.professional_id = ? and st.code = 'APPROVED'");
        List<Object> args = new ArrayList<>(List.of(professionalId));
        if (date != null) { sql.append(" and cast(a.scheduled_start_at as date) = ?"); args.add(date); }
        if (from != null) { sql.append(" and a.scheduled_start_at >= ?"); args.add(from.atStartOfDay()); }
        if (to != null) { sql.append(" and a.scheduled_start_at < ?"); args.add(to.plusDays(1).atStartOfDay()); }
        if (locationId != null) { sql.append(" and a.location_id = ?"); args.add(locationId); }
        sql.append(" order by a.scheduled_start_at");
        return jdbc.query(sql.toString(), (rs, row) -> new ProfessionalAgendaEntry(
                rs.getLong("id"), rs.getLong("patient_user_id"), rs.getString("patient_name"), rs.getLong("location_id"), rs.getLong("specialty_id"),
                rs.getString("status"), rs.getObject("scheduled_start_at", LocalDateTime.class).toString(),
                rs.getObject("scheduled_end_at", LocalDateTime.class).toString(), rs.getInt("duration_minutes"), rs.getString("reason")), args.toArray());
    }

    @Transactional
    public CloseOutResponse closeOut(Long appointmentId, Long professionalId, Long actingUserId, String outcome) {
        if (!"COMPLETED".equals(outcome) && !"NO_SHOW".equals(outcome)) throw new InvalidAppointmentException("outcome must be COMPLETED or NO_SHOW");
        Appointment appointment = appointments.findById(appointmentId).orElseThrow(AppointmentNotFoundException::new);
        if (!appointment.getProfessionalId().equals(professionalId)) throw new ForbiddenAppointmentException();
        AppointmentStatus currentStatus = statuses.findById(appointment.getStatusId()).orElseThrow(() -> new IllegalStateException("status is missing"));
        if (!"APPROVED".equals(currentStatus.getCode())) throw new InvalidTransitionException("appointment is not approved");
        if (!appointment.getScheduledStartAt().isBefore(LocalDateTime.now(BOGOTA))) throw new InvalidTransitionException("appointment has not started yet");

        AppointmentStatus newStatus = statuses.findByCode(outcome).orElseThrow(() -> new IllegalStateException("status is missing"));
        appointment.changeStatus(newStatus.getId());
        appointments.saveAndFlush(appointment);
        jdbc.update("insert into appointment_status_history (appointment_id, status_id, changed_by_user_id, change_source, reason) values (?, ?, ?, 'PROFESSIONAL', null)",
                appointmentId, newStatus.getId(), actingUserId);
        log.info("Appointment closed out: appointmentId={}, outcome={}", appointmentId, outcome);
        return new CloseOutResponse(appointmentId, outcome);
    }

    public static class SlotAlreadyReservedException extends RuntimeException { }
    public static class InvalidAppointmentException extends RuntimeException {
        public InvalidAppointmentException(String message) { super(message); }
    }
    public static class AppointmentNotFoundException extends RuntimeException { }
    public static class ForbiddenAppointmentException extends RuntimeException { }
    public static class InvalidTransitionException extends RuntimeException {
        public InvalidTransitionException(String message) { super(message); }
    }
}
