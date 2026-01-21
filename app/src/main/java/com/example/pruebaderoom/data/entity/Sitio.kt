package com.example.pruebaderoom.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Sitio")
data class Sitio(
    @PrimaryKey
    @ColumnInfo(name = "id_sitio")
    val idSitio: String,
    
    @ColumnInfo(name = "nombre")
    val nombre: String,
    
    @ColumnInfo(name = "teem")
    val teem: String,
    
    @ColumnInfo(name = "site_morfology")
    val siteMorfology: String,
    
    @ColumnInfo(name = "new_morfology")
    val newMorfology: String,
    
    @ColumnInfo(name = "visit")
    val visit: Int
)
