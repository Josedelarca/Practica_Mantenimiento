package com.example.pruebaderoom.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pruebaderoom.data.entity.Sitio

@Dao
interface SitioDao {
    @Query("SELECT * FROM Sitio")
    suspend fun getAll(): List<Sitio>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sitio: Sitio)

    @Query("SELECT * FROM Sitio WHERE id_sitio = :id")
    suspend fun getById(id: String): Sitio?

    @Query("DELETE FROM Sitio")
    suspend fun deleteAll()
}
