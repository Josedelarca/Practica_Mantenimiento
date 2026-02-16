package com.example.pruebaderoom.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Seccion")
data class Seccion(
    @PrimaryKey
    @ColumnInfo(name = "id_seccion")
    val idSeccion: Long,
    
    @ColumnInfo(name = "id_formulario")
    val idFormulario: Long,
    
    @ColumnInfo(name = "nombre")
    val nombre: String,

    @ColumnInfo(name = "zona")
    val zona: String = "ambos" // 'suelo', 'altura' o 'ambos'
)
