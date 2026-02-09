package com.example.pruebaderoom.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pruebaderoom.data.entity.Campo

@Dao
interface CampoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(campos: List<Campo>)

    @Query("SELECT * FROM Campos WHERE id_pregunta = :idPregunta ORDER BY orden")
    suspend fun getByPregunta(idPregunta: Long): List<Campo>

    @Query("DELETE FROM Campos")
    suspend fun deleteAll()
}
