package com.example.pruebaderoom.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "Seccion",
    foreignKeys = [
        ForeignKey(
            entity = Formulario::class,
            parentColumns = ["id_formulario"],
            childColumns = ["id_formulario"]
        )
    ]
)
data class Seccion(
    @PrimaryKey
    @ColumnInfo(name = "id_seccion")
    val idSeccion: Long,
    
    @ColumnInfo(name = "id_formulario")
    val idFormulario: Long,
    
    @ColumnInfo(name = "nombre")
    val nombre: String
)
