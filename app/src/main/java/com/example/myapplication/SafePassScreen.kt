package com.example.myapplication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Interfaz de Usuario (UI) para la app SafePass 2026.
 * Diseñada para recolectar y enviar los datos de los asistentes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafePassScreen(
    // Función Lambda (callback) para emitir los datos recolectados
    onRegistrarClick: (Asistente) -> Unit = {}
) {
    // Variables de estado (State) para almacenar lo que el usuario escribe en la pantalla.
    // Usamos 'remember' para que los valores sobrevivan a las recomposiciones de Jetpack Compose.
    var nombreTexto by remember { mutableStateOf("") }
    var edadTexto by remember { mutableStateOf("") }
    var tipoEntradaTexto by remember { mutableStateOf("") }
    var cedulaTexto by remember {mutableStateOf("")}

    // Estado reactivo del registro usando remember y mutableStateOf propio del nivel básico-intermedio.
    var registroState by remember { mutableStateOf<RegistroState>(RegistroState.Idle) }

    // Instancia de la Lógica de Negocio
    val gestorAsistentes = remember { GestorAsistentes() }

    // Scaffold es la estructura base recomendada en Material Design.
    // Automáticamente maneja el Edge-to-Edge si se configura correctamente en la Actividad (API 36).
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("SafePass 2026") }
            )
        }
    ) { innerPadding ->
        
        // Column alinea los elementos de forma vertical, uno debajo del otro.
        Column(
            modifier = Modifier
                // fillMaxSize() asegura que usemos toda la pantalla disponible
                .fillMaxSize()
                // Aplicamos el innerPadding del Scaffold para respetar el Edge-to-Edge y no superponer 
                // componentes con la barra de estado o navegación del sistema.
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 24.dp), // Espaciado interno adicional por estética
            verticalArrangement = Arrangement.spacedBy(16.dp) // Espacio de 16dp entre cada campo
        ) {
            
            // 1. Campo para el Nombre
            OutlinedTextField(
                value = nombreTexto,
                onValueChange = { nombreTexto = it },
                label = { Text("Nombre del asistente") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // 2. Campo para la Edad
            OutlinedTextField(
                value = edadTexto,
                onValueChange = { edadTexto = it },
                label = { Text("Edad") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                // Configuramos el teclado numérico para facilitar la entrada del usuario
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            // 3. Campo para el Tipo de Entrada
            OutlinedTextField(
                value = tipoEntradaTexto,
                onValueChange = { tipoEntradaTexto = it },
                label = { Text("Tipo de Entrada (VIP, General, etc.)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = cedulaTexto,
                onValueChange = {
                    if (it.length <=10){
                        cedulaTexto = it
                    }
                },
                label = { Text("Número de cedula: ")},
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            // Botón de acción para realizar el registro
            Button(
                onClick = {
                    // CONVERSIÓN SEGURA E INYECCIÓN DE DEFAULT: 
                    // Usamos toIntOrNull() para evitar NumberFormatException. 
                    // Si el usuario ingresa letras o vacío, inyectamos 0 con el operador Elvis (?:).
                    // Esto evita un crash, pero intencionalmente hace que falle la validación de negocio (esMayorDeEdad).
                    val edadLimpia: Int = edadTexto.toIntOrNull() ?: 0

                    val cedulaLimpia = cedulaTexto.trim()
                    if(cedulaLimpia.length != 10 || !cedulaLimpia.all{
                        it.isDigit()
                    }){
                        registroState = RegistroState.Error("La cédula debe tener 10 números.")
                    return@Button}

                    // Delegamos toda la validación a la Lógica de Negocio (GestorAsistentes)
                    // Esto devolverá directamente un RegistroState (Success o Error) según sus propias regla
                    val resultado = gestorAsistentes.validarRegistro(
                        nombre = nombreTexto.trim(),
                        edad = edadLimpia,
                        tipoEntrada = tipoEntradaTexto.trim(),
                        cedula = cedulaLimpia
                    )

                    // Actualizamos el estado para que la UI reaccione (el "when" de abajo)
                    registroState = resultado

                    // Usamos la Función de Orden Superior 'procesarAsistente' si fue un éxito
                    if (resultado is RegistroState.Success) {
                        gestorAsistentes.procesarAsistente(resultado.asistente) { asistenteProcesado ->
                            // Validando la prioridad como requiere el examen
                            if (asistenteProcesado.tipoEntrada.equals("VIP", ignoreCase = true)) {
                                println("Validación de Prioridad: ¡Tiene acceso prioritario!")
                            } else {
                                println("Validación de Prioridad: Entrada normal.")
                            }
                            onRegistrarClick(asistenteProcesado)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text("Registrar Asistente")
            }

            // CONTROL DE FLUJO REACTIVO EXHAUSTIVO
            when (val state = registroState) {
                is RegistroState.Idle -> {
                    // Estado inicial: no se muestra nada extra
                }
                is RegistroState.Success -> {
                    // USO DE SCOPE FUNCTION 'let': 
                    // Garantiza el procesamiento seguro dentro del bloque
                    state.asistente.let { asistente ->
                        // Plantillas de cadena ($) para mostrar el resumen
                        Text(
                            text = "¡Éxito! Registrado: ${asistente.nombre}, Edad: ${asistente.edad}, Entrada: ${asistente.tipoEntrada}",
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
                is RegistroState.Error -> {
                    // Mensaje de error elegante
                    Text(
                        text = state.mensaje,
                        color = androidx.compose.ui.graphics.Color.Red,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            // Usamos obtenerAsistentes() para verificar y mostrar cuántas personas se han registrado
            val listaConfirmados = gestorAsistentes.obtenerAsistentes()
            if (listaConfirmados.isNotEmpty()) {
                Text(
                    text = "Total en el sistema: ${listaConfirmados.size} asistente(s)",
                    modifier = Modifier.padding(top = 16.dp),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
