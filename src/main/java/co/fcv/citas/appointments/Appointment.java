package co.fcv.citas.appointments;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "appointments")
public class Appointment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "patient_user_id", nullable = false) private Long patientUserId;
    @Column(name = "professional_id", nullable = false) private Long professionalId;
    @Column(name = "location_id", nullable = false) private Long locationId;
    @Column(name = "specialty_id", nullable = false) private Long specialtyId;
    @Column(name = "status_id", nullable = false) private Long statusId;
    private String reason;
    @Column(name = "scheduled_start_at", nullable = false) private LocalDateTime scheduledStartAt;
    @Column(name = "scheduled_end_at", nullable = false) private LocalDateTime scheduledEndAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    protected Appointment() { }
    public Appointment(Long patientUserId, Long professionalId, Long locationId, Long specialtyId, Long statusId,
                       String reason, LocalDateTime start, LocalDateTime end) {
        this.patientUserId = patientUserId; this.professionalId = professionalId; this.locationId = locationId;
        this.specialtyId = specialtyId; this.statusId = statusId; this.reason = reason;
        this.scheduledStartAt = start; this.scheduledEndAt = end;
    }
    public Long getId() { return id; }
    public Long getPatientUserId() { return patientUserId; }
    public Long getProfessionalId() { return professionalId; }
    public Long getLocationId() { return locationId; }
    public Long getSpecialtyId() { return specialtyId; }
    public Long getStatusId() { return statusId; }
    public String getReason() { return reason; }
    public LocalDateTime getScheduledStartAt() { return scheduledStartAt; }
    public LocalDateTime getScheduledEndAt() { return scheduledEndAt; }
    public void changeStatus(Long statusId) { this.statusId = statusId; }
    public void reschedule(Long locationId, LocalDateTime start, LocalDateTime end) {
        this.locationId = locationId; this.scheduledStartAt = start; this.scheduledEndAt = end;
    }
}
