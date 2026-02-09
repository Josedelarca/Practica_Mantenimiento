package com.example.pruebaderoom.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Campos")
data class Campo(
    @PrimaryKey
    @ColumnInfo(name = "id_campo")
    val idCampo: Long,
    @ColumnInfo(name = "id_pregunta")
    val idPregunta: Long,
    @ColumnInfo(name = "tipo")
    val tipo: String, // "texto", "booleano", "numero", etc.
    @ColumnInfo(name = "label")
    val label: String,
    @ColumnInfo(name = "orden")
    val orden: Int
)
