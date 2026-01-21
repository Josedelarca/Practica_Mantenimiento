package com.example.pruebaderoom.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pruebaderoom.data.entity.Formulario

@Dao
interface FormularioDao {
    @Query("SELECT * FROM Formulario")
    suspend fun getAll(): List<Formulario>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(formulario: Formulario)

    @Query("SELECT * FROM Formulario WHERE id_formulario = :id")
    suspend fun getById(id: Long): Formulario?
}
