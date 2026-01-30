package com.example.pruebaderoom.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad Pregunta: Hemos eliminado la restricción estricta de ForeignKey para permitir 
 * que la estructura del formulario se borre y actualice sin errores.
 */
@Entity(tableName = "Preguntas")
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
