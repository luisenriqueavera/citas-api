package co.fcv.citas.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByDocumentTypeAndDocumentNumber(String documentType, String documentNumber);
    Optional<UserAccount> findByEmailIgnoreCase(String email);
}
