package com.example.pruebaderoom.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pruebaderoom.data.entity.Imagen

/**
 * Esta interfaz gestiona las fotos guardadas en el celular.
 * Aquí guardamos la ruta de cada archivo de imagen para saber dónde están.
 */
@Dao
interface ImagenDao {
    
    // Lista todas las imágenes registradas en el sistema
    @Query("SELECT * FROM Imagen")
    suspend fun getAll(): List<Imagen>

    // Guarda una nueva foto en la base de datos
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(imagen: Imagen)

    // Trae todas las fotos que pertenecen a una respuesta específica de una pregunta
    @Query("SELECT * FROM Imagen WHERE id_respuesta = :idRespuesta")
    suspend fun getByRespuesta(idRespuesta: Long): List<Imagen>

    // Borra todas las fotos de una respuesta (por si se reinicia la pregunta)
    @Query("DELETE FROM Imagen WHERE id_respuesta = :idRespuesta")
    suspend fun deleteByRespuesta(idRespuesta: Long)

    // Borra el registro de una foto específica usando su ubicación en la memoria
    @Query("DELETE FROM Imagen WHERE ruta_archivo = :ruta")
    suspend fun deleteByPath(ruta: String)
}
