---
id: HU-023
epica: EP-005
estado: Aprobada
esfuerzo: Medio
---
# HU-023 — CRUD de EPS y planes de EPS

**COMO** ADMIN, **QUIERO** crear, editar y activar/desactivar EPS y planes de EPS, **PARA** mantener el catálogo de afiliación sin borrar datos referenciados.

## Criterios de aceptación

- CA-01: ADMIN crea, edita y activa/desactiva EPS y planes; no existe borrado físico.
- CA-02: desactivar una EPS no borra sus planes, pero una EPS o plan inactivos dejan de ofrecerse en `GET /api/insurance-plans` y en el registro (`/api/auth/register`).
- CA-03: editar el nombre de una EPS actualiza el nombre denormalizado (`eps_plans.eps_name`) de todos sus planes.

## DoD

Endpoints, pruebas de los tres casos anteriores (incluida la corrección del filtro `eps.active`), página admin nueva conectada, `mvn test`, `npm run lint`/`test`/`build` en verde.
