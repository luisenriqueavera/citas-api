---
id: HU-021
epica: EP-004
estado: Aprobada
esfuerzo: Medio
---
# HU-021 — Agenda del profesional

**COMO** PROFESSIONAL, **QUIERO** ver mis citas `APPROVED` por día, semana y sede, **PARA** organizar mi jornada sin ver datos de otros profesionales.

## Criterios de aceptación

- CA-01: `GET /api/v1/professional/appointments` solo devuelve citas donde el profesional corresponde al usuario autenticado.
- CA-02: soporta filtro por fecha (día), rango y sede.
- CA-03: un profesional nunca puede leer la agenda de otro aunque conozca su identificador.

## DoD

Endpoint, prueba de aislamiento entre profesionales, UI de "Mi Agenda" conectada reemplazando los turnos hardcodeados, `mvn test` y build frontend en verde.
