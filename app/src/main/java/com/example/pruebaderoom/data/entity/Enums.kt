package com.example.pruebaderoom.data.entity

/**
 * Diferentes tipos de mantenimiento que se pueden realizar.
 */
enum class TipoMantenimiento {
    PREVENTIVO, CORRECTIVO, OTROS
}

/**
 * Estados por los que pasa una tarea desde que se crea hasta que se termina.
 */
enum class EstadoTarea {
    EN_PROCESO,  // El técnico la está llenando
    SUBIENDO,    // Se está enviando al servidor (no debe salir en pendientes)
    FINALIZADA   // Proceso terminado con éxito
}
