package com.example.pruebaderoom.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "Respuesta",
    foreignKeys = [
        ForeignKey(
            entity = Pregunta::class,
            parentColumns = ["id_pregunta"],
            childColumns = ["id_pregunta"]
        ),
        ForeignKey(
            entity = Tarea::class,
            parentColumns = ["id_tarea"],
            childColumns = ["id_tarea"]
        )
    ]
)
data class Respuesta(
    @PrimaryKey
    @ColumnInfo(name = "id_respuesta")
    val idRespuesta: Long,
    
    @ColumnInfo(name = "id_pregunta")
    val idPregunta: Long,
    
    @ColumnInfo(name = "id_tarea")
    val idTarea: Long,
    
    @ColumnInfo(name = "texto_varchar")
    val texto: String,
    
    @ColumnInfo(name = "fecha")
    val fecha: Date
)
