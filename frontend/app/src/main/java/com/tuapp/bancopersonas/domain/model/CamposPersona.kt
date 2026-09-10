package com.tuapp.bancopersonas.domain.model

import com.tuapp.bancopersonas.data.local.entity.PersonaEntity

/**
 * Nombres de campo tal como los espera el servidor, y el cálculo de qué
 * cambió de verdad.
 *
 * Los nombres están acá y no sueltos en el repositorio porque un typo en una
 * clave no rompe la compilación: el servidor simplemente ignora ese campo y lo
 * devuelve en `rechazados`. El cambio parecería guardarse y no se guardaría.
 */
object CamposPersona {
    const val TIPO_DOCUMENTO = "tipo_documento"
    const val NUMERO_DOCUMENTO = "numero_documento"
    const val PRIMER_NOMBRE = "primer_nombre"
    const val SEGUNDO_NOMBRE = "segundo_nombre"
    const val PRIMER_APELLIDO = "primer_apellido"
    const val SEGUNDO_APELLIDO = "segundo_apellido"
    const val FECHA_NACIMIENTO = "fecha_nacimiento"
    const val SEXO = "sexo"
    const val TELEFONO = "telefono"

    /** Etiquetas para mostrarle al registrador qué campo entró en conflicto. */
    private val ETIQUETAS = mapOf(
        TIPO_DOCUMENTO to "Tipo de documento",
        NUMERO_DOCUMENTO to "Número de documento",
        PRIMER_NOMBRE to "Primer nombre",
        SEGUNDO_NOMBRE to "Segundo nombre",
        PRIMER_APELLIDO to "Primer apellido",
        SEGUNDO_APELLIDO to "Segundo apellido",
        FECHA_NACIMIENTO to "Fecha de nacimiento",
        SEXO to "Sexo",
        TELEFONO to "Teléfono",
    )

    fun etiqueta(campo: String): String = ETIQUETAS[campo] ?: campo

    /**
     * Vacío y nulo son el mismo valor.
     *
     * Un campo opcional que el formulario deja en blanco significa "sin dato",
     * no "el texto vacío". Sin unificarlos, borrar un teléfono viajaría como
     * un cambio de null a "" y después de "" a null: ruido permanente en la
     * auditoría y conflictos donde nadie modificó nada.
     */
    private fun normalizar(valor: String?): String? = valor?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * Qué campos cambian de verdad entre lo guardado y lo que trae el
     * formulario.
     *
     * El formulario devuelve el registro entero aunque se haya tocado un solo
     * campo. Mandar todo convertiría cualquier edición simultánea en un
     * choque, cuando el servidor sabe convivir con cambios sobre campos
     * distintos. Mandar solo lo modificado es lo que habilita el merge.
     */
    fun diferencias(antes: PersonaEntity, despues: Persona): Map<String, String?> {
        val candidatos = mapOf(
            TIPO_DOCUMENTO to (antes.tipoDocumento to despues.tipoDocumento.name),
            NUMERO_DOCUMENTO to (antes.numeroDocumento to despues.numeroDocumento),
            PRIMER_NOMBRE to (antes.primerNombre to despues.primerNombre),
            SEGUNDO_NOMBRE to (antes.segundoNombre to despues.segundoNombre),
            PRIMER_APELLIDO to (antes.primerApellido to despues.primerApellido),
            SEGUNDO_APELLIDO to (antes.segundoApellido to despues.segundoApellido),
            FECHA_NACIMIENTO to (antes.fechaNacimiento to despues.fechaNacimiento),
            SEXO to (antes.sexo to despues.sexo.name),
            TELEFONO to (antes.telefono to despues.telefono),
        )

        return candidatos
            .filter { (_, par) -> normalizar(par.first) != normalizar(par.second) }
            .mapValues { (_, par) -> normalizar(par.second) }
    }
}
