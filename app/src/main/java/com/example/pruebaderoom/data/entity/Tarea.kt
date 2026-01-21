package com.example.pruebaderoom.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.Date

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
    val idTarea: Long,
    
    @ColumnInfo(name = "id_sitio")
    val idSitio: String,
    
    @ColumnInfo(name = "id_formulario")
    val idFormulario: Long,
    
    @ColumnInfo(name = "tipo_mantenimiento")
    val tipoMantenimiento: TipoMantenimiento,
    
    @ColumnInfo(name = "fecha")
    val fecha: Date,
    
    @ColumnInfo(name = "observaciones_generales")
    val observacionesGenerales: String,
    
    @ColumnInfo(name = "estado")
    val estado: EstadoTarea
)
