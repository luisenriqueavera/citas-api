INSERT INTO users (id, first_name, last_name, document_type, document_number, email, phone, password_hash, active)
VALUES (102, 'Administrador', 'Demo', 'CC', 'DEMO-ADMIN-102', 'admin.demo@fcv.local', '3000000102', '$2a$10$5Ih8hESl5H6aHVoyl3KVxuEng7F39cDlTqaRyAzOQDJd1BnSpag0S', TRUE);

INSERT INTO user_roles (user_id, role_id)
SELECT 102, id FROM roles WHERE code = 'ADMIN';
