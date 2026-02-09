package com.example.pruebaderoom.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ValoresRespuesta")
data class ValorRespuesta(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id_valor")
    val id: Long = 0,
    
    @ColumnInfo(name = "id_respuesta")
    val idRespuesta: Long,
    
    @ColumnInfo(name = "id_campo")
    val idCampo: Long,
    
    @ColumnInfo(name = "valor")
    val valor: String
)
