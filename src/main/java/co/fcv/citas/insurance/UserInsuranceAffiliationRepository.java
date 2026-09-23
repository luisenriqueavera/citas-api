package co.fcv.citas.insurance;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserInsuranceAffiliationRepository extends JpaRepository<UserInsuranceAffiliation, Long> {
    long countByUserId(Long userId);
}
