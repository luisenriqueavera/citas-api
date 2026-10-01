---
id: HU-019
epica: EP-003
estado: Aprobada
esfuerzo: Medio
---
# HU-019 — Cancelación de citas

**COMO** USER, **QUIERO** cancelar una cita futura no terminal, **PARA** liberar el cupo si ya no la necesito.

## Criterios de aceptación

- CA-01: solo citas `APPROVED`/`REQUESTED` con inicio futuro son cancelables.
- CA-02: cancelar pone el estado en `CANCELLED`, libera todos los slots asociados y nunca permite reactivarla.
- CA-03: cancelar una cita que no pertenece al usuario autenticado devuelve `403`; una cita inexistente devuelve `404`; un estado terminal o ya pasado devuelve `409`.
- CA-04: se inserta historial de auditoría (`appointment_status_history`, `change_source = 'USER'`).

## DoD

Endpoint, pruebas de los cuatro casos anteriores, UI conectada (el modal existente llama la API real), `mvn test` y build frontend en verde.
