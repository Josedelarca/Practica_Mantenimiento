package com.example.pruebaderoom.data

import com.google.gson.annotations.SerializedName

data class FormularioFullResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: FormularioData
)

data class FormularioData(
    @SerializedName("id") val id: Long,
    @SerializedName("nombre") val nombre: String,
    @SerializedName("descripcion") val descripcion: String,
    @SerializedName("secciones") val secciones: List<SeccionApiData>
)

data class SeccionApiData(
    @SerializedName("id") val id: Long,
    @SerializedName("nombre") val nombre: String,
    @SerializedName("orden") val orden: Int,
    @SerializedName("preguntas") val preguntas: List<PreguntaApiData>
)

data class PreguntaApiData(
    @SerializedName("id") val id: Long,
    @SerializedName("descripcion") val descripcion: String,
    @SerializedName("tipo") val tipo: String,
    @SerializedName("min_imagenes") val minImagenes: Int,
    @SerializedName("max_imagenes") val maxImagenes: Int
)
