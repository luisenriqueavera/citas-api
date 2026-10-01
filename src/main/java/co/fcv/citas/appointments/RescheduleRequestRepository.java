package co.fcv.citas.appointments;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RescheduleRequestRepository extends JpaRepository<RescheduleRequest, Long> {
    Optional<RescheduleRequest> findByIdAndStatus(Long id, String status);
    List<RescheduleRequest> findAllByAppointmentIdAndStatus(Long appointmentId, String status);
}
