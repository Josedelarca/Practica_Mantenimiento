package com.example.pruebaderoom.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "Preguntas",
    foreignKeys = [
        ForeignKey(
            entity = Seccion::class,
            parentColumns = ["id_seccion"],
            childColumns = ["id_seccion"]
        )
    ]
)
data class Pregunta(
    @PrimaryKey
    @ColumnInfo(name = "id_pregunta")
    val idPregunta: Long,
    
    @ColumnInfo(name = "id_seccion")
    val idSeccion: Long,
    
    @ColumnInfo(name = "descripcion")
    val descripcion: String,
    
    @ColumnInfo(name = "min_imagenes")
    val minImagenes: Int,
    
    @ColumnInfo(name = "max_imagenes")
    val maxImagenes: Int
)
