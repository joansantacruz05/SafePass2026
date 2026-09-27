# SafePass 2026 — Sistema de Gestión de Check-in y Validación de Asistentes

## 1. Título y Contexto

**SafePass 2026** es una aplicación Android destinada a la gestión del registro (check-in) de asistentes a un evento. Cumple dos funciones principales:

- **Registrar** a cada asistente validando reglas de negocio antes de dar por válida su asistencia.
- **Reportar** el resultado de esa validación de forma clara y consistente, distinguiendo entre un registro exitoso y un error de negocio (por ejemplo, un asistente menor de edad).

El sistema se desarrolla en dos fases. La **Fase 1 (completada)** corresponde a la *lógica de negocio y las estructuras de datos*, escrita en Kotlin puro, sin ninguna dependencia de la interfaz gráfica. La **Fase 2 (pendiente)** corresponde al desarrollo de la interfaz en **Jetpack Compose**, a cargo de Persona B.

> **Principio de diseño de la Fase 1:** la lógica de negocio es completamente independiente de la UI. Esto permite que la lógica sea testeable de forma aislada y que la capa visual solo se limite a *mostrar* el resultado, sin decidir reglas de negocio.

**Stack tecnológico:** Kotlin · Android SDK 36 · minSdk 26 · Jetpack Compose (Material 3) · Gradle Kotlin DSL.

---

## 2. Arquitectura Implementada (Fase 1)

La lógica de negocio reside en el paquete `com.example.myapplication`, distribuida en dos archivos:

| Archivo | Responsabilidad |
|---|---|
| `Asistente.kt` | Modelo de datos (estructura de datos del asistente). |
| `GestorAsistentes.kt` | Estados de la UI, función de extensión, lógica de registro y función de orden superior. |

### 2.1 La `data class Asistente` y la inmutabilidad (`val`)

`Asistente` es el modelo de datos que representa a una persona asistente al evento:

- `nombre: String`
- `edad: Int?` — **anulable a propósito**, ya que el campo podría no ingresarse.
- `tipoEntrada: String` — por ejemplo, `VIP`, `General`, etc.

Se implementó como `data class` porque Kotlin genera de forma automática las funciones `equals()`, `hashCode()` y `toString()`, lo que facilita enormemente la comparación y el registro de objetos en pruebas o logs.

**¿Por qué las propiedades son inmutables (`val`)?**

- **Previene efectos secundarios no deseados.** Al declarar todas las propiedades con `val`, una instancia de `Asistente` no puede ser alterada una vez creada. Si un asistente es válido, *es* válido durante toda la ejecución: no puede "cambiar de edad" a mitad del flujo y corromper la lista interna.
- **Facilita el razonamiento y la seguridad en la UI.** Un objeto inmutable puede llegar a cualquier `composable` de Compose sin riesgo de ser alterado por una recomposición, lo que se alinea con el modelo de datos inmutable de Compose.
- **Protege la encapsulación del gestor.** El `GestorAsistentes` mantiene su lista privada `listaAsistentes`. Al exponerla mediante `obtenerAsistentes()`, retorna una **copia defensiva** (`toList()`), de modo que el exterior nunca pueda modificar la lista interna. Esta decisión complementaria refuerza el mismo principio de inmutabilidad.

### 2.2 La `sealed class RegistroState` (Idle, Success, Error)

`RegistroState` modela el **resultado** de una validación de registro. Al ser `sealed`, el compilador garantiza que solo existan las subclases declaradas:

- `data object Idle` — estado inicial/reposo: no se ha realizado ninguna validación.
- `data class Success(val asistente: Asistente)` — el registro fue exitoso e incluye al asistente creado.
- `data class Error(val mensaje: String)` — el registro falló e incluye un mensaje legible para el usuario.

**Mejora de seguridad para la interfaz:** al ser una unión cerrada de estados, la UI podrá consumirla con una expresión `when` **exhaustiva**. El compilador obliga a cubrir todos los casos, por lo que no hace falta una cláusula `else` ni valores nulos o centinelas para representar la ausencia de datos. Esto previene estados indefinidos y excepciones en tiempo de ejecución. (Ver la sección 4 para el uso exacto de esta información en el informe.)

