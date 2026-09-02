package com.tuapp.bancopersonas.data.remote.dto

/**
 * Cuerpo de error de la API.
 *
 * [codigo] es lo que permite distinguir dos rechazos que comparten el mismo
 * 409 pero se tratan al revés: un conflicto de versión se resuelve
 * reintentando con la versión del servidor, y un documento repetido no se
 * resuelve reintentando nunca.
 */
data class ErrorApiDto(
    val error: String? = null,
    val codigo: String? = null
)
