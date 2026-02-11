package com.example.pruebaderoom.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "HistorialEnvio")
data class HistorialEnvio(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "sitio_nombre")
    val sitioNombre: String,
    @ColumnInfo(name = "formulario_nombre")
    val formularioNombre: String,
    @ColumnInfo(name = "fecha_envio")
    val fechaEnvio: Date
)
