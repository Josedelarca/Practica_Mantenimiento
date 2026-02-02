package com.example.pruebaderoom.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pruebaderoom.data.entity.Imagen

@Dao
interface ImagenDao {
    @Query("SELECT * FROM Imagen")
    suspend fun getAll(): List<Imagen>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(imagen: Imagen)

    @Query("SELECT * FROM Imagen WHERE id_respuesta = :idRespuesta")
    suspend fun getByRespuesta(idRespuesta: Long): List<Imagen>

    @Query("UPDATE Imagen SET is_synced = :synced WHERE id_imagen = :id")
    suspend fun updateSyncStatus(id: Long, synced: Boolean)

    @Query("DELETE FROM Imagen WHERE id_respuesta IN (SELECT id_respuesta FROM Respuesta WHERE id_tarea = :idTarea)")
    suspend fun deleteByTarea(idTarea: Long)

    @Query("DELETE FROM Imagen WHERE id_respuesta = :idRespuesta")
    suspend fun deleteByRespuesta(idRespuesta: Long)

    @Query("DELETE FROM Imagen WHERE ruta_archivo = :ruta")
    suspend fun deleteByPath(ruta: String)
}
