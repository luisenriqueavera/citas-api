package co.fcv.citas.insurance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface InsurancePlanRepository extends JpaRepository<InsurancePlan, Long> {
    List<InsurancePlan> findAllByActiveTrueOrderByEpsNameAscNameAsc();
    Optional<InsurancePlan> findByIdAndActiveTrue(Long id);

    @Query(value = "select p.* from eps_plans p join eps e on e.id = p.eps_id where p.active = true and e.active = true order by p.eps_name, p.name", nativeQuery = true)
    List<InsurancePlan> findAllActiveWithActiveEps();

    @Query(value = "select p.* from eps_plans p join eps e on e.id = p.eps_id where p.id = :id and p.active = true and e.active = true", nativeQuery = true)
    Optional<InsurancePlan> findActiveByIdWithActiveEps(@Param("id") Long id);
}
