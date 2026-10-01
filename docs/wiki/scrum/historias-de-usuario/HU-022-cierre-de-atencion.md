---
id: HU-022
epica: EP-004
estado: Aprobada
esfuerzo: Bajo
---
# HU-022 — Cierre de atención

**COMO** PROFESSIONAL, **QUIERO** marcar una cita pasada como `COMPLETED` o `NO_SHOW`, **PARA** dejar constancia de la atención.

## Criterios de aceptación

- CA-01: solo citas `APPROVED`, propias y con inicio ya transcurrido pueden cerrarse.
- CA-02: el cierre es una transición terminal y queda auditada.
- CA-03: cerrar una cita de otro profesional devuelve `403`; cerrar una cita futura devuelve `409`.

## DoD

Endpoint, pruebas de los tres casos anteriores, botones de "Mi Agenda" conectados a la API real, `mvn test` y build frontend en verde.
