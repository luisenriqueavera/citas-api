---
id: EP-006
estado: Aprobada
---
# EP-006 — Recuperación de contraseña

## Objetivo

Permitir que USER recupere el acceso a su cuenta mediante un token temporal de un solo uso, sin depender de SMTP real.

## HU

- [[HU-024-recuperacion-de-contrasena]]

## Reglas

El token expira a los 15 minutos, es de un solo uso, y en este entorno de desarrollo se expone en la respuesta/log en vez de enviarse por correo real.
