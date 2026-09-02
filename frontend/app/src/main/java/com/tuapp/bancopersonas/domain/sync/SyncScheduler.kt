package com.tuapp.bancopersonas.domain.sync

/**
 * Puerto de agendado de la sincronización.
 *
 * Vive en el dominio y no en la capa de datos a propósito: los casos de uso
 * necesitan declarar "esto hay que subirlo", pero no tienen por qué saber que
 * del otro lado hay un WorkManager. La implementación es
 * `data.sync.WorkManagerSyncScheduler`.
 */
interface SyncScheduler {

    /**
     * Deja programada la sincronización periódica de respaldo. Se llama una
     * sola vez, al arrancar la aplicación.
     *
     * Es la red de seguridad para lo que quedó pendiente cuando el teléfono
     * estaba sin señal, no el camino normal de una operación recién hecha.
     */
    fun programarSincronizacionPeriodica()

    /**
     * Pide una subida inmediata de la cola, sin esperar la ventana periódica.
     *
     * Es lo que hace que un alta hecha en el teléfono llegue al servidor en
     * el momento. Sin esto, quien se registra vuelve al login y el servidor
     * todavía no conoce su documento: el intento termina en un 401 que no
     * tiene forma de entender, y que además no dispara la entrada sin
     * conexión, porque el servidor sí respondió.
     */
    fun sincronizarAhora()
}
