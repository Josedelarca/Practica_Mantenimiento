package com.example.pruebaderoom.data

import com.google.gson.annotations.SerializedName

data class FormularioFullResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: FormularioApiData
)

data class FormularioApiData(
    @SerializedName("id") val id: Long,
    @SerializedName("nombre") val nombre: String,
    @SerializedName("descripcion") val descripcion: String,
    @SerializedName("secciones") val secciones: List<SeccionApiData>
)

data class SeccionApiData(
    @SerializedName("id") val id: Long,
    @SerializedName("formulario_id") val formularioId: Long,
    @SerializedName("nombre") val nombre: String,
    @SerializedName("orden") val orden: Int,
    @SerializedName("zona") val zona: String, // 'suelo', 'altura' o 'ambos'
    @SerializedName("preguntas") val preguntas: List<PreguntaApiData>
)

data class PreguntaApiData(
    @SerializedName("id") val id: Long,
    @SerializedName("seccion_id") val seccionId: Long,
    @SerializedName("descripcion") val descripcion: String,
    @SerializedName("min_imagenes") val minImagenes: Int,
    @SerializedName("max_imagenes") val maxImagenes: Int,
    @SerializedName("campos") val campos: List<CampoApiData>
)

data class CampoApiData(
    @SerializedName("id") val id: Long,
    @SerializedName("tipo") val tipo: String,
    @SerializedName("label") val label: String,
    @SerializedName("orden") val orden: Int
)
