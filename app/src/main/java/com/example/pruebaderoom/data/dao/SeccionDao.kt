package com.example.pruebaderoom.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.example.pruebaderoom.data.entity.Seccion

@Dao
interface SeccionDao {
    @Query("SELECT * FROM Seccion WHERE id_formulario = :idFormulario")
    suspend fun getByFormulario(idFormulario: Long): List<Seccion>

    @Query("SELECT * FROM Seccion WHERE id_seccion = :id LIMIT 1")
    suspend fun getById(id: Long): Seccion?

    @Query("SELECT * FROM Seccion")
    suspend fun getAll(): List<Seccion>

    @Upsert
    suspend fun upsertAll(secciones: List<Seccion>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(seccion: Seccion)

    @Query("DELETE FROM Seccion WHERE id_formulario = :idFormulario AND id_seccion NOT IN (:idsApi)")
    suspend fun deleteOldSecciones(idFormulario: Long, idsApi: List<Long>)

    @Query("DELETE FROM Seccion WHERE id_formulario = :idFormulario")
    suspend fun deleteByFormulario(idFormulario: Long)
}
