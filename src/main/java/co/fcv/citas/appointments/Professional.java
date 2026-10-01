package co.fcv.citas.appointments;

import jakarta.persistence.*;

@Entity
@Table(name = "professionals")
public class Professional {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false, unique = true) private Long userId;
    @Column(name = "professional_code", nullable = false, unique = true) private String professionalCode;
    @Column(name = "license_number", nullable = false, unique = true) private String licenseNumber;
    @Column(nullable = false) private boolean active;
    protected Professional() { }
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getProfessionalCode() { return professionalCode; }
    public boolean isActive() { return active; }
}
