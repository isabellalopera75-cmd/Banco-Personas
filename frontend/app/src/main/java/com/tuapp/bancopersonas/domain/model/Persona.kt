package com.tuapp.bancopersonas.domain.model

/**
 * Una persona del padrón.
 *
 * Ya no tiene contraseña: en el modelo anterior cada persona era además una
 * cuenta del sistema, y esa mezcla hacía imposible que un registrador diera
 * de alta a un tercero. Las credenciales viven ahora solo en las cuentas de
 * admin y registrador.
 */
data class Persona(
    val id: String,
    val tipoDocumento: TipoDocumento,
    val numeroDocumento: String,
    val primerNombre: String,
    val segundoNombre: String? = null,
    val primerApellido: String,
    val segundoApellido: String? = null,
    val fechaNacimiento: String,
    val sexo: Sexo,
    val telefono: String? = null,

    /** Id de la cuenta que la registró. Lo necesita el admin y define permisos. */
    val creadoPor: String? = null,

    /** Última versión conocida del servidor. NO se incrementa localmente. */
    val version: Int = 1,

    val syncStatus: SyncStatus = SyncStatus.PENDING,

    /** Presente solo si el registro quedó esperando decisión del admin. */
    val conflictoId: String? = null,
) {
    val nombreCompleto: String
        get() = listOfNotNull(
            primerNombre,
            segundoNombre?.takeIf { it.isNotBlank() },
            primerApellido,
            segundoApellido?.takeIf { it.isNotBlank() },
        ).joinToString(" ")

    val documentoCompleto: String
        get() = "${tipoDocumento.name} $numeroDocumento"
}

/**
 * Tipos aceptados por la base. La lista es la misma que el CHECK de la tabla:
 * si dejaran de coincidir, el alta fallaría recién al sincronizar, con el
 * registrador ya lejos de la persona.
 */
enum class TipoDocumento(val etiqueta: String) {
    CC("Cédula de ciudadanía"),
    TI("Tarjeta de identidad"),
    RC("Registro civil"),
    CE("Cédula de extranjería"),
    PPT("Permiso por protección temporal"),
    PEP("Permiso especial de permanencia");

    companion object {
        fun desde(valor: String?): TipoDocumento? =
            entries.firstOrNull { it.name.equals(valor?.trim(), ignoreCase = true) }
    }
}

enum class Sexo(val etiqueta: String) {
    M("Masculino"),
    F("Femenino"),
    I("Intersexual");

    companion object {
        fun desde(valor: String?): Sexo? =
            entries.firstOrNull { it.name.equals(valor?.trim(), ignoreCase = true) }
    }
}

enum class SyncStatus {
    /** Igual a lo que tiene el servidor. */
    SYNCED,

    /** Hay un cambio local esperando en la cola de salida. */
    PENDING,

    /**
     * El servidor lo recibió pero no lo pudo aplicar, y lo guardó como
     * conflicto. Reemplaza al viejo CONFLICT, que significaba lo contrario:
     * antes el dato se marcaba y se borraba de la cola sin haber salido nunca
     * del teléfono. Ahora está a salvo del otro lado y espera al admin.
     */
    EN_REVISION,
}
