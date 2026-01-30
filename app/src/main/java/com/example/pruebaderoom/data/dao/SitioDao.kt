package com.example.pruebaderoom.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.pruebaderoom.data.entity.Sitio
import kotlinx.coroutines.flow.Flow

@Dao
interface SitioDao {
    
    @Query("SELECT * FROM Sitio")
    fun getAllFlow(): Flow<List<Sitio>>

    @Query("SELECT * FROM Sitio")
    suspend fun getAll(): List<Sitio>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sitio: Sitio)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sitios: List<Sitio>)

    @Query("SELECT * FROM Sitio WHERE id_sitio = :id")
    suspend fun getById(id: Long): Sitio? // Cambiado a Long

    @Query("DELETE FROM Sitio")
    suspend fun deleteAll()

    @Transaction
    suspend fun refreshData(sitios: List<Sitio>) {
        deleteAll()
        insertAll(sitios)
    }
}
