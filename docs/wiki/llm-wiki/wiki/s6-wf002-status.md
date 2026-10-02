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

## Actualizacion 2026-10-01 (sesion posterior) — mitigacion de riesgos S5

A partir de la auditoria de `s5-untrusted-content-and-residual-risks.md`, se aplicaron a nivel de codigo (sin tocar la instancia real de n8n ni activar workflows) las 3 mitigaciones siguientes:

- **Riesgo #1 (webhook sin auth):** `AppointmentWebhookNotifier` ahora envia el header `X-Webhook-Secret` cuando `APPOINTMENT_WEBHOOK_SECRET` esta configurado (`application.yml` + `.env.example`); el nodo Webhook de WF-002 exige `authentication: headerAuth`. Cubierto por 2 casos nuevos en `AppointmentWebhookNotifierTest` (header presente/ausente) usando un servidor HTTP JDK embebido, sin dependencias nuevas.
- **Riesgo #2 (HTML sin escapar):** las 8 expresiones `htmlBody` de WF-001/002/003 ahora escapan `& < > " '` en los campos de texto libre (`patientName`, `professionalName`, `specialtyName`, `reason`, `locationName`, `status`, `error`) antes de concatenarlos.
- **Riesgo #3 (token ADMIN completo):** nuevo rol de catalogo `AUTOMATION` (`V7__automation_role.sql`), nuevo endpoint `POST /api/v1/admin/automation-accounts` (solo ADMIN) para crear la cuenta de servicio, y `SecurityConfiguration` ahora permite `GET /api/v1/admin/appointments/upcoming-reminders` y `/daily-summary` a `ADMIN` o `AUTOMATION`, mientras el resto de `/api/v1/admin/**` sigue exigiendo `ADMIN`. Cubierto por `AutomationRoleIntegrationTest` (creacion de cuenta, bloqueo a no-admin, acceso de AUTOMATION solo a los 2 endpoints de lectura).

**Limite de esta actualizacion:** Docker Desktop no arrancaba en el entorno de ejecucion de esta sesion ("Docker Desktop is unable to start"), y no hay Java/Maven/Node instalados directamente en el host, asi que **nada de esto se compilo ni se corrio con `mvn test`**. La revision fue manual: lectura cruzada de las clases modificadas/nuevas contra los patrones ya usados en el proyecto (firma de constructor, imports, convenciones de `AdminCatalogController`/`SecurityConfiguration`), y validacion de que los 3 JSON de n8n siguen siendo JSON bien formado. Esto reduce pero no elimina el riesgo de un error de compilacion no detectado.

Apiweb (`citas-web`): se corrigio ademas un gap independiente marcado como pendiente mas abajo en este documento ("Migrar las llamadas HTTP ya existentes...") — los 13 metodos de S2/S3 en `fcv-data.service.ts` que usaban rutas relativas ahora usan `apiBaseUrl` como el resto.

## Actualizacion 2026-10-01 (sesion posterior, parte 2) — credenciales reales y prueba en vivo de WF-002

El usuario configuro en su instancia real de n8n las 3 credenciales pendientes:
- **Webhook Secret Compartido** (Header Auth, header `X-Webhook-Secret`) en el nodo Webhook de WF-002.
- **Gmail FCV Citas** (Gmail OAuth2, scope minimo `gmail.compose` — solo enviar/gestionar borradores, sin lectura/borrado de la bandeja) en los 3 workflows.
- **Citas API Admin Token** (Simplified Custom Auth; el campo auto-generado para `{{token}}` no acepto guardar sin valor, asi que el token quedo escrito literal dentro del Auth template en vez de via placeholder — limitacion de la UI de n8n, no del diseno) en WF-001 y WF-003, usando el token de la cuenta `AUTOMATION` real creada via `POST /api/v1/admin/automation-accounts`.

Con eso se ejecuto una prueba real de extremo a extremo contra WF-002: `curl` con header `X-Webhook-Secret` correcto y payload incluyendo `patientName: "María O'Brien <script>alert(1)</script>"` (caso de prueba deliberado para el riesgo #2). Resultado:
- El webhook acepto la peticion (antes, sin el header, un 404/401 lo habria rechazado).
- La ejecucion completa tomo ~33s (reintentos normales de Gmail) y termino en `Success`.
- Llego el correo real a la bandeja Gmail del usuario con asunto "Cita especializada aprobada - FCV", y el cuerpo mostro el nombre con las etiquetas `<script>` como **texto plano literal**, confirmando que el escape de HTML funciona en produccion, no solo en el JSON versionado.

**Limite de WF-001/WF-003:** sus nodos HTTP Request llaman desde n8n Cloud hacia `http://localhost:8080` (el backend del usuario, corriendo en su maquina via Docker). n8n Cloud no puede resolver el `localhost` del usuario, asi que una prueba real de extremo a extremo de esos dos workflows requiere exponer el backend local a internet (p. ej. `ngrok`) o usar datos simulados ("pin data") sobre el nodo HTTP Request, pendiente de decision del usuario sobre cual camino tomar.

Ninguno de los 3 workflows esta activado todavia (`active: false` en los 3).

## Pendiente para el cierre de S5–S6

- **Compilar/verificar el backend completo (`mvn test` via Docker)**: ademas de los cambios de WF-002/endpoints de la ronda anterior, ahora incluye el header de secreto del webhook y el rol/endpoint AUTOMATION — nada de esto se ha compilado todavia (ver limite arriba). Hacerlo en cuanto Docker este disponible, antes de dar S5/S6 por cerrado.
- En n8n (accion del usuario, no de este agente): crear la credencial Header Auth "Webhook Secret Compartido" para WF-002 con el mismo valor que `APPOINTMENT_WEBHOOK_SECRET"; crear la cuenta AUTOMATION real via el nuevo endpoint, loguearse con ella y usar ese `accessToken` (no uno de ADMIN) en la credencial "Citas API Admin Token" de WF-001/WF-003.
- Autorizar la credencial Gmail OAuth2 propia, y repetir las pruebas controladas de WF-001/002/003 para confirmar un envio/consulta real exitosos con las credenciales ya endurecidas.
- Activar cada workflow solo despues de validar la salida real esperada (regla explicita de la guia).
- Riesgos #4 (expiracion silenciosa del token) y #5 (retencion de datos en Data Tables) de `s5-untrusted-content-and-residual-risks.md` siguen sin mitigar; quedan como decision pendiente del usuario sobre prioridad/esfuerzo.
