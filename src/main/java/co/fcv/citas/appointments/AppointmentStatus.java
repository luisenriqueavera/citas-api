package co.fcv.citas.appointments;

import jakarta.persistence.*;

@Entity
@Table(name = "appointment_statuses")
public class AppointmentStatus {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true) private String code;
    @Column(nullable = false) private String name;
    protected AppointmentStatus() { }
    public Long getId() { return id; }
    public String getCode() { return code; }
}
