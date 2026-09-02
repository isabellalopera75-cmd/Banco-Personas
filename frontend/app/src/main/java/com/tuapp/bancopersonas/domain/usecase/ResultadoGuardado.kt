package com.tuapp.bancopersonas.domain.usecase

/**
 * Desenlace de un alta o una edición.
 *
 * Existe para que el rechazo por documento repetido llegue a la pantalla como
 * un mensaje y no como silencio. Antes, ese choque se descubría recién en la
 * sincronización, en segundo plano: el servidor lo rechazaba, la cola lo
 * reintentaba en vano y terminaba descartando el cambio sin que nadie se
 * enterara. Desde la aplicación se veía como un dato que simplemente no se
 * actualizaba.
 */
sealed interface ResultadoGuardado {

    data object Exito : ResultadoGuardado

    /** Otro participante activo ya tiene ese documento. */
    data object DocumentoDuplicado : ResultadoGuardado
}
