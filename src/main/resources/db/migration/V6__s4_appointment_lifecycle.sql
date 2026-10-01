INSERT INTO appointment_statuses (code, name) VALUES
    ('CANCELLED', 'Cancelada'),
    ('COMPLETED', 'Atendida'),
    ('NO_SHOW', 'No asistió');

CREATE TABLE password_reset_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE INDEX idx_password_reset_user ON password_reset_tokens (user_id);

CREATE TABLE reschedule_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    appointment_id BIGINT NOT NULL,
    requested_by_user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reason VARCHAR(500),
    decided_by_user_id BIGINT,
    decided_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reschedule_appointment FOREIGN KEY (appointment_id) REFERENCES appointments(id),
    CONSTRAINT fk_reschedule_requested_by FOREIGN KEY (requested_by_user_id) REFERENCES users(id),
    CONSTRAINT fk_reschedule_decided_by FOREIGN KEY (decided_by_user_id) REFERENCES users(id)
);
CREATE INDEX idx_reschedule_requests_status ON reschedule_requests (status);
CREATE INDEX idx_reschedule_requests_appointment ON reschedule_requests (appointment_id);

CREATE TABLE reschedule_request_slots (
    reschedule_request_id BIGINT NOT NULL,
    slot_id BIGINT NOT NULL,
    PRIMARY KEY (reschedule_request_id, slot_id),
    CONSTRAINT fk_reschedule_slot_request FOREIGN KEY (reschedule_request_id) REFERENCES reschedule_requests(id),
    CONSTRAINT fk_reschedule_slot_slot FOREIGN KEY (slot_id) REFERENCES professional_slots(id)
);

CREATE INDEX idx_appointments_patient_user ON appointments (patient_user_id);
CREATE INDEX idx_appointments_professional_status ON appointments (professional_id, status_id);
CREATE INDEX idx_status_history_appointment ON appointment_status_history (appointment_id);
