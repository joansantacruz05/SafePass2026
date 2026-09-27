package com.example.myapplication

/**
 * Estado que representa el resultado de la validación del registro.
 */
sealed class RegistroState {
    data object Idle : RegistroState()
    data class Success(val asistente: Asistente) : RegistroState()
    data class Error(val mensaje: String) : RegistroState()
}

/**
 * Función de extensión para determinar si una edad es considerada mayor de edad.
 */
fun Int.esMayorDeEdad(): Boolean {
    return this >= 18
}

/**
 * Lógica de Negocio para la app SafePass 2026.
 * Esta clase se encarga de gestionar a los asistentes sin depender de ninguna UI (Compose, XML, etc.).
 * Mantiene un nivel intermedio y sin patrones complejos.
 */
class GestorAsistentes {

    // Estructura de datos en memoria para almacenar la lista de asistentes.
    private val listaAsistentes = mutableListOf<Asistente>()

    /**
     * Registra un nuevo asistente validando reglas básicas de negocio.
     * Retorna el estado del registro (Success o Error) tras la validación.
     */
    fun validarRegistro(nombre: String, edad: Int?, tipoEntrada: String): RegistroState {
        if (nombre.isBlank() || tipoEntrada.isBlank()) {
            return RegistroState.Error("Faltan datos obligatorios (nombre o tipo de entrada).")
        }

        // Se usa la función de alcance 'let' para verificar que la edad no sea nula.
        return edad?.let { edadValida ->
            if (edadValida.esMayorDeEdad()) {
                val asistente = Asistente(
                    nombre = nombre,
                    edad = edadValida,
                    tipoEntrada = tipoEntrada
                ).apply {
                    // Se usa la función de alcance 'apply' para configurar o registrar un log
                    println("Nuevo asistente configurado: $nombre, Edad: $edad")
                }
                
                listaAsistentes.add(asistente)
                RegistroState.Success(asistente)
            } else {
                RegistroState.Error("El asistente es menor de edad.")
            }
        } ?: RegistroState.Error("La edad no puede ser nula.")
    }

    /**
     * Función de Orden Superior (Higher-Order Function) para procesar un asistente
     * aplicando una operación o validación de prioridad.
     */
    fun procesarAsistente(asistente: Asistente, operacion: (Asistente) -> Unit) {
        operacion(asistente)
    }

    /**
     * Devuelve una lista inmutable para proteger los datos internos de modificaciones externas.
     */
    fun obtenerAsistentes(): List<Asistente> {
        return listaAsistentes.toList()
    }
}
