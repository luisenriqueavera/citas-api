---
id: HU-020
epica: EP-003
estado: Aprobada
esfuerzo: Alto
---
# HU-020 — Reprogramación de citas aprobadas

**COMO** USER, **QUIERO** solicitar una nueva franja para una cita `APPROVED` futura, **PARA** cambiar mi horario sin perder la cita mientras se decide.

## Criterios de aceptación

- CA-01: solo citas `APPROVED` futuras aceptan solicitud de reprogramación; la franja nueva debe ser del mismo profesional y especialidad que la cita original.
- CA-02: la solicitud nace `PENDING`, retiene la(s) nueva(s) franja(s) (quedan indisponibles para terceros) y la cita original conserva su franja y estado intactos.
- CA-03: ADMIN aprueba (libera la franja antigua, asigna la nueva a la cita) o rechaza con motivo obligatorio (libera la franja nueva, conserva la original).
- CA-04: la bandeja administrativa (`GET /api/v1/admin/reschedule-requests/pending`) lista las solicitudes `PENDING`.
- CA-05: cada decisión queda auditada.

## DoD

Entidad y migración, servicio con bloqueo pesimista sobre slots viejos y nuevos, pruebas de estado y de concurrencia, UI conectada, `mvn test` y build frontend en verde.
