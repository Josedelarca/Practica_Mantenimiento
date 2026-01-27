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
    PENDIENTE, EN_PROCESO, FINALIZADA
}
