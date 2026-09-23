package co.fcv.citas.insurance;

import co.fcv.citas.auth.UserAccount;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_insurance_affiliations")
public class UserInsuranceAffiliation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "plan_id", nullable = false)
    private InsurancePlan plan;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();

    protected UserInsuranceAffiliation() { }
    public UserInsuranceAffiliation(UserAccount user, InsurancePlan plan) { this.user = user; this.plan = plan; }
    public Long getId() { return id; }
    public UserAccount getUser() { return user; }
    public InsurancePlan getPlan() { return plan; }
}
