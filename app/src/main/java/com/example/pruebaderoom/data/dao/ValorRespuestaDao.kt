package com.example.pruebaderoom.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pruebaderoom.data.entity.ValorRespuesta

@Dao
interface ValorRespuestaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(valores: List<ValorRespuesta>)

    @Query("SELECT * FROM ValoresRespuesta WHERE id_respuesta = :idRespuesta")
    suspend fun getByRespuesta(idRespuesta: Long): List<ValorRespuesta>

    @Query("DELETE FROM ValoresRespuesta WHERE id_respuesta = :idRespuesta")
    suspend fun deleteByRespuesta(idRespuesta: Long)

    @Query("DELETE FROM ValoresRespuesta WHERE id_respuesta IN (SELECT id_respuesta FROM Respuesta WHERE id_tarea = :idTarea)")
    suspend fun deleteByTarea(idTarea: Long)
}
