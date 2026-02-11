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

    @Query("DELETE FROM Campos WHERE id_pregunta IN (SELECT id_pregunta FROM Preguntas WHERE id_seccion IN (SELECT id_seccion FROM Seccion WHERE id_formulario = :idFormulario))")
    suspend fun deleteByFormulario(idFormulario: Long)

    @Query("DELETE FROM Campos")
    suspend fun deleteAll()
}
