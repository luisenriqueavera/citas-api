package co.fcv.citas.insurance;

import jakarta.persistence.*;

@Entity
@Table(name = "eps_plans")
public class InsurancePlan {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "eps_name", nullable = false) private String epsName;
    @Column(nullable = false) private String name;
    @Column(nullable = false) private boolean active;

    protected InsurancePlan() { }
    public Long getId() { return id; }
    public String getEpsName() { return epsName; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
}
