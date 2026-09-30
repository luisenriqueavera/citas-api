# Contrato de registro y afiliación opcional

## Planes activos

`GET /api/insurance-plans` devuelve únicamente planes activos:

```json
[{"id":1,"epsName":"EPS Demo A","name":"Plan Demo 1"}]
```

## Registro

`POST /api/auth/register` acepta los campos de registro y el campo opcional `insurancePlanId`. El alias legado `planId` también se acepta mientras exista compatibilidad con clientes anteriores.

- Sin identificador de plan: crea `USER` sin fila en `user_insurance_affiliations`.
- Con plan activo: crea la fila de afiliación con FK `plan_id`.
- Plan inexistente o inactivo: responde `400` con `{"error":"invalid_plan"}` y no crea el usuario.

Los nombres de EPS y plan no se almacenan en `users`.