### 2.3 Función de extensión y función de orden superior

En `GestorAsistentes.kt` se implementaron dos constructs de Kotlin de alcance medio:

**a) Función de extensión** — `fun Int.esMayorDeEdad(): Boolean`

Permite evaluar la mayoría de edad **como si fuera un método propio del tipo `Int`**, sin necesidad de una función auxiliar global ni una clase de utilidad. Se invoca de forma natural y legible:

```kotlin
if (edadValida.esMayorDeEdad()) { ... }
```

Su ventaja principal es la **extensibilidad**: puede añadir comportamiento a tipos ya existentes (incluidos los de la biblioteca estándar) sin modificarlos.

**b) Función de orden superior (Higher-Order Function)** — `fun procesarAsistente(asistente: Asistente, operacion: (Asistente) -> Unit)`

Acepta como parámetro **otra función** (`(Asistente) -> Unit`) y la ejecuta. Esto permite que el comportamiento se inyecte desde fuera — por ejemplo, aplicar una validación de prioridad, registrar un log o activar una notificación — sin acoplar `GestorAsistentes` a ninguna de esas tareas concretas. Es una aplicación directa del **Principio de Inversión de Dependencias**: la clase define *qué* hacer (ejecutar una operación), mientras el llamador decide *con qué* hacerlo.

---

## 3. Seguridad y Prevención de Crashes

El objetivo de esta sección es demostrar que el sistema **no se bloquea (no lanza excepciones) ante datos inválidos, incompletos o nulos**, en lugar de forzar cierres inseguros.

### 3.1 Conversión segura de tipos (`toIntOrNull()` / `toDoubleOrNull()`)

- **Estado: se aplicará en la Fase 2 (capa de UI).** En la frontera de entrada de datos, los campos de texto se convierten con `toIntOrNull()` / `toDoubleOrNull()` en lugar de `toInt()` / `toDouble()`. A diferencia de las conversiones forzadas —que lanzan `NumberFormatException` y cierran la app ante un texto no numérico—, las conversiones seguras devuelven `null` ante cualquier error de formato, lo que permite enrutarlo a un `RegistroState.Error` en lugar de propagar la excepción. *(Nota: la lógica de la Fase 1 ya recibe la edad como `Int?` y valida su nulabilidad; la conversión de `String` a `Int` se incorporará en `MainActivity`/Compose.)*

### 3.2 Scope functions: `let` y `apply` (implementadas en Fase 1)

- **`let` (scope function)** — Se usa en `validarRegistro()` para ejecutar la lógica de negocio **solo si la edad no es nula**. `edad?.let { edadValida -> ... }` actúa como una barrera: el bloque interno únicamente se ejecuta cuando existe un valor, y se omite por completo en caso contrario, evitando un `NullPointerException`.
- **`apply` (scope function)** — Se usa al construir el `Asistente` dentro del mismo `let`. `apply` recibe el objeto como receptor (`this`) y devuelve el mismo objeto, por lo que permite configurar o registrar el asistente de forma encadenada sin perder la referencia al resultado.

Ambas pueden resumirse así: `let` **filtra por nulidad** y `apply` **encadena la construcción del objeto**.

### 3.3 Operador Elvis `?:` (implementado en Fase 1)

El operador Elvis `?:` es la herramienta de Kotlin que permite proporcionar un valor por defecto cuando una expresión es `null`. En `validarRegistro()` se usa al final de la cadena:

```kotlin
return edad?.let { edadValida -> ... } ?: RegistroState.Error("La edad no puede ser nula.")
```

Es decir: si la edad es válida, devuelve `RegistroState.Success(...)`; si es `null` (el `let` no se ejecutó y devolvió `null`), el operador Elvis provee un valor por defecto —un `RegistroState.Error`— que la interfaz mostrará al usuario. Así se **sustituye el cierre forzado ante un dato nulo por un mensaje de error controlado**.

---

## 4. Instrucciones para el Equipo de Documentación (¡Muy Importante!)

