package co.fcv.citas.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
public class UserAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "first_name", nullable = false) private String firstName;
    @Column(name = "last_name", nullable = false) private String lastName;
    @Column(name = "document_type", nullable = false) private String documentType;
    @Column(name = "document_number", nullable = false) private String documentNumber;
    @Column(nullable = false) private String email;
    @Column(nullable = false) private String phone;
    @Column(name = "password_hash", nullable = false) private String passwordHash;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    protected UserAccount() { }
    public UserAccount(String firstName, String lastName, String documentType, String documentNumber, String email, String phone, String passwordHash) {
        this.firstName = firstName; this.lastName = lastName; this.documentType = documentType; this.documentNumber = documentNumber;
        this.email = email; this.phone = phone; this.passwordHash = passwordHash;
    }
    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isActive() { return active; }
    public Set<Role> getRoles() { return roles; }
    public void addRole(Role role) { roles.add(role); }
}
