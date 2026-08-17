# Tareas: Sincronización Total y Protección de Integridad

- [x] Implementar fase de **PULL** en `SyncWorker.kt` (Descarga de Postgres -> Room)
    - [x] Evitar sobrescritura de registros con estado `PENDING` o `CONFLICT`
- [x] Refinar `LoginViewModel.kt` para proteger datos locales durante el inicio de sesión
- [x] Asegurar que `revertirHistorial` en `PersonaRepositoryImpl.kt` genere cambios `PENDING`
- [x] Verificar persistencia offline tras logout y re-login
- [x] Probar sincronización bidireccional completa (Push + Pull)
