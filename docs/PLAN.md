# Plan maestro — ZAM Link

## Propósito

ZAM Link es el control móvil de Ekars: no duplica el asistente, sino que permite hablarle, vigilar su estado y aprobar acciones de la laptop desde Android. La app es intencionalmente oscura, limpia y técnica; Ekars sigue siendo la entidad principal, no una pantalla corporativa llena de menús.

## Diseño visual

- Fondo `#080B10`, superficies `#111720` y acento verde `#35D49A`.
- Cabecera con núcleo circular **Z** y estado visible: desconectado, conectado, escuchando, ejecutando o esperando confirmación.
- La pantalla de inicio es una conversación operativa: dirección del nodo Ekars, orden, accesos de acción y respuesta.
- Más adelante habrá cuatro pestañas: **Control**, **Pantalla**, **Actividad** y **Ajustes**. No se mostrarán hasta que aporten una función real.

## Entregas

### 0.1 — Enlace funcional

- Conectar con `GET /health`.
- Enviar texto mediante `POST /api/v1/commands`.
- Accesos: pantalla, estado y escucha.
- APK de depuración en cada push y botón de actualización desde GitHub Releases.

### 0.2 — Emparejamiento seguro

- QR creado por Ekars y escaneado desde el teléfono.
- Token único por dispositivo guardado en almacenamiento cifrado de Android.
- Renovación y revocación desde la laptop.
- La app no guardará contraseñas ni expondrá la API local.

### 0.3 — Voz, respuesta y tareas

- Micrófono con pulsar-para-hablar; STT en el móvil o envío de audio a Ekars según latencia.
- Respuesta en tarjetas y TTS opcional.
- Tareas con estado: recibida, necesita autorización, ejecutando, finalizada o fallida.

### 0.4 — Vista remota

- Captura bajo demanda, nunca streaming permanente por defecto.
- Galería temporal protegida y botón para pedir análisis visual a Ekars.
- Controles de sesión: bloquear, cerrar y revocar teléfono.

### 1.0 — Acceso exterior

- Túnel HTTPS autenticado, lista de dispositivos y caducidad de sesiones.
- Confirmación en laptop para acciones irreversibles: borrar, instalar, enviar o pagar.
- Auditoría local: quién pidió qué, cuándo y qué ejecutó Ekars.

## Contrato de API

Primera versión:

```text
GET  /health
POST /api/v1/commands   { command: string }
                         -> { message: string, taskId?: string }
```

El contrato de la siguiente versión agregará `Authorization: Bearer <device-token>` y endpoints para pairing, tareas, capturas y revocación. La app debe negarse a usar HTTP: únicamente aceptará HTTPS cuando Ekars salga de la red local.

## Firma y actualizaciones

Android solo actualiza una app si se conservan **a la vez** el mismo `applicationId` y la misma llave de firma. Por eso `com.zamazmz17.zammobile` queda congelado desde hoy y la llave `zam-release.jks` se generará una única vez. Nunca va al repositorio: se conserva de forma privada y sus valores se cargan como secretos de GitHub.

Actions genera un APK de prueba en cada push. Para una actualización que instale sobre la versión previa se publica un tag `vX.Y.Z`; ese flujo genera un APK firmado, crea una Release pública y ZAM Link la descarga desde adentro. Android aún pedirá su confirmación final de instalación, porque ninguna app puede actualizarse silenciosamente sin privilegios especiales del sistema.
