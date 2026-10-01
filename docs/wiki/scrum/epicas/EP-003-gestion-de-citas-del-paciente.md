---
id: EP-003
estado: Aprobada
---
# EP-003 — Gestión de citas del paciente

## Objetivo

Permitir que USER consulte, cancele y reprograme sus propias citas sin perder la franja original hasta que ADMIN decida.

## HU

- [[HU-018-mis-citas]]
- [[HU-019-cancelacion-de-citas]]
- [[HU-020-reprogramacion-de-citas]]

## Reglas

Identidad del paciente resuelta desde el JWT; cancelar libera slots; reprogramar retiene la franja nueva en `PENDING` sin tocar la cita original hasta la decisión de ADMIN.
