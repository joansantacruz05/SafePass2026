package com.example.myapplication

/**
 * Lógica de Negocio para la app SafePass 2026.
 * Esta clase se encarga de gestionar a los asistentes sin depender de ninguna UI (Compose, XML, etc.).
 * Mantiene un nivel intermedio, fácil de estudiar y sin patrones complejos.
 */
class GestorAsistentes {

    // Estructura de datos en memoria para almacenar la lista de asistentes.
    private val listaAsistentes = mutableListOf<Asistente>()

    /**
     * Registra un nuevo asistente validando reglas básicas de negocio.
     * Retorna true si el registro fue exitoso, y false si no pasó la validación.
     */
    fun registrarAsistente(asistente: Asistente): Boolean {
        if (!validarAsistente(asistente)) {
            return false
        }
        
        listaAsistentes.add(asistente)
        return true
    }

    /**
     * Reglas de validación sencillas de acuerdo al dominio de SafePass.
     */
    private fun validarAsistente(asistente: Asistente): Boolean {
        // El nombre no debe estar en blanco
        if (asistente.nombre.isBlank()) {
            return false
        }
        
        // Si proporciona edad, debe tener una edad lógica (mayor de 0)
        if (asistente.edad != null && asistente.edad <= 0) {
            return false
        }
        
        // El tipo de entrada debe estar definido
        if (asistente.tipoEntrada.isBlank()) {
            return false
        }

        return true
    }

    /**
     * Devuelve una lista inmutable para proteger los datos internos de modificaciones externas.
     */
    fun obtenerAsistentes(): List<Asistente> {
        return listaAsistentes.toList()
    }
}
