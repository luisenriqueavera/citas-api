# Log de la wiki

## 2026-09-30

- INGEST: se incorporó la auditoría LOOP_00 S1–S3.
- DECISIÓN: no avanzar a S4–S6 mientras el gate tenga pendientes humanos.
- DOC: se generaron `AGENTS.md` y Scrum specs con estado pendiente de aprobación.
- DECISIÓN: con LOOP_00 S1–S3 en verde, el estudiante autorizó iniciar S4 (MVP completo) en la misma sesión.
- DOC: se redactaron y aprobaron EP-003..EP-007 y HU-018..HU-025 (gestión de citas del paciente, operación del profesional, catálogos configurables, recuperación de contraseña, webhook stub) como parte del plan de implementación revisado por el estudiante.
- BUILD: S4 backend implementado y verificado con `mvn test` (34/34 en verde); ver [[s4-status]].

## 2026-10-01

- FIX: `citas-web/fcv-data.service.ts` — los 13 metodos heredados de S2/S3 que llamaban rutas relativas (`/api/...`) ahora usan `apiBaseUrl`, igual que los metodos de S4. Gap marcado como pendiente en [[s4-status]].
- BUILD: mitigacion de riesgos S5 a nivel de codigo (sin tocar la instancia real de n8n): secreto compartido para el webhook de WF-002, escape de HTML en los 8 mensajes de WF-001/002/003, y rol `AUTOMATION` de minimo privilegio con endpoint dedicado `POST /api/v1/admin/automation-accounts`. Detalle en [[s6-wf002-status]] y [[s5-untrusted-content-and-residual-risks]].
- LIMITE: Docker Desktop no arranco en el entorno de esta sesion; ningun cambio de `citas-api` se compilo con `mvn test`. Pendiente correr la suite completa (incluye `AutomationRoleIntegrationTest` y los 2 casos nuevos de `AppointmentWebhookNotifierTest`) en cuanto el stack este disponible.
