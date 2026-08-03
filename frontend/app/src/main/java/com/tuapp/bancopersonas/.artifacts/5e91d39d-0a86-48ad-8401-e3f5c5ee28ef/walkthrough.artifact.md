# Walkthrough: Solución del Bug de Sincronización (409 Conflict)

Se ha corregido el problema donde los cambios locales "desaparecían" de la cola de sincronización tras un conflicto de versión con el servidor, sin llegar a impactar en la base de datos remota.

## Cambios Realizados

### 1. Conciencia de Versión en el Dominio
Se añadió el campo `version` al modelo `Persona` y se actualizaron los mappers y el ViewModel. Esto permite que la app sepa qué versión está editando y pueda enviarla correctamente al servidor.

- [Persona.kt](file:///C:/proyectos/bancopersonas/app/src/main/java/com/tuapp/bancopersonas/domain/model/Persona.kt): Añadido `version: Int`.
- [PersonaMapper.kt](file:///C:/proyectos/bancopersonas/app/src/main/java/com/tuapp/bancopersonas/data/mapper/PersonaMapper.kt): Mapeo de versión entre DB y Dominio.
- [PersonaFormViewModel.kt](file:///C:/proyectos/bancopersonas/app/src/main/java/com/tuapp/bancopersonas/presentation/form/PersonaFormViewModel.kt): Captura de la versión al iniciar la edición.

### 2. Resolución de Conflictos Automática (Auto-Correction)
Se modificó el `ConflictResolver` para que, en lugar de eliminar la operación fallida, obtenga la versión actual del servidor y actualice la petición en el `outbox`.

- [ConflictResolver.kt](file:///C:/proyectos/bancopersonas/app/src/main/java/com/tuapp/bancopersonas/data/sync/ConflictResolver.kt):
    - **Ya no elimina** el registro del outbox en caso de 409.
    - Actualiza el `payload` del outbox con la versión real del servidor.
    - Sincroniza la versión local con la del servidor para futuros intentos.

### 3. Mejora en la Trazabilidad del SyncWorker
Se refinaron los logs de `SyncWorker` para incluir el ID de la operación y de la persona, eliminando mensajes genéricos de éxito que inducían a error.

- [SyncWorker.kt](file:///C:/proyectos/bancopersonas/app/src/main/java/com/tuapp/bancopersonas/data/sync/SyncWorker.kt): Logs detallados por operación.

## Verificación

> [!TIP]
> Para probar esta solución:
> 1. Edita una persona en la app.
> 2. Antes de que el SyncWorker termine, incrementa manualmente la versión de esa misma persona en el servidor (o edítala desde otro dispositivo/Postman).
> 3. Observa los logs en Android Studio: verás el error 409, seguido del log `Payload actualizado con versión X`.
> 4. En la siguiente ejecución del `SyncWorker`, el cambio se aplicará correctamente al servidor usando la nueva versión base.

## Resultados
| Estado Anterior | Estado Actual |
| :--- | :--- |
| El Outbox se borraba tras un 409. | El Outbox persiste y se corrige automáticamente. |
| Logs decían "Sincronizado" tras fallar. | Logs indican claramente el conflicto y la delegación. |
| El cambio local se perdía para el servidor. | El cambio local se reintenta con la versión corregida. |
