package com.example.pruebaderoom.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

@Entity(tableName = "Imagen")
data class Imagen(
    @PrimaryKey
    @ColumnInfo(name = "id_imagen")
    val idImagen: Long,
    
    @ColumnInfo(name = "id_respuesta")
    val idRespuesta: Long,
    
    @ColumnInfo(name = "ruta_archivo")
    val rutaArchivo: String,
    
    @ColumnInfo(name = "tipo")
    val tipo: String,
    
    @ColumnInfo(name = "fecha")
    val fecha: Date,

    @ColumnInfo(name = "uuid")
    val uuid: String = UUID.randomUUID().toString(), // Para idempotencia en Laravel

    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = false // Estado de sincronización segmentada
)
