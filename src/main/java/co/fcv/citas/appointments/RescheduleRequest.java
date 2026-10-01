package co.fcv.citas.appointments;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "reschedule_requests")
public class RescheduleRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "appointment_id", nullable = false) private Long appointmentId;
    @Column(name = "requested_by_user_id", nullable = false) private Long requestedByUserId;
    @Column(nullable = false) private String status = "PENDING";
    private String reason;
    @Column(name = "decided_by_user_id") private Long decidedByUserId;
    @Column(name = "decided_at") private Instant decidedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();

    protected RescheduleRequest() { }
    public RescheduleRequest(Long appointmentId, Long requestedByUserId) {
        this.appointmentId = appointmentId; this.requestedByUserId = requestedByUserId;
    }
    public Long getId() { return id; }
    public Long getAppointmentId() { return appointmentId; }
    public Long getRequestedByUserId() { return requestedByUserId; }
    public String getStatus() { return status; }
    public String getReason() { return reason; }

    public void approve(Long decidedByUserId) {
        if (!"PENDING".equals(status)) throw new IllegalStateException("reschedule request is not pending");
        this.status = "APPROVED"; this.decidedByUserId = decidedByUserId; this.decidedAt = Instant.now();
    }
    public void reject(Long decidedByUserId, String reason) {
        if (!"PENDING".equals(status)) throw new IllegalStateException("reschedule request is not pending");
        this.status = "REJECTED"; this.decidedByUserId = decidedByUserId; this.decidedAt = Instant.now(); this.reason = reason;
    }
}
