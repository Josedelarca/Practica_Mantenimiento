package com.example.pruebaderoom.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

/**
 * Representa un lugar físico (como una antena o torre) donde se realiza el mantenimiento.
 * Esta clase sirve tanto para la base de datos local como para entender lo que viene del servidor.
 */
@Entity(tableName = "Sitio")
data class Sitio(
    @PrimaryKey
    @ColumnInfo(name = "id_sitio")
    @SerializedName("id")
    val idSitio: String, // ID único que le da el sistema
    
    @ColumnInfo(name = "nombre")
    @SerializedName("nombre")
    val nombre: String, // Nombre común del sitio
    
    @ColumnInfo(name = "teem")
    @SerializedName("teem")
    val teem: String, // Equipo encargado del sitio
    
    @ColumnInfo(name = "site_morfology")
    @SerializedName("site_morfology")
    val siteMorfology: String, // Tipo de estructura actual
    
    @ColumnInfo(name = "new_morfology")
    @SerializedName("new_morfology")
    val newMorfology: String,

    @ColumnInfo(name = "visit")
    @SerializedName("visit")
    val visit: Int, // Número de visitas o prioridad

    @ColumnInfo(name = "latitud")
    @SerializedName("latitud")
    val latitud: Double, // Ubicación para el mapa

    @ColumnInfo(name = "longitud")
    @SerializedName("longitud")
    val longitud: Double
)
