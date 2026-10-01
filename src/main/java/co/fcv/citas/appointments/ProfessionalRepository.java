package co.fcv.citas.appointments;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ProfessionalRepository extends JpaRepository<Professional, Long> {
    Optional<Professional> findByUserIdAndActiveTrue(Long userId);
}
