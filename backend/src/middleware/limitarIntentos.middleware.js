/**
 * Limitador de intentos por IP, en memoria.
 *
 * Existe por una razón concreta: `POST /api/auth/login-persona` no pide
 * contraseña. La credencial es el tipo y número de documento, que no es un
 * secreto. Sin un límite, recorrer números de cédula hasta encontrar registros
 * válidos es cuestión de minutos.
 *
 * No reemplaza a una contraseña — nada lo hace — pero convierte un barrido
 * masivo en algo lento y ruidoso.
 *
 * Es en memoria a propósito: una sola instancia de API, sin dependencias
 * nuevas. Si algún día hay varias réplicas detrás de un balanceador, cada una
 * llevará su propia cuenta y habrá que mover esto a Redis. Está anotado acá
 * para que esa limitación se descubra leyendo y no en producción.
 */

const crearLimitador = ({ maximo = 10, ventanaMs = 15 * 60 * 1000, reloj = Date.now } = {}) => {
    const intentos = new Map();
    let ultimaLimpieza = reloj();

    // Sin esto el Map crece con cada IP que haya tocado el servidor alguna vez.
    // Una fuga de memoria lenta es más difícil de encontrar que una rápida.
    const limpiarVencidos = (ahora) => {
        if (ahora - ultimaLimpieza < ventanaMs) return;

        for (const [clave, registro] of intentos) {
            if (ahora - registro.desde >= ventanaMs) {
                intentos.delete(clave);
            }
        }
        ultimaLimpieza = ahora;
    };

    const limitador = (req, res, next) => {
        const ahora = reloj();
        limpiarVencidos(ahora);

        const clave = req.ip || 'desconocida';
        const registro = intentos.get(clave);

        if (!registro || ahora - registro.desde >= ventanaMs) {
            intentos.set(clave, { desde: ahora, cantidad: 1 });
            return next();
        }

        registro.cantidad += 1;

        if (registro.cantidad > maximo) {
            const esperaSegundos = Math.ceil((registro.desde + ventanaMs - ahora) / 1000);
            res.set('Retry-After', String(esperaSegundos));
            return res.status(429).json({
                error: 'Demasiados intentos. Esperar unos minutos antes de volver a probar.',
            });
        }

        return next();
    };

    // Solo para los tests: permite comprobar que la limpieza ocurre.
    limitador.tamano = () => intentos.size;

    return limitador;
};

const QUINCE_MINUTOS_MS = 15 * 60 * 1000;

/**
 * Dos limitadores separados, y no uno compartido, por dos motivos.
 *
 * El primero es que cada instancia lleva su propio Map: con uno solo, los
 * intentos fallidos de un registrador consumirían el cupo del login de
 * personas, y una cosa no tiene nada que ver con la otra.
 *
 * El segundo es que los riesgos son distintos. El login de operador pide
 * contraseña, así que adivinarlo por fuerza bruta es caro de por sí; el límite
 * está para frenar un ataque, no para castigar a un registrador que tipeó mal
 * dos veces con el sol de frente. El login de persona no pide contraseña: ahí
 * el límite ES la seguridad, y por eso es más ajustado.
 */
const limitarLoginOperador = crearLimitador({ maximo: 10, ventanaMs: QUINCE_MINUTOS_MS });
const limitarLoginPersona = crearLimitador({ maximo: 5, ventanaMs: QUINCE_MINUTOS_MS });

module.exports = {
    crearLimitador,
    limitarLoginOperador,
    limitarLoginPersona,
};
