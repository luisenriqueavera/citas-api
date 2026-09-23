# S3 — Core de agendamiento

**Fecha:** 2026-09-23  
**Estado:** núcleo backend validado; integración frontend del flujo de búsqueda y reserva validada por build.

## Implementado

- Flyway `V3__appointment_slots.sql` y `V4__s3_offer_and_audit.sql` agregan estados, especialidades, sedes, profesionales, asignaciones, bloques, slots e historial sin modificar V1.
- `GET /api/v1/catalogs/locations`, `/specialties`, `/appointment-statuses`, `/roles` y `/catalogs/insurance-plans` son lecturas.
- ADMIN puede crear/desactivar especialidades, crear profesionales con BCrypt, asignar especialidades/sedes y activar/desactivar profesionales.
- PROFESSIONAL puede crear, consultar, modificar y eliminar bloques futuros; la API valida sede asignada, profesional activo, fecha futura, múltiplos de 30 minutos y solapamiento, y genera slots atómicos.
- `GET /api/v1/availability` sólo devuelve slots de profesionales activos, asociados a la especialidad y habilitados en la sede; una especialidad de 60 minutos exige dos consecutivos.
- `POST /api/v1/appointments` admite `slotIds` o `startAt`, bloquea los slots con `PESSIMISTIC_WRITE`, crea general como `APPROVED`, especializada como `REQUESTED`, guarda historial y devuelve `409` cuando la franja dejó de estar disponible.
- `GET /api/v1/admin/appointments/pending` y `POST /api/v1/admin/appointments/{id}/decision` permiten aprobar o rechazar; el rechazo exige motivo y libera slots.
- El frontend consulta disponibilidad y confirma reservas por REST; se protege el prerender SSR para no llamar la API durante la compilación.

## Verificación

- Backend: `mvn -q test` en contenedor Maven — PASS; 12 pruebas, 0 fallos, 0 errores.
- Evidencia de concurrencia: respuestas `[201 APPROVED, 409 slot_already_reserved]`; el slot queda asociado a una única cita.
- Especializada: respuesta `201 REQUESTED`, con retención de los slots solicitados.
- Frontend: `npm test -- --watch=false` — PASS; 1 prueba.
- Frontend: `npm run lint` — PASS.
- Frontend: `npm run build` — PASS; prerender de 9 rutas.

## Pendiente para un corte posterior

- Las pantallas administrativas y profesionales conservan partes del prototipo visual y aún no están conectadas a todos los endpoints S3.
- `Mis citas`, agenda profesional, cancelación, reprogramación y auditoría visual completa corresponden a historias posteriores.
- No se implementó CRUD de EPS/planes, conforme al alcance.
