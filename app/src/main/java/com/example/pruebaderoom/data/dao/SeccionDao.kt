package com.example.pruebaderoom.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pruebaderoom.data.entity.Seccion

@Dao
interface SeccionDao {
    @Query("SELECT * FROM Seccion")
    suspend fun getAll(): List<Seccion>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(seccion: Seccion)

    @Query("SELECT * FROM Seccion WHERE id_formulario = :idFormulario")
    suspend fun getByFormulario(idFormulario: Long): List<Seccion>
}
