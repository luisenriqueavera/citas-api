package co.fcv.citas.appointments;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    long countByPatientUserId(Long patientUserId);
    java.util.List<Appointment> findAllByStatusId(Long statusId);
}
