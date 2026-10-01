# S5–S6 — Automatizaciones n8n (WF-001, WF-002, WF-003)

**Fecha:** 2026-10-01
**Estado:** los 3 workflows estan creados en la cuenta n8n conectada del usuario y validados con ejecucion controlada (happy path + rutas de error); el envio real de Gmail y las llamadas reales a la API quedan pendientes de credenciales propias del usuario.

## Backend (citas-api)

### Para WF-002 (ya documentado, resumen)
- `AppointmentWebhookNotifier.AppointmentStatusEvent` se amplio con `patientEmail`, `patientName`, `professionalName`, `specialtyName`, `scheduledStartAt`.
- `AppointmentService.notifyStatusChange(...)` resuelve esos campos con un join y delega en `AppointmentWebhookNotifier`; no-op si el webhook esta deshabilitado.
- **Gap cerrado:** `POST /api/v1/admin/appointments/{id}/decision` no disparaba el webhook; ahora si.
- `AppointmentWebhookNotifierTest` actualizado a la nueva aridad del record.

### Nuevos endpoints para WF-001 y WF-003
No existia ningun endpoint para consultar citas de forma agregada/multi-paciente (todos los existentes estan scoped a `/me` o `/professional`). Se agregaron dos endpoints de solo lectura en `AdminAppointmentController` (protegidos por `ROLE_ADMIN`, igual que el resto de `/api/v1/admin/**`):

- `GET /api/v1/admin/appointments/upcoming-reminders?withinHours=24` — citas `APPROVED` cuyo `scheduled_start_at` cae en la ventana `[ahora, ahora+withinHours]`, con email/nombre del paciente, nombre del profesional, especialidad y sede. Responde `{ withinHours, reminders: [...] }` (envuelto en una clave para que n8n pueda usar Split Out sin ambiguedad). Excluye CANCELLED/REJECTED por construccion (solo filtra `st.code = 'APPROVED'`).
- `GET /api/v1/admin/appointments/daily-summary?date=YYYY-MM-DD` (fecha opcional, por defecto hoy en America/Bogota) — tres listas agregadas (`byLocation`, `byStatus`, `bySpecialty`) con conteos de citas cuyo `scheduled_start_at` cae ese dia. La agregacion se hace en SQL (no en n8n).

**Limite de esta verificacion:** no hay Maven/Docker disponible en este entorno de ejecucion; ningun cambio de `citas-api` (ni el de WF-002 ni estos dos endpoints nuevos) se compilo ni se corrio con `mvn test` localmente. Antes de cerrar S5/S6, correr `docker-compose up` + `mvn test` para confirmar compilacion y que las pruebas existentes siguen en verde, y probar manualmente los dos endpoints nuevos contra MySQL con un usuario ADMIN real.

## n8n — credenciales compartidas entre los 3 workflows

- **Gmail FCV Citas** (`gmailOAuth2`): usada por WF-001, WF-002 y WF-003 para enviar correo. El usuario debe crear su propia credencial OAuth2 de Gmail en n8n (S5: "cada estudiante crea sus propias credenciales OAuth") y asignarla a los nodos Gmail antes de activar cualquiera de los tres.
- **Citas API Admin Token** (`httpTemplatedCustomAuth`): usada por WF-001 y WF-003 para llamar a los endpoints `/api/v1/admin/...`. n8n rechaza crear una credencial generica nueva de tipo `httpBearerAuth` directamente desde codigo (exige `httpTemplatedCustomAuth` para credenciales nuevas), asi que la credencial se configura con la plantilla `{"headers":{"Authorization":"Bearer {{token}}"}}` y un campo `token` = `accessToken` obtenido de `POST /api/auth/login` con un usuario ADMIN. **Limitacion conocida de laboratorio:** ese token expira segun `app.jwt.access-minutes`; para un workflow programado de larga duracion hay que renovarlo periodicamente a mano (o aumentar ese valor en el despliegue local del lab). No se implemento un paso de login/refresh automatico dentro del workflow para no introducir una dependencia de credenciales-en-body sin verificar en este entorno.

