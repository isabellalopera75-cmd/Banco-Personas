# Plan de Implementación: Sincronización Bidireccional Robusta (Offline-First)

Este plan detalla la implementación de una sincronización completa que respeta la integridad de los datos, el historial de cambios y el funcionamiento offline, asegurando que los usuarios siempre vean su información más reciente, incluso tras cerrar sesión sin internet.

## User Review Required

> [!IMPORTANT]
> **Integridad Offline:**
> - Si editas datos sin internet, estos se guardan en Room con estado `PENDING`.
> - Al cerrar sesión y volver a entrar (incluso offline), el sistema priorizará tus datos locales de Room.
> - La sincronización descargará datos del servidor pero **respetará tus cambios locales pendientes**, evitando que el servidor los sobrescriba antes de sincronizarse.

## Propuesta de Cambios

### Capa de Datos (Android)

#### [MODIFY] [SyncWorker.kt](file:///C:/proyectos/BancoPersona/frontend/app/src/main/java/com/tuapp/bancopersonas/data/sync/SyncWorker.kt)
Implementar la fase de **PULL** al inicio de `doWork()`:
1. Descargar todos los registros del servidor (`api.obtenerCambios`).
2. Para cada registro:
   - Si no existe localmente, guardarlo.
   - Si existe localmente con estado `SYNCED`, actualizarlo (el servidor manda).
   - Si existe localmente con estado `PENDING` o `CONFLICT`, **no sobrescribir**. Esperar a que la fase de **PUSH** resuelva la sincronización.

#### [MODIFY] [PersonaRepositoryImpl.kt](file:///C:/proyectos/BancoPersona/frontend/app/src/main/java/com/tuapp/bancopersonas/data/repository/PersonaRepositoryImpl.kt)
- Refinar `sincronizarHistorial` para que sea más resiliente.
- Asegurar que `revertirHistorial` use la lógica de edición estándar para que el cambio se encole en el outbox y se marque como `PENDING`.

### Capa de Usuario (Android)

#### [MODIFY] [LoginViewModel.kt](file:///C:/proyectos/BancoPersona/frontend/app/src/main/java/com/tuapp/bancopersonas/presentation/login/LoginViewModel.kt)
- Al hacer login online exitoso, **solo guardar en Room si no hay cambios locales pendientes** para ese usuario. Esto protege la integridad de ediciones hechas offline antes del login.

## Verificación Plan
- [ ] **Persistencia Offline tras Logout:**
  1. Editar datos sin internet (Estado: `PENDING`).
  2. Cerrar sesión.
  3. Loguear offline y verificar que los cambios editados persisten.
- [ ] **Sincronización Bidireccional:**
  1. Crear registros nuevos en el servidor (via Postman o consola).
  2. Ejecutar Sync en la app y verificar que los registros se descarguen.
- [ ] **Protección de Ediciones Pendientes:**
  1. Editar un registro localmente (offline).
  2. Modificar el mismo registro en el servidor.
  3. Sincronizar y verificar que el `SyncWorker` detecte el conflicto y lo maneje via `ConflictResolver` en lugar de borrar el cambio local.
