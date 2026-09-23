package co.fcv.citas.appointments;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ProfessionalSlotRepository extends JpaRepository<ProfessionalSlot, Long> {
    @Query(value = "select count(*) from professional_slots where id = :id and appointment_id is not null", nativeQuery = true)
    long countReservedAppointments(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ProfessionalSlot s where s.id in :ids order by s.id")
    List<ProfessionalSlot> findAllByIdForUpdate(@Param("ids") List<Long> ids);
}
