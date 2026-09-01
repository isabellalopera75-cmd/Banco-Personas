// Genera un archivo .env listo para producción, con secretos aleatorios.
//
// Uso:
//   node scripts/generar-env.js
//
// Escribe .env.produccion en la carpeta backend y muestra por pantalla la
// contraseña del administrador. Esa contraseña se imprime UNA sola vez: en el
// archivo queda solo su hash, y de un hash bcrypt no se recupera el original.

const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const bcrypt = require('bcryptjs');

const SALIDA = path.join(__dirname, '..', '.env.produccion');

if (fs.existsSync(SALIDA)) {
    console.error(`Ya existe ${SALIDA}. Borrarlo o moverlo antes de regenerar.`);
    process.exit(1);
}

const passwordBase = crypto.randomBytes(12).toString('base64url');
const passwordAdmin = crypto.randomBytes(9).toString('base64url');

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

ADMIN_USER=admin
ADMIN_PASSWORD_HASH='${bcrypt.hashSync(passwordAdmin, 12)}'
`;

fs.writeFileSync(SALIDA, contenido, { mode: 0o600 });

console.log(`Archivo generado: ${SALIDA}`);
console.log('');
console.log('  GUARDAR AHORA (no se vuelve a mostrar):');
console.log(`  usuario admin ....... admin`);
console.log(`  contraseña admin .... ${passwordAdmin}`);
console.log('');
console.log('Renombrar a .env en el servidor antes de levantar los contenedores.');
