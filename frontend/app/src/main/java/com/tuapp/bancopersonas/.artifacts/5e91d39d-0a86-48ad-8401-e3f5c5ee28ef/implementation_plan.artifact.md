# Plan de Mejora UI/UX - Pantalla de Administración

Este plan propone modernizar la interfaz del administrador (`PersonaListScreen`) utilizando componentes de **Material 3**, mejorando la jerarquía visual y proporcionando feedback sobre el estado de sincronización.

## User Review Required

> [!TIP]
> **Propuesta Visual:**
> - Cambiar la lista plana por **Tarjetas (ElevatedCard)** con sombras y bordes redondeados.
> - Sustituir el botón de texto "Eliminar" por **iconos de acción** claros (Editar/Borrar).
> - Implementar indicadores visuales para el **Estado de Sincronización** (Nube, Pendiente, Error).

## Propuesta de Cambios

### Interfaz de Usuario (Android)

#### [MODIFY] [PersonaListScreen.kt](file:///C:/proyectos/BancoPersona/frontend/app/src/main/java/com/tuapp/bancopersonas/presentation/list/PersonaListScreen.kt)
- **Rediseño de Items**: Usar `ElevatedCard` en lugar de una `Row` simple.
- **Jerarquía de Texto**: El nombre resaltado en negrita, el documento en un estilo secundario.
- **Iconografía**:
    - `Icons.Default.Edit` para editar.
    - `Icons.Default.Delete` con color de error (`errorContainer`) para eliminar.
- **Feedback de Sincronización**:
    - **Sincronizado**: Icono de check verde o nube gris suave.
    - **Pendiente**: Icono de reloj o flechas circulares (Sync).
    - **Conflicto**: Icono de advertencia en rojo.
- **Empty State**: Un diseño más atractivo con un icono central cuando no hay datos.
- **TopAppBar**: Mejorar el estilo y añadir un subtítulo que indique el total de personas.

#### [MODIFY] [PersonaFormScreen.kt](file:///C:/proyectos/BancoPersona/frontend/app/src/main/java/com/tuapp/bancopersonas/presentation/form/PersonaFormScreen.kt)
- **Modal Bottom Sheet (Opcional)**: En lugar de reemplazar toda la pantalla, podríamos usar un `ModalBottomSheet` para una experiencia más fluida (se evaluará si el tiempo lo permite, por ahora se mantendrá la lógica de pantalla pero con mejor diseño).
- **Validaciones Visuales**: Resaltar errores en los campos de texto.

## Navegación y UX
1. **Confirmación de Borrado**: Añadir un diálogo de confirmación antes de eliminar una persona.
2. **Animaciones**: Usar `AnimatedVisibility` o transiciones suaves al añadir/quitar elementos de la lista.

## Verificación Plan
- [ ] Visualizar los nuevos estilos de tarjetas.
- [ ] Confirmar que los iconos de sincronización cambian correctamente según el estado.
- [ ] Probar el diálogo de confirmación al borrar.
- [ ] Verificar que el formulario se vea integrado con el nuevo diseño.
