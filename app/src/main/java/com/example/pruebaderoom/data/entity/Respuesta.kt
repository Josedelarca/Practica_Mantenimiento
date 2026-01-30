package com.example.pruebaderoom.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Entidad Respuesta: Hemos eliminado las ForeignKeys para permitir 
 * que la estructura del formulario se borre y actualice sin errores.
 */
@Entity(tableName = "Respuesta")
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
