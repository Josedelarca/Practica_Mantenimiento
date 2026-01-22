package com.example.pruebaderoom.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "Sitio")
data class Sitio(
    @PrimaryKey
    @ColumnInfo(name = "id_sitio")
    @SerializedName("id")
    val idSitio: String,
    
    @ColumnInfo(name = "nombre")
    @SerializedName("nombre")
    val nombre: String,
    
    @ColumnInfo(name = "teem")
    @SerializedName("teem")
    val teem: String,
    
    @ColumnInfo(name = "site_morfology")
    @SerializedName("site_morfology")
    val siteMorfology: String,
    
    @ColumnInfo(name = "new_morfology")
    @SerializedName("new_morfology")
    val newMorfology: String,

    @ColumnInfo(name = "visit")
    @SerializedName("visit")
    val visit: Int,

    @ColumnInfo(name = "latitud")
    @SerializedName("latitud")
    val latitud: Double,

    @ColumnInfo(name = "longitud")
    @SerializedName("longitud")
    val longitud: Double
)
