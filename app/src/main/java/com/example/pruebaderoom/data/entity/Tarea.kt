package com.example.pruebaderoom.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date
import java.util.UUID

/**
 * Una "Tarea" representa una visita de mantenimiento específica.
 * Hemos eliminado la restricción estricta de ForeignKey para permitir 
 * que el catálogo de Sitios y Formularios se actualice (Borrar/Insertar) 
 * sin borrar el trabajo del técnico.
 */
@Entity(tableName = "Tarea")
data class Tarea(
    @PrimaryKey
    @ColumnInfo(name = "id_tarea")
    val idTarea: Long, 
    
    @ColumnInfo(name = "id_sitio")
    val idSitio: Long, 
    
    @ColumnInfo(name = "id_formulario")
    val idFormulario: Long, 
    
    @ColumnInfo(name = "tipo_mantenimiento")
    val tipoMantenimiento: TipoMantenimiento, 
    
    @ColumnInfo(name = "fecha")
    val fecha: Date, 
    
    @ColumnInfo(name = "observaciones_generales")
    val observacionesGenerales: String, 
    
    @ColumnInfo(name = "estado")
    val estado: EstadoTarea,

    @ColumnInfo(name = "uuid")
    val uuid: String = UUID.randomUUID().toString() // Identificador único para idempotencia en el servidor
)
