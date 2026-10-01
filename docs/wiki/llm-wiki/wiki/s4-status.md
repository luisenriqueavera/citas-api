# S4 — Gestión avanzada de citas y MVP

**Fecha:** 2026-09-30
**Estado:** backend completo y verificado; frontend conectado para las pantallas reconstruidas en esta ronda.

## Implementado

- `V6__s4_appointment_lifecycle.sql` agrega estados `CANCELLED`/`COMPLETED`/`NO_SHOW`, `password_reset_tokens`, `reschedule_requests` y `reschedule_request_slots`.
- `GET /api/v1/me/appointments` (con filtro por estado/fecha) y `GET /api/v1/me/appointments/{id}/history` — USER consulta sus propias citas e historial de auditoría.
- `POST /api/v1/me/appointments/{id}/cancel` — cancelación con liberación de slots y auditoría.
- `POST /api/v1/me/appointments/{id}/reschedule-requests` y `POST /api/v1/admin/reschedule-requests/{id}/decision` — reprogramación con retención de franja nueva y conservación de la original hasta la decisión de ADMIN.
- `GET /api/v1/professional/appointments` y `POST /api/v1/professional/appointments/{id}/close` — agenda y cierre de atención (`COMPLETED`/`NO_SHOW`) por profesional, con aislamiento entre profesionales.
- CRUD de EPS y planes de EPS (`/api/v1/admin/eps*`), más edición de especialidades; corregido el filtro `eps.active` que antes permitía ofrecer planes de una EPS desactivada.
- `POST /api/auth/password-reset/request` y `/confirm` — recuperación de contraseña de un solo uso, sin SMTP, token expuesto solo en la respuesta de desarrollo.
- `AppointmentWebhookNotifier`: webhook saliente deshabilitado por defecto (`APPOINTMENT_WEBHOOK_URL`), disparado en cancelación, decisión de cita especializada y decisión de reprogramación. **No se probó contra una instancia real de n8n** (no disponible en este entorno); queda pendiente de que el usuario configure su propia instancia y credenciales (S5/S6).
- Todos los endpoints nuevos resuelven la identidad del usuario actuante desde el JWT (`CurrentUser`), no desde el body; se corrigió el mismo hueco en el endpoint de decisión de citas especializadas ya existente desde S3.

## Verificación

- Backend: `mvn test` (JDK 21, Maven local, sin Docker disponible en este entorno) — PASS; 34 pruebas, 0 fallos, 0 errores, incluidas las suites de regresión de S2/S3 sin modificar su comportamiento.
- Pruebas nuevas cubren: aislamiento entre pacientes y entre profesionales, liberación/retención de slots en cancelación y reprogramación, concurrencia reprogramación-vs-reserva directa, CRUD de EPS/planes sin borrado físico, ciclo completo de recuperación de contraseña (token único, expiración, no enumeración de correos), y el no-op del webhook sin URL configurada.
- Frontend: `npm run lint`, `npm test -- --watch=false` (10 pruebas) y `npm run build` (10 rutas prerenderizadas) — PASS.
- **Límite de esta verificación**: no se ejecutó el stack real (Docker/MySQL no disponibles en este entorno de ejecución). Se intentó levantar `citas-api` contra H2 vía `mvn spring-boot:run`, pero falló porque el driver H2 tiene `scope=test` en `pom.xml` (diseño correcto: producción usa MySQL), así que no hay forma de servir la app sin una base de datos real. Las pruebas de integración (`mvn test`) sí ejercen el stack HTTP/JWT/Flyway/BD completo contra H2 dentro de cada test, pero **no reemplazan una prueba manual en navegador contra MySQL**. Antes de dar S4 por cerrado, el estudiante debe levantar `docker-compose up` y validar manualmente al menos: login de los 3 roles demo, reservar/cancelar/reprogramar una cita, cerrar una cita como profesional, CRUD de una EPS/plan, y el flujo de recuperación de contraseña.

## Pendiente para un corte posterior

- Migrar las llamadas HTTP ya existentes en `citas-web` (S2/S3) para usar la nueva `apiBaseUrl` configurable; de momento solo los métodos nuevos de S4 la usan.
- Vista de calendario de "Mis Citas": se eliminó la versión decorativa (datos falsos de abril 2024); una vista de calendario real queda fuera de alcance de S4.
- S5/S6 reales (n8n, MCP, Gmail OAuth): requieren instancia y credenciales propias del usuario; no abordado en esta ronda.
- Verificación manual en navegador contra el stack Docker real (ver nota de límite arriba).
