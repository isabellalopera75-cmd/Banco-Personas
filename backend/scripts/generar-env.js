// Genera un archivo .env listo para producción, con secretos aleatorios.
//
// Uso:
//   node scripts/generar-env.js
//
// Escribe .env.produccion en la carpeta backend y muestra por pantalla la
// contraseña del administrador.
//
// A diferencia de la versión anterior, el archivo lleva ADMIN_PASSWORD en
// claro y no un hash. El motivo es que cambió quién la usa: antes el servidor
// comparaba contra el hash en cada login, ahora el admin es una fila de
// `usuarios` y esta variable solo alimenta a scripts/crear-admin.js, una vez.
//
// Por eso mismo esa línea se borra en cuanto el script corrió: es un insumo de
// arranque, no una credencial que el sistema necesite tener a mano.

const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

const SALIDA = path.join(__dirname, '..', '.env.produccion');

if (fs.existsSync(SALIDA)) {
    console.error(`Ya existe ${SALIDA}. Borrarlo o moverlo antes de regenerar.`);
    process.exit(1);
}

const passwordBase = crypto.randomBytes(12).toString('base64url');
const passwordAdmin = crypto.randomBytes(15).toString('base64url');

const contenido = `# Generado por scripts/generar-env.js
# No versionar este archivo.

DB_USER=bancopersonas
DB_PASSWORD=${passwordBase}
DB_NAME=bancopersonas

# docker-compose sobrescribe estas dos con el nombre del servicio.
# Se dejan por si la aplicación se corre fuera de contenedores.
DB_HOST=db
DB_PORT=5432

PORT=3000

JWT_SECRET=${crypto.randomBytes(48).toString('hex')}
JWT_EXPIRES_IN=7d

# Insumo de scripts/crear-admin.js. Se usa UNA vez, al desplegar por primera
# vez, y después estas dos líneas se borran de este archivo: el servidor no
# las lee para arrancar y no tienen por qué quedar guardadas.
ADMIN_USER=admin
ADMIN_PASSWORD=${passwordAdmin}
`;

fs.writeFileSync(SALIDA, contenido, { mode: 0o600 });

console.log(`Archivo generado: ${SALIDA}`);
console.log('');
console.log('  GUARDAR AHORA:');
console.log(`  usuario admin ....... admin`);
console.log(`  contraseña admin .... ${passwordAdmin}`);
console.log('');
console.log('Pasos en el servidor:');
console.log('  1. Renombrar este archivo a .env');
console.log('  2. Levantar los contenedores');
console.log('  3. npm run crear-admin');
console.log('  4. Borrar ADMIN_USER y ADMIN_PASSWORD del .env');
