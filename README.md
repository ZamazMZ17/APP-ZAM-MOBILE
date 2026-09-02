# ZAM Link

Control móvil nativo para Ekars/Zam. La primera versión permite enlazar un servidor FastAPI, enviar órdenes y descargar futuras actualizaciones desde la propia app.

## Decisiones permanentes

- **ID de Android:** `com.zamazmz17.zammobile`. No debe cambiarse tras la primera instalación publicada.
- **Firma:** todo APK que actualice una instalación previa debe usar el mismo archivo `.jks` y el mismo alias. El workflow usa secretos de GitHub para no exponer esa llave.
- **Actualización interna:** la app busca APKs en la última *GitHub Release*, no en los artefactos de Actions. Los artefactos requieren autenticación; las Releases públicas permiten descargar dentro de la app.

## Contrato inicial con Ekars

- `GET /health` responde HTTP 200 cuando el servidor está disponible.
- `POST /api/v1/commands` recibe `{ "command": "..." }` y devuelve `{ "message": "..." }`.

La versión productiva añadirá emparejamiento QR, clave por dispositivo, aprobaciones de acciones sensibles, voz, captura y streaming de estado.

## Publicar una actualización instalable

1. Crea una sola vez la llave de firma y guárdala fuera del repositorio.
2. Configura los secretos `ZAM_KEYSTORE_B64`, `ZAM_STORE_PASSWORD`, `ZAM_KEY_ALIAS` y `ZAM_KEY_PASSWORD`.
3. Sube un tag como `v0.1.0`. Actions creará una Release; ZAM Link descargará ese APK desde su botón de actualización.
