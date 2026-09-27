package com.example.myapplication

/**
 * Estructura de Datos para un asistente al evento SafePass 2026.
 * Utilizamos 'data class' para representar el modelo de datos de forma clara y directa.
 */
data class Asistente(
    // El nombre del asistente, inmutable.
    val nombre: String,
    
    // La edad es de tipo Int? (anulable) según los requerimientos, ya que podría no ingresarse.
    val edad: Int?,
    
    // El tipo de entrada que posee el asistente (ej. VIP, General, etc.).
    val tipoEntrada: String
)
