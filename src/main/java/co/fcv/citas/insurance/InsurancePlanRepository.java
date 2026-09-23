package co.fcv.citas.insurance;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface InsurancePlanRepository extends JpaRepository<InsurancePlan, Long> {
    List<InsurancePlan> findAllByActiveTrueOrderByEpsNameAscNameAsc();
    Optional<InsurancePlan> findByIdAndActiveTrue(Long id);
}
