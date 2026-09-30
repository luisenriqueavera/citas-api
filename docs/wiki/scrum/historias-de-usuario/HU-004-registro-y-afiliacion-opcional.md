---
id: HU-004
epica: EP-001
estado: Aprobada
esfuerzo: Medio
---
# HU-004 — Registro y afiliación opcional

**COMO** visitante, **QUIERO** registrarme con o sin un plan activo, **PARA** crear una cuenta USER sin duplicar datos de EPS o plan.

## Criterios de aceptación

- CA-01: registrar sin `planId` crea un USER sin afiliación.
- CA-02: registrar con plan activo crea una afiliación cuyo `plan_id` referencia `eps_plans.id`.
- CA-03: plan inexistente o inactivo devuelve `400` con `invalid_plan` y no crea el usuario.
- CA-04: los nombres de EPS/plan no existen como columnas de `users`.
- CA-05: el frontend carga planes activos desde `/api/insurance-plans` y permite la opción sin afiliación.

## DoD

- Backend y frontend implementados sin contrato inventado.
- Pruebas backend para CA-01–CA-04.
- Prueba frontend del cargue y selección opcional.
- `mvn test`, lint, test y build frontend en verde.
- Contrato documentado y evidencia enlazada.
