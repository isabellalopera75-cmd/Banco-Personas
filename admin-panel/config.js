/*
 * Dirección de la API.
 *
 * Va en un archivo aparte y no dentro de app.js por un motivo práctico: el
 * contenedor sirve archivos estáticos tal como están, así que este archivo se
 * puede reemplazar en el despliegue para apuntar a otro servidor sin
 * recompilar ni reconstruir nada.
 *
 * Si el panel se abre desde localhost se asume que hay un backend local; en
 * cualquier otro caso, producción. Así probar no obliga a editar el archivo y
 * después acordarse de revertirlo, que es como una configuración de prueba
 * termina publicada.
 */
window.CONFIG = {
    API: /^(localhost|127\.0\.0\.1)$/.test(location.hostname)
        ? 'http://localhost:3000'
        : 'https://api.isita.online',
};
