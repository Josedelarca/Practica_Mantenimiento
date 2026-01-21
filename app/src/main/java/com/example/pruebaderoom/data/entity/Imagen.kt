package com.example.pruebaderoom.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "Imagen",
    foreignKeys = [
        ForeignKey(
            entity = Respuesta::class,
            parentColumns = ["id_respuesta"],
            childColumns = ["id_respuesta"]
        )
    ]
)
data class Imagen(
    @PrimaryKey
    @ColumnInfo(name = "id_imagen")
    val idImagen: Long,
    
    @ColumnInfo(name = "id_respuesta")
    val idRespuesta: Long,
    
    @ColumnInfo(name = "ruta_archivo")
    val rutaArchivo: String,
    
    @ColumnInfo(name = "marca_agua")
    val marcaAgua: String,
    
    @ColumnInfo(name = "fecha")
    val fecha: Date
)
