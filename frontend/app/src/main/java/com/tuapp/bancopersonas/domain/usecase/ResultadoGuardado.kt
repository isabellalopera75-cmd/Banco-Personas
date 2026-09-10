package com.tuapp.bancopersonas.domain.usecase

/**
 * Desenlace de un alta o una edición.
 *
 * Existe para que un rechazo llegue a la pantalla como un mensaje y no como
 * silencio. En el sistema anterior el choque por documento repetido se
 * descubría recién en la sincronización, en segundo plano: el servidor lo
 * rechazaba, la cola lo reintentaba en vano y terminaba descartando el cambio
 * sin que nadie se enterara. Desde la aplicación se veía como un dato que
 * simplemente no se guardaba.
 */
sealed interface ResultadoGuardado {

    data object Exito : ResultadoGuardado

    /** Ya hay una persona activa con ese tipo y número de documento. */
    data class DocumentoDuplicado(val nombreExistente: String) : ResultadoGuardado

    /** Falta algún campo obligatorio. */
    data class Incompleto(val campos: List<String>) : ResultadoGuardado
}
