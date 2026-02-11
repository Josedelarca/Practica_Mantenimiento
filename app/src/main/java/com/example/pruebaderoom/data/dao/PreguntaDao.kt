package com.example.pruebaderoom.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.example.pruebaderoom.data.entity.Pregunta

@Dao
interface PreguntaDao {

    @Query("SELECT * FROM Preguntas")
    suspend fun getAll(): List<Pregunta>

    @Upsert
    suspend fun upsertAll(preguntas: List<Pregunta>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pregunta: Pregunta)

    @Query("SELECT * FROM Preguntas WHERE id_seccion = :idSeccion")
    suspend fun getBySeccion(idSeccion: Long): List<Pregunta>

    @Query("SELECT * FROM Preguntas WHERE id_seccion IN (SELECT id_seccion FROM Seccion WHERE id_formulario = :idForm)")
    suspend fun getByFormulario(idForm: Long): List<Pregunta>

    @Query("SELECT * FROM Preguntas WHERE id_pregunta = :id")
    suspend fun getById(id: Long): Pregunta?

    @Query("DELETE FROM Preguntas WHERE id_seccion IN (SELECT id_seccion FROM Seccion WHERE id_formulario = :idForm) AND id_pregunta NOT IN (:idsApi)")
    suspend fun deleteOldPreguntas(idForm: Long, idsApi: List<Long>)

    @Query("DELETE FROM Preguntas WHERE id_seccion IN (SELECT id_seccion FROM Seccion WHERE id_formulario = :idFormulario)")
    suspend fun deleteByFormulario(idFormulario: Long)

    @Query("DELETE FROM Preguntas")
    suspend fun deleteAll()
}
