package com.example.pruebaderoom.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pruebaderoom.data.entity.Tarea

@Dao
interface TareaDao {
    @Query("SELECT * FROM Tarea")
    suspend fun getAll(): List<Tarea>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tarea: Tarea)

    @Query("SELECT * FROM Tarea WHERE id_tarea = :id")
    suspend fun getById(id: Long): Tarea?

    @Query("SELECT * FROM Tarea WHERE id_sitio = :idSitio")
    suspend fun getBySitio(idSitio: Long): List<Tarea>

    @Delete
    suspend fun delete(tarea: Tarea)
}
