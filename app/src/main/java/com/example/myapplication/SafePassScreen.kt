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

            // Botón de acción para realizar el registro
            Button(
                onClick = {
                    // CONVERSIÓN SEGURA: 
                    // Usamos toIntOrNull() para evitar un NumberFormatException ("crasheo").
                    // Si el usuario deja la edad vacía o escribe letras, retornará 'null' en lugar de fallar.
                    val edadLimpia: Int? = edadTexto.toIntOrNull()

                    // Creamos el objeto Asistente con los datos recolectados de la UI
                    val nuevoAsistente = Asistente(
                        nombre = nombreTexto.trim(),
                        edad = edadLimpia,
                        tipoEntrada = tipoEntradaTexto.trim()
                    )

                    // Enviamos el objeto a la capa superior (simulado aquí con un callback)
                    onRegistrarClick(nuevoAsistente)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text("Registrar Asistente")
            }
        }
    }
}