## WF-001 — Recordatorio de citas proximas (id `r3T9F9BlWrBKTtk5`)

Flujo: `Schedule (cada 6h)` → `Configuracion` (apiBaseUrl, windowHours) → `GET upcoming-reminders` (onError → log "API no disponible", reintenta en el siguiente ciclo) → `Split Out` sobre `reminders` → `Data Table rowNotExists` contra `appointment_reminders_sent` (dedup: omite citas ya recordadas) → mensaje HTML → Gmail (retry 3x) → si tuvo exito, `insert` en `appointment_reminders_sent`; si fallo, NoOp "reintenta en el proximo ciclo" (no se marca como enviada, por lo que el siguiente ciclo la reintenta sin tabla de reintentos adicional).

**Validado con ejecucion controlada:**
- Happy path con 2 citas simuladas (una ya "recordada" previamente insertada en la Data Table, otra nueva): `rowNotExists` filtro correctamente la ya recordada y dejo pasar solo la nueva; el mensaje HTML se compuso bien; Gmail fallo por falta de credencial real y cayo en la rama de reintento sin escribir en la tabla de dedup (confirmado).
- Rama "API no disponible": dejando el nodo HTTP sin pin (ejecucion real contra `http://localhost:8080`, inalcanzable desde n8n cloud) se confirmo el enrutamiento real al error output → NoOp correspondiente.

## WF-003 — Resumen operativo diario (id `YydAUNQok22Oia7F`, opcional/bonus)

Flujo: `Schedule (diario 07:00)` → `Configuracion` (apiBaseUrl, reportRecipient) → `GET daily-summary` → si tuvo exito, `Construir resumen` (HTML con listas por sede/estado/especialidad via `.map().join()` sobre los arreglos que ya vienen agregados del backend); si fallo, `Construir aviso de incidencia` (el correo se envia igual, notificando la falla, segun lo pedido en el spec) → ambas ramas convergen en `Mensaje resumen listo` → Gmail → `Data Table insert` en `daily_summary_log` (`SENT`/`FAILED`).

**Validado con ejecucion controlada:**
- Happy path con datos agregados simulados (2 sedes, 4 estados, 2 especialidades): el HTML se renderizo correctamente; Gmail fallo por falta de credencial real; el log quedo en `FAILED` con `reportDate` resuelto correctamente desde el dato propagado.
- Rama de incidencia: dejando el HTTP sin pin (fallo real de conexion/credencial) se confirmo que SI se construye y se intenta enviar el correo de incidencia (no se omite), y que el log tambien registra `FAILED` con el detalle real del error.

## Entregables

- `citas-api/automations/n8n/WF-001-appointment-reminders.json`
- `citas-api/automations/n8n/WF-002-status-notifications.json`
- `citas-api/automations/n8n/WF-003-daily-operational-summary.json`

Los 3 JSON estan libres de secretos/credenciales embebidos (solo referencias `newCredential(...)` por nombre). **Nota de portabilidad:** cada JSON referencia sus Data Tables (`appointment_reminders_sent`, `appointment_notifications_log`, `daily_summary_log`) por el `dataTableId` interno de esta instancia de n8n; al importar en otra instancia hay que recrear esas tablas (mismas columnas, documentadas en cada seccion) y reapuntar los nodos "Registrar/Omitir ...", igual que con las credenciales de Gmail y de la API.

## Pendiente para el cierre de S5–S6

- Compilar/verificar el backend completo (`mvn test` via Docker): cambios de WF-002 (payload enriquecido, gap de webhook) y los 2 endpoints nuevos de WF-001/WF-003 no se compilaron en este entorno.
- Autorizar la credencial Gmail OAuth2 propia y la credencial "Citas API Admin Token" (con un accessToken ADMIN real), y repetir las mismas pruebas controladas para confirmar un envio/consulta real exitosos.
- Activar cada workflow solo despues de validar la salida real esperada (regla explicita de la guia).
- Documentar riesgos residuales de seguridad (bloque S5: contenido no confiable / respuesta MCP) si aun no se hizo en otra pagina de la wiki.
