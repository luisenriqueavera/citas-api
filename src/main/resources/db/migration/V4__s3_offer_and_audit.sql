ALTER TABLE specialties ADD duration_minutes INTEGER NOT NULL DEFAULT 30;
UPDATE specialties SET duration_minutes = 30 WHERE code = 'GENERAL';
UPDATE specialties SET duration_minutes = 60 WHERE code <> 'GENERAL';

CREATE TABLE locations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(180) NOT NULL,
    address VARCHAR(255) NOT NULL,
    city VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE professionals (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    professional_code VARCHAR(40) NOT NULL UNIQUE,
    license_number VARCHAR(80) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_professional_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE professional_specialties (
    professional_id BIGINT NOT NULL,
    specialty_id BIGINT NOT NULL,
    primary_specialty BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (professional_id, specialty_id),
    CONSTRAINT fk_professional_specialty_professional FOREIGN KEY (professional_id) REFERENCES professionals(id),
    CONSTRAINT fk_professional_specialty_specialty FOREIGN KEY (specialty_id) REFERENCES specialties(id)
);

CREATE TABLE professional_locations (
    professional_id BIGINT NOT NULL,
    location_id BIGINT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (professional_id, location_id),
    CONSTRAINT fk_professional_location_professional FOREIGN KEY (professional_id) REFERENCES professionals(id),
    CONSTRAINT fk_professional_location_location FOREIGN KEY (location_id) REFERENCES locations(id)
);

CREATE TABLE availability_blocks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    professional_id BIGINT NOT NULL,
    location_id BIGINT NOT NULL,
    available_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_availability_professional FOREIGN KEY (professional_id) REFERENCES professionals(id),
    CONSTRAINT fk_availability_location FOREIGN KEY (location_id) REFERENCES locations(id)
);

ALTER TABLE professional_slots ADD availability_block_id BIGINT;
ALTER TABLE professional_slots ADD CONSTRAINT fk_slot_block FOREIGN KEY (availability_block_id) REFERENCES availability_blocks(id);

CREATE TABLE appointment_status_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    appointment_id BIGINT NOT NULL,
    status_id BIGINT NOT NULL,
    changed_by_user_id BIGINT,
    change_source VARCHAR(20) NOT NULL,
    reason VARCHAR(500),
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_history_appointment FOREIGN KEY (appointment_id) REFERENCES appointments(id),
    CONSTRAINT fk_history_status FOREIGN KEY (status_id) REFERENCES appointment_statuses(id),
    CONSTRAINT fk_history_user FOREIGN KEY (changed_by_user_id) REFERENCES users(id)
);

INSERT INTO locations (code, name, address, city, active) VALUES
    ('HIC', 'Hospital Internacional de Colombia', 'Km 7 Autopista Bucaramanga–Piedecuesta', 'Floridablanca', TRUE),
    ('ICV', 'Instituto Cardiovascular', 'Calle 155A No. 23-58', 'Floridablanca', TRUE);
INSERT INTO roles (code) VALUES ('PROFESSIONAL'), ('ADMIN');
INSERT INTO appointment_statuses (code, name) VALUES ('REJECTED', 'Rechazada');

INSERT INTO users (id, first_name, last_name, document_type, document_number, email, phone, password_hash, active)
VALUES
    (100, 'Paciente', 'Demo', 'CC', 'DEMO-USER-100', 'paciente.demo@fcv.local', '3000000100', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', TRUE),
    (101, 'Profesional', 'Demo', 'CC', 'DEMO-PRO-101', 'profesional.demo@fcv.local', '3000000101', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', TRUE);
INSERT INTO user_roles (user_id, role_id) SELECT 100, id FROM roles WHERE code = 'USER';
INSERT INTO user_roles (user_id, role_id) SELECT 101, id FROM roles WHERE code = 'PROFESSIONAL';
INSERT INTO professionals (id, user_id, professional_code, license_number, active) VALUES (1, 101, 'DEMO-PRO-01', 'DEMO-LIC-01', TRUE);
INSERT INTO professional_specialties (professional_id, specialty_id, primary_specialty, active) VALUES (1, 1, TRUE, TRUE), (1, 2, FALSE, TRUE);
INSERT INTO professional_locations (professional_id, location_id, active) VALUES (1, 1, TRUE), (1, 2, TRUE);
INSERT INTO professional_slots (professional_id, location_id, start_at, end_at) VALUES
    (1, 1, TIMESTAMP '2030-01-15 10:00:00', TIMESTAMP '2030-01-15 10:30:00'),
    (1, 1, TIMESTAMP '2030-01-15 10:30:00', TIMESTAMP '2030-01-15 11:00:00'),
    (1, 1, TIMESTAMP '2030-01-15 11:00:00', TIMESTAMP '2030-01-15 11:30:00'),
    (1, 1, TIMESTAMP '2030-01-15 11:30:00', TIMESTAMP '2030-01-15 12:00:00');
