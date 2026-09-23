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
    public Long getStatusId() { return statusId; }
    public void changeStatus(Long statusId) { this.statusId = statusId; }
}
