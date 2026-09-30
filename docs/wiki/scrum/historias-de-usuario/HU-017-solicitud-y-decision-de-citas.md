---
id: HU-017
epica: EP-002
estado: Aprobada
esfuerzo: Alto
---
# HU-017 — Solicitud y decisión de citas

**COMO** USER, **QUIERO** solicitar una cita general o especializada, **PARA** recibir confirmación automática o decisión administrativa.

## Criterios de aceptación

- CA-01: cita general queda `APPROVED`.
- CA-02: cita especializada queda `REQUESTED` y retiene slots.
- CA-03: ADMIN aprueba o rechaza; el rechazo exige motivo y libera slots.
- CA-04: una reserva incompatible devuelve `409`.

## DoD

Backend, UI conectada, autorización por rol, auditoría de estados, pruebas de concurrencia y build frontend en verde.
