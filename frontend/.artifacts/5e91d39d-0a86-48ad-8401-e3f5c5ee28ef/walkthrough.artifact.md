# Walkthrough: Sincronización Bidireccional y Offline-First

Se ha implementado un sistema de sincronización completo que permite a la aplicación funcionar de manera totalmente offline, protegiendo la integridad de los datos del usuario y asegurando que la información se mantenga actualizada con el servidor Postgres.

## Cambios Realizados

### 1. Sincronización Bidireccional (SyncWorker)
- Se añadió la fase de **PULL** al inicio de la sincronización. Ahora la app descarga automáticamente todos los registros del servidor y los guarda en Room.
- **Protección de datos:** Se implementó una regla que impide que la descarga del servidor sobrescriba registros locales que tengan cambios pendientes (`PENDING`) o conflictos (`CONFLICT`).

### 2. Integridad en el Inicio de Sesión
- Se modificó `LoginViewModel` para que, al iniciar sesión online, se verifique si ya existen datos locales con cambios sin subir. Si es así, la app **preserva los datos locales** en lugar de usar los del servidor, asegurando que tus ediciones offline no se pierdan al entrar de nuevo.

### 3. Flujo de Edición y Reversión
- Se ajustó el repositorio para que cualquier edición (incluyendo la reversión de historial) marque automáticamente el registro como `PENDING`. Esto garantiza que la UI muestre el estado de sincronización correcto y que el `SyncWorker` sepa que debe subir esos cambios.

## Verificación de Funcionalidades

> [!TIP]
> **Prueba Offline:**
> 1. Entra a la app y apaga el Wi-Fi.
> 2. Edita tu nombre o teléfono. Verás el icono de **reloj (pendiente)**.
> 3. Cierra sesión y vuelve a entrar (incluso sin internet).
> 4. ¡Tus datos editados siguen ahí!

> [!IMPORTANT]
> **Prueba de Sincronización:**
> 1. Con los datos editados offline, enciende el Wi-Fi.
> 2. Espera unos segundos o fuerza la sincronización.
> 3. El icono cambiará a una **nube (sincronizado)**, lo que indica que tus datos ya están seguros en Postgres.

## Notas Técnicas
- Se utiliza `OnConflictStrategy.REPLACE` en Room para asegurar que la descarga del servidor actualice los datos existentes sin duplicarlos.
- Se añadió registro de logs (`SYNC_DEBUG`) detallado para monitorear el proceso de descarga y protección de registros.
