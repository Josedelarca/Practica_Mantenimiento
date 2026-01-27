package com.example.pruebaderoom.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pruebaderoom.data.entity.Respuesta

/**
 * Esta interfaz gestiona las "Respuestas". Una respuesta vincula una pregunta
 * con una tarea específica y guarda su estado (si está terminada o a medias).
 */
@Dao
interface RespuestaDao {
    
    // Obtiene todas las respuestas registradas en el celular
    @Query("SELECT * FROM Respuesta")
    suspend fun getAll(): List<Respuesta>

    // Guarda o actualiza una respuesta (por ejemplo, para cambiar de "En proceso" a "Finalizado")
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(respuesta: Respuesta)

    // Trae todas las respuestas que se han dado dentro de una misma inspección (tarea)
    @Query("SELECT * FROM Respuesta WHERE id_tarea = :idTarea")
    suspend fun getByTarea(idTarea: Long): List<Respuesta>

    // Borra una respuesta específica de la base de datos
    @Delete
    suspend fun delete(respuesta: Respuesta)
}
