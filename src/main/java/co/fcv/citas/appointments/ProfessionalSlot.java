package co.fcv.citas.appointments;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "professional_slots")
public class ProfessionalSlot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "professional_id", nullable = false) private Long professionalId;
    @Column(name = "location_id", nullable = false) private Long locationId;
    @Column(name = "start_at", nullable = false) private LocalDateTime startAt;
    @Column(name = "end_at", nullable = false) private LocalDateTime endAt;
    @Column(name = "appointment_id") private Long appointmentId;
    protected ProfessionalSlot() { }
    public Long getId() { return id; }
    public LocalDateTime getStartAt() { return startAt; }
    public LocalDateTime getEndAt() { return endAt; }
    public Long getAppointmentId() { return appointmentId; }
    public void reserve(Long appointmentId) { this.appointmentId = appointmentId; }
}
