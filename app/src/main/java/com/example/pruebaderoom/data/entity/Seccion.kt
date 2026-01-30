package com.example.pruebaderoom.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad Seccion: Hemos eliminado la restricción estricta de ForeignKey para permitir 
 * que la estructura del formulario se borre y actualice sin errores.
 */
@Entity(tableName = "Seccion")
data class Seccion(
    @PrimaryKey
    @ColumnInfo(name = "id_seccion")
    val idSeccion: Long,
    
    @ColumnInfo(name = "id_formulario")
    val idFormulario: Long,
    
    @ColumnInfo(name = "nombre")
    val nombre: String
)
