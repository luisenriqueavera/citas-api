---
id: HU-018
epica: EP-003
estado: Aprobada
esfuerzo: Medio
---
# HU-018 — Mis citas

**COMO** USER, **QUIERO** ver mis citas y filtrarlas por estado y fecha, **PARA** conocer sede, profesional, especialidad, horario, duración, estado y motivo de rechazo.

## Criterios de aceptación

- CA-01: `GET /api/v1/me/appointments` solo devuelve citas del usuario autenticado, nunca de otro, aunque se fuerce un id distinto en query o body.
- CA-02: soporta filtro opcional por `status` (código de `appointment_statuses`) y por rango `from`/`to` de fecha.
- CA-03: cada ítem expone sede, profesional, especialidad, fecha/hora, duración, estado y `rejectionReason` cuando el estado es `REJECTED`.
- CA-04: la identidad del paciente se resuelve desde el JWT, nunca de un parámetro de la petición.

## DoD

Endpoint y autorización por rol USER, prueba de aislamiento entre pacientes, UI de "Mis Citas" conectada reemplazando el mock, `mvn test` y build frontend en verde.
