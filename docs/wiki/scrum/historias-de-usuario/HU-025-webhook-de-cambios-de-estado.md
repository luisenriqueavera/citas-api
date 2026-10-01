---
id: HU-025
epica: EP-007
estado: Aprobada
esfuerzo: Bajo
---
# HU-025 — Webhook de cambios de estado (stub)

**COMO** sistema, **QUIERO** notificar cambios de estado de citas a una URL externa configurable, **PARA** habilitar automatizaciones futuras (n8n) sin tocar de nuevo el código de citas.

## Criterios de aceptación

- CA-01: sin `APPOINTMENT_WEBHOOK_URL` configurada, el mecanismo es un no-op total: no intenta red y no lanza error.
- CA-02: con la URL configurada, se envía un POST JSON tras aprobar/rechazar una cita especializada, cancelar una cita, y aprobar/rechazar una reprogramación.
- CA-03: un fallo de red o timeout del webhook nunca revierte ni bloquea la transacción principal.
- CA-04: la wiki documenta que el mecanismo no se probó contra una instancia real de n8n (no disponible) y que está pendiente de credenciales del usuario.

## DoD

Componente y configuración, pruebas unitarias del no-op y del manejo de fallos, nota en `s4-status.md`, `mvn test` en verde.
