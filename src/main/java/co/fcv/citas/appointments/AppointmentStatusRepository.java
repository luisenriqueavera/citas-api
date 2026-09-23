package co.fcv.citas.appointments;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AppointmentStatusRepository extends JpaRepository<AppointmentStatus, Long> {
    Optional<AppointmentStatus> findByCode(String code);
}
