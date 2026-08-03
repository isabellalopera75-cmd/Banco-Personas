# Walkthrough: Sistema de Autenticación por Roles

Se ha implementado un sistema de inicio de sesión que distingue entre administradores y usuarios registrados, permitiendo una experiencia personalizada para cada rol.

## Cambios Realizados

### 1. Backend (Node.js)
- **Controlador de Auth**: Creado `src/controllers/auth.controller.js` con lógica para validar credenciales fijas de admin (`admin`/`admin123`) y búsqueda en DB para usuarios.
- **Rutas**: Creado `src/routes/auth.routes.js` exponiendo el endpoint `/login`.
- **Servidor**: Integrado el nuevo sistema de rutas en `src/server.js`.

### 2. Capa de Datos (Android)
- **DTOs**: Creado `LoginDto.kt` para el intercambio de datos con el servidor.
- **API**: Definida la interfaz `AuthApi` para Retrofit.
- **Inyección de Dependencias**: Actualizado `NetworkModule.kt` para proveer `AuthApi`.
- **Mappers**: Extendida la funcionalidad de `PersonaMapper.kt` para convertir DTOs del servidor directamente a modelos de dominio.

### 3. Interfaz de Usuario y Navegación
- **Pantalla de Login**: Creada `LoginScreen.kt` con un selector de rol dinámico que cambia los campos visibles (Contraseña para Admin, Documento para Usuario).
- **Pantalla de Usuario**: Creada `UsuarioScreen.kt` que muestra una ficha técnica con los datos personales del usuario logueado.
- **Navegación**: Modificado `MainActivity.kt` para gestionar el flujo de la aplicación mediante estados, garantizando que siempre se inicie en el login.
- **Logout**: Añadida la funcionalidad de "Cerrar Sesión" tanto en la vista de administración como en la ficha de usuario.

## Cómo probar la funcionalidad

> [!TIP]
> **Para Admin:**
> - Selecciona "Admin".
> - Nombre: `admin`
> - Contraseña: `admin123`
> - Deberías ver la lista completa con el botón "+" y opciones de edición/borrado.

> [!IMPORTANT]
> **Para Usuario:**
> - Selecciona "Usuario".
> - Ingresa el **Nombre** y **Documento** de una persona que ya esté registrada en tu base de datos.
> - Deberías ver únicamente tu ficha personal con tus datos.

## Verificación de Seguridad Simple
- Intentar ingresar como Admin con contraseña incorrecta: Verás un mensaje de error.
- Intentar ingresar como Usuario con datos que no existen en Postgres: Verás un mensaje de error.
