# citas-web

## Estado actual

El repositorio contiene un frontend **Angular 21 + TypeScript** importado desde
`fcv-citas`. Incluye las vistas de inicio de sesión y los flujos de paciente,
profesional y administrador, junto con componentes de navegación, notificaciones
y modelos del dominio de citas.

El build de producción se validó con `npm run build` y genera los artefactos en
`citas-web/dist/app`.

## Ejecución local

1. En `citas-web`, ejecutar `npm install`.
2. Configurar la URL del backend en `.env.local` a partir de `.env.example`
   (`API_URL`).
3. Ejecutar `npm start`.

## Integración pendiente

Actualmente `FcvDataService` mantiene datos sintéticos en memoria para soportar
la interfaz. Debe sustituirse gradualmente por clientes HTTP que consuman la API
REST de `citas-api`, respetando los contratos acordados y configurando la URL
del backend por environment.

No usar Express ni BFF.
