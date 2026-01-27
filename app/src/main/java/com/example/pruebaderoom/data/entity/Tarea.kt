package com.example.pruebaderoom.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Una "Tarea" representa una visita de mantenimiento específica.
 * Vincula un Sitio con un Formulario y guarda cuándo y cómo se hizo la inspección.
 */
@Entity(
    tableName = "Tarea",
    foreignKeys = [
        ForeignKey(
            entity = Sitio::class,
            parentColumns = ["id_sitio"],
            childColumns = ["id_sitio"]
        ),
        ForeignKey(
            entity = Formulario::class,
            parentColumns = ["id_formulario"],
            childColumns = ["id_formulario"]
        )
    ]
)
data class Tarea(
    @PrimaryKey
    @ColumnInfo(name = "id_tarea")
    val idTarea: Long, // ID único de la inspección (usamos el tiempo actual en milisegundos)
    
    @ColumnInfo(name = "id_sitio")
    val idSitio: String, // A qué sitio fuimos
    
    @ColumnInfo(name = "id_formulario")
    val idFormulario: Long, // Qué cuestionario usamos
    
    @ColumnInfo(name = "tipo_mantenimiento")
    val tipoMantenimiento: TipoMantenimiento, // Si fue preventivo, correctivo, etc.
    
    @ColumnInfo(name = "fecha")
    val fecha: Date, // Día y hora de la visita
    
    @ColumnInfo(name = "observaciones_generales")
    val observacionesGenerales: String, // Comentarios adicionales del técnico
    
    @ColumnInfo(name = "estado")
    val estado: EstadoTarea // En qué paso va la tarea (pendiente, en proceso, terminada)
)
