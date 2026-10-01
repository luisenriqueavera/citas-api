---
id: HU-024
epica: EP-006
estado: Aprobada
esfuerzo: Medio
---
# HU-024 — Recuperación de contraseña

**COMO** USER, **QUIERO** solicitar un token de recuperación y usarlo una sola vez para cambiar mi contraseña, **PARA** recuperar acceso sin depender de SMTP real.

## Criterios de aceptación

- CA-01: `POST /api/auth/password-reset/request` genera un token de un solo uso que expira a los 15 minutos; en este entorno de desarrollo se expone en la respuesta y en el log, nunca en un entorno de producción real.
- CA-02: solicitar un token para un correo inexistente responde `200` de forma genérica, sin revelar si el correo existe.
- CA-03: `POST /api/auth/password-reset/confirm` consume el token (un solo uso) y cambia la contraseña; un token ya usado, expirado o inexistente devuelve `400`.

## DoD

Migración, endpoints, pruebas de los tres casos anteriores, modal de "¿Olvidaste tu contraseña?" en login conectado a la API real, `mvn test` y build frontend en verde.
