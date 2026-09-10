const { Pool, types } = require('pg');
const config = require('../config');

// Postgres devuelve bigint (int8) como texto y no como número, para no perder
// precisión: un bigint llega hasta 9.2 trillones y un number de JavaScript es
// exacto solo hasta 9.007 billones.
//
// Acá esa cautela hace más daño que bien. `cambio_seq` es el cursor de
// sincronización y el cliente lo compara y lo guarda: recibirlo como "5" en
// lugar de 5 hace que una comparación con === falle en silencio, que es el
// tipo de error que aparece recién en producción.
//
// Se convierte a número porque el techo real está lejísimos: `cambio_seq`
// avanza una vez por cada alta y cada edición del padrón. Llegar al límite
// seguro de JavaScript pediría nueve mil millones de operaciones por cada
// persona de Colombia.
types.setTypeParser(types.builtins.INT8, (valor) => Number.parseInt(valor, 10));

const pool = new Pool(config.db);

module.exports = pool;
