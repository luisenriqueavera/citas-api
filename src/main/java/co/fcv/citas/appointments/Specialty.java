package co.fcv.citas.appointments;

import jakarta.persistence.*;

@Entity
@Table(name = "specialties")
public class Specialty {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true) private String code;
    @Column(nullable = false) private String name;
    @Column(name = "is_general", nullable = false) private boolean general;
    @Column(nullable = false) private boolean active;
    protected Specialty() { }
    public Long getId() { return id; }
    public boolean isGeneral() { return general; }
    public boolean isActive() { return active; }
}
