package com.example.pruebaderoom.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.pruebaderoom.data.entity.HistorialEnvio

@Dao
interface HistorialEnvioDao {
    @Insert
    suspend fun insert(historial: HistorialEnvio)

    @Query("SELECT * FROM HistorialEnvio ORDER BY fecha_envio DESC")
    suspend fun getAll(): List<HistorialEnvio>

    @Query("DELETE FROM HistorialEnvio")
    suspend fun deleteAll()
}
