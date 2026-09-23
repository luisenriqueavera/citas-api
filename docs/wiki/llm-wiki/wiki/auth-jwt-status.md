# Registro y sesión JWT — estado de implementación

**Fecha:** 2026-09-22  
**Alcance:** RF-01 (registro de USER) y RF-02 (login y sesión JWT) del PRD.  
**Estado:** implementación pendiente de validación automatizada.

## Contexto de especificación

No hay una historia de usuario, criterios de aceptación ni Definition of Done generados en `docs/wiki/scrum/`; sus directorios sólo contienen `.gitkeep`. Se emplearon exclusivamente los requisitos RF-01 y RF-02 del PRD como fuente de alcance. No se implementó UI ni recuperación de contraseña.

## Diseño implementado

- `POST /api/auth/register` crea únicamente cuentas con rol `USER`.
  - Requiere nombres, apellidos, tipo y número de documento, email, teléfono y contraseña.
  - El email y la pareja `document_type`/`document_number` son únicos tanto en servicio como en esquema.
  - La contraseña se guarda usando BCrypt; nunca se devuelve en la respuesta.
- `POST /api/auth/login` valida email y contraseña, y entrega `accessToken`, `refreshToken` y `tokenType` (`Bearer`).
- `POST /api/auth/refresh` usa refresh JWT rotativo.
  - El refresh es un JWT firmado distinto del access JWT y su hash SHA-256, no el token en claro, se guarda en `refresh_tokens`.
  - Un refresh válido se revoca al canjearse y produce un access token y refresh token nuevos; reutilizarlo devuelve `401`.
- La migración `V1__auth_schema.sql` crea `users`, `roles`, `user_roles` y `refresh_tokens`, e inicializa el rol `USER`.

## Casos cubiertos por pruebas de integración

`AuthIntegrationTest` define cobertura para:

1. registro correcto con rol `USER` y hash BCrypt verificable;
2. rechazo de email y documento duplicados (`409`);
3. login correcto con ambos tokens;
4. credenciales inválidas (`401`);
5. refresh válido rotativo, refresh reutilizado e inválido (`401`).

## Bloqueo de validación

La prueba obligatoria `mvn test` **no se ha podido ejecutar** porque Maven no está instalado/disponible en el entorno. Se hicieron tres intentos sin resultado:

1. `mvn test`: PowerShell no reconoce `mvn`.
2. Instalación mediante `winget`: el origen configurado no encontró el paquete Apache Maven.
3. Descarga temporal del archivo oficial de Maven: no quedó disponible `mvn.cmd`; el ejecutable temporal no fue encontrado.

No se debe declarar este incremento completado ni actualizar el estado de una HU hasta ejecutar `mvn test` con Maven operativo y registrar el resultado.