A continuación, las tareas concretas que el equipo de documentación debe realizar. Léanlas con atención y, ante cualquier duda, consulten al responsable técnico antes de redactar.

### 4.1 Captura de pantalla de la estructura de paquetes y archivos — ¡Ya pueden realizarla!

- **Pueden proceder ahora mismo a capturar la pantalla** de la estructura de paquetes y archivos directamente desde Android Studio. No es necesario esperar a que termine la interfaz gráfica, porque la captura corresponde al árbol de archivos de la **Fase 1**, que ya está completo.
- **Ruta sugerida en Android Studio:** en el panel **Project**, ubique el módulo `app` → `src/main/java/com/example/myapplication/`, o bien active la vista de árbol con **Project** (no *Android*) en el desplegable superior para que se muestren las carpetas y no solo los recursos.
- **Título sugerido para la figura en el informe:** *"Figura 1. Estructura de paquetes y archivos de la lógica de negocio (Fase 1)."*
- **Nota importante para la leyenda:** el paquete actual es `com.example.myapplication` (el nombre por defecto de la plantilla de Android Studio). Si en la leyenda de la figura lo nombran como "SafePass 2026", indiquen explícitamente que se trata del paquete del proyecto, para que la correspondencia con el código sea clara.

### 4.2 Explicación obligatoria de máximo 150 palabras sobre la `sealed class`

- Ya pueden redactar este apartado del informe. **Pueden basarse en la información de la sección 2.2** de este documento, que describe cómo la `sealed class RegistroState` mejora la seguridad de la interfaz gráfica.
- **Requisito estricto:** el texto final **no debe superar las 150 palabras**. Se recomienda escribir entre 120 y 145 palabras para tener margen.
- El texto debe cubrir tres ideas, en este orden: (1) qué es una unión cerrada de estados; (2) que permite un `when` exhaustivo sin `else`, obligando al compilador a cubrir todos los casos; y (3) que esto evita estados indefinidos y previene errores en tiempo de ejecución.
- *Borrador opcional de referencia (123 palabras — editable libremente, siempre por debajo del límite):*

  > La clase sellada `RegistroState` actúa como una unión cerrada de estados posibles durante el registro de un asistente. Al ser `sealed`, el compilador garantiza que solo existan las subclases declaradas: `Idle`, `Success` y `Error`. Esta restricción permite que la interfaz gráfica ejecute un `when` exhaustivo, sin necesidad de una cláusula `else`, ya que el compilador obliga a manejar todos los casos. Gracias a esto, la UI nunca queda en un estado indefinido ni debe recurrir a valores nulos o sentinelas para representar la ausencia de datos. En lugar de múltiples variables y banderas booleanas dispersas, todo el resultado del registro viaja en un único objeto tipado y seguro, lo que simplifica el renderizado de la pantalla y previene excepciones en tiempo de ejecución.

### 4.3 Capturas del emulador — ¡Aún NO pueden tomarlas!

Las siguientes capturas **deben esperar a la Fase 2**, es decir, a que **Persona B termine la interfaz gráfica en Jetpack Compose**. No se deben generar imágenes-placeholder ni modificarlas:

1. **Pantalla inicial.**
2. **Registro exitoso.**
3. **Manejo de error.**

Una vez que Compose esté integrado, la fuente de verdad para estas imágenes será el emulador ejecutando la app con datos reales (un asistente válido, un asistente menor de edad y un asistente con edad nula o inválida), no esquemas ni mockups. **No adelanten estas capturas.**

---

### Resumen de estado

| Ítem | Estado |
|---|---|
| Estructura de datos (`data class Asistente`) | Completado |
| Estados de UI (`sealed class RegistroState`) | Completado |
| Función de extensión + HOF | Completado |
| Scope functions (`let`, `apply`) y Elvis (`?:`) | Completado |
| Conversión segura (`toIntOrNull()` / `toDoubleOrNull()`) | Pendiente (Fase 2) |
| Interfaz gráfica (Jetpack Compose) | Pendiente (Persona B) |
| Capturas de pantalla del emulador | Pendiente (Fase 2) |
