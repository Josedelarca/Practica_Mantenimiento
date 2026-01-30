package com.example.pruebaderoom.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pruebaderoom.data.entity.Respuesta
import kotlinx.coroutines.flow.Flow

@Dao
interface RespuestaDao {
    @Query("SELECT * FROM Respuesta")
    suspend fun getAll(): List<Respuesta>

    @Query("SELECT * FROM Respuesta")
    fun getAllFlow(): Flow<List<Respuesta>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(respuesta: Respuesta)

    @Query("SELECT * FROM Respuesta WHERE id_tarea = :idTarea")
    fun getByTareaFlow(idTarea: Long): Flow<List<Respuesta>>

    @Query("SELECT * FROM Respuesta WHERE id_tarea = :idTarea")
    suspend fun getByTarea(idTarea: Long): List<Respuesta>

    @Delete
    suspend fun delete(respuesta: Respuesta)

    /**
     * Borra todas las respuestas vinculadas a una tarea específica.
     * Cambiamos el retorno a Int para solucionar el error de compilación.
     */
    @Query("DELETE FROM Respuesta WHERE id_tarea = :idTarea")
    suspend fun deleteByTarea(idTarea: Long): Int
}
