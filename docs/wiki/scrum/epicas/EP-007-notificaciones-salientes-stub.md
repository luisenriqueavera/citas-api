---
id: EP-007
estado: Aprobada
---
# EP-007 — Notificaciones salientes (stub)

## Objetivo

Preparar un mecanismo de notificación saliente configurable para cambios de estado de citas, como base para la integración n8n de S5/S6, sin activarla ni probarla contra infraestructura real.

## HU

- [[HU-025-webhook-de-cambios-de-estado]]

## Reglas

Sin `APPOINTMENT_WEBHOOK_URL` configurada, el mecanismo es un no-op total. Un fallo de red nunca bloquea ni revierte la transacción principal. No se prueba contra una instancia real de n8n por no estar disponible; queda pendiente de credenciales del usuario.
