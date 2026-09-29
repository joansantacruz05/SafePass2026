# SafePass 2026 — Sistema de Gestión de Check-in y Validación de Asistentes

> **Documento de soporte para el equipo de documentación.** Describe qué se implementó en el proyecto y qué evidencia capturar para el informe final en PDF. Última actualización: tras la integración completa de las Fases 1 y 2.

---

## 1. Visión General del Sistema

**SafePass 2026** es una aplicación Android para la gestión del registro (*check-in*) de asistentes a un evento. Su objetivo es validar los datos de cada asistente antes de confirmar su asistencia, distinguiendo de forma explícita entre un registro exitoso y un error de negocio (por ejemplo, un asistente menor de edad).

El proyecto se desarrolló en dos fases, **ya integradas y funcionales**:

| Fase | Alcance | Responsable | Estado |
|---|---|---|---|
| **Fase 1** | Lógica de negocio y estructuras de datos (Kotlin puro, sin UI). | Persona A | Completada |
| **Fase 2** | Interfaz gráfica y reactividad (Jetpack Compose + Material 3). | Persona B | Completada |

**Principio arquitectónico:** la lógica de negocio es completamente independiente de la interfaz. `GestorAsistentes` no importa nada de Compose; la UI solo *invoca* y *muestra* el resultado. Esto permite testear las reglas de forma aislada y garantiza que la pantalla nunca decide por sí misma qué es un registro válido.

**Archivos que componen el proyecto:**

| Archivo | Capa | Responsabilidad |
|---|---|---|
| `Asistente.kt` | Datos | Modelo de datos del asistente. |
| `GestorAsistentes.kt` | Lógica | `RegistroState`, función de extensión, reglas de negocio y función de orden superior. |
| `SafePassScreen.kt` | Interfaz | Pantalla Compose, reactividad y manejo de entradas. |
| `MainActivity.kt` | Interfaz | Punto de entrada; activa Edge-to-Edge e invoca la pantalla. |
| `ui/theme/` | Interfaz | Tema, colores y tipografía (Material 3). |

**Stack tecnológico verificado:** Kotlin 2.0.0 · AGP 8.7.0 · Gradle 8.10.2 · Compose BOM 2024.04.01 (Material 3) · compileSdk / targetSdk 36 · minSdk 26 · Java 21.

---

## 2. Capa de Datos y Lógica (Fase 1)

### 2.1 `data class Asistente` e inmutabilidad (`val`)

`Asistente` es el modelo de datos que representa a un asistente, con tres propiedades: `nombre: String`, `edad: Int?` (anulable a propósito) y `tipoEntrada: String` (por ejemplo, `VIP`, `General`).

Se implementó como `data class` porque Kotlin genera automáticamente `equals()`, `hashCode()` y `toString()`, lo que facilita la comparación de objetos y la generación de logs en pruebas.

**¿Por qué las propiedades son inmutables (`val`)?**

- **Previene efectos secundarios.** Una instancia de `Asistente` no puede alterarse tras crearse: un asistente válido lo es durante toda la ejecución y no puede "cambiar de edad" a mitad del flujo, lo que corrompería la lista interna.
- **Es coherente con el modelo de Compose.** Compose recompondrá la pantalla con frecuencia; los objetos inmutables pueden pasarse a cualquier `@Composable` sin riesgo de ser alterados, ya que las actualizaciones viajan por recomposición y no por mutación silenciosa.
- **Refuerza la encapsulación del gestor.** `GestorAsistentes` expone su lista mediante `obtenerAsistentes()`, que retorna una **copia defensiva** (`toList()`). El exterior nunca puede modificar la lista interna, aplicando el mismo principio de inmutabilidad un nivel más arriba.

### 2.2 `sealed class RegistroState` (Idle, Success, Error)

`RegistroState` modela el resultado de una validación. Al ser `sealed`, el compilador garantiza que solo existan las subclases declaradas:

- `data object Idle` — estado inicial/reposo; aún no se ha realizado ninguna validación.
- `data class Success(val asistente: Asistente)` — registro exitoso; transporta al asistente creado.
- `data class Error(val mensaje: String)` — registro fallido; transporta un mensaje legible para el usuario.

**Mejora de seguridad para la interfaz:** por ser una unión cerrada de estados, la UI puede consumirla con un `when` **exhaustivo**, sin cláusula `else` ni valores nulos o centinelas que representen la ausencia de datos. El compilador obliga a cubrir todos los casos, por lo que es imposible que la pantalla quede en un estado indefinido. Este es el sustento teórico del párrafo obligatorio de la sección 4.1.

### 2.3 Extension Function y Higher-Order Function

**a) Función de extensión** — `fun Int.esMayorDeEdad(): Boolean`

Permite evaluar la mayoría de edad **como si fuera un método propio del tipo `Int`**, sin una función auxiliar global ni una clase de utilidad. Se invoca de forma natural: `if (edadValida.esMayorDeEdad())`. Su ventaja es la **extensibilidad**: añade comportamiento a tipos ya existentes, incluidos los de la biblioteca estándar, sin modificarlos.

**b) Función de orden superior** — `fun procesarAsistente(asistente: Asistente, operacion: (Asistente) -> Unit)`

Acepta como parámetro **otra función** y la ejecuta. Esto permite inyectar el comportamiento desde fuera —registrar un log, activar una notificación, aplicar una validación de prioridad— sin acoplar `GestorAsistentes` a ninguna de esas tareas. Es una aplicación del **Principio de Inversión de Dependencias**: la clase define *qué* hacer, el llamador decide *con qué*.

En la UI, la función se consume pasándole una lambda como callback:

```kotlin
gestorAsistentes.procesarAsistente(resultado.asistente) { asistenteProcesado ->
    onRegistrarClick(asistenteProcesado)
}
```

### 2.4 Scope functions y operador Elvis en la lógica

- **`let`** — `edad?.let { edadValida -> ... }` actúa como barrera: el bloque solo se ejecuta si la edad no es nula, evitando un `NullPointerException`.
- **`apply`** — se aplica al construir el `Asistente`; recibe el objeto como receptor (`this`) y devuelve el mismo objeto, permitiendo configurarlo de forma encadenada sin perder la referencia.
- **Elvis `?:`** — cierra la cadena: `return edad?.let { ... } ?: RegistroState.Error("La edad no puede ser nula.")`. Si la edad es válida devuelve `Success`; si es nula, provee un `Error` controlado en lugar de propagar un cierre forzado.

---

## 3. Capa de Interfaz y Reactividad (Fase 2)

### 3.1 Construcción de la UI: `Scaffold` y `Column` con Edge-to-Edge (API 36)

La pantalla se construye en `SafePassScreen.kt` con dos contenedores jerárquicos:

- **`Scaffold`** — es la estructura base recomendada por Material Design. Proporciona la `TopAppBar` con el título "SafePass 2026" y, además, entrega un `innerPadding` que refleja los límites del sistema.
- **`Column`** — alinea verticalmente los campos de entrada, el botón y la zona de resultados, usando `Arrangement.spacedBy(16.dp)` y `Modifier.fillMaxWidth()`.

**Edge-to-Edge.** `MainActivity` invoca `enableEdgeToEdge()` antes de `setContent { }`. En **API 36 el modo Edge-to-Edge es obligatorio**, por lo que el contenido se dibuja detrás de las barras de estado y navegación del sistema. La solución adoptada es aplicar el `innerPadding` que entrega el `Scaffold`:

```kotlin
Column(
    modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)   // respeta las barras del sistema
        .padding(horizontal = 16.dp, vertical = 24.dp)
)
```

Sin este `padding`, los campos de texto quedarían ocultos bajo la barra de estado. Este detalle es verificable en las capturas del emulador y debe mencionarse en el informe.

### 3.2 Estado reactivo y `when` exhaustivo

La reactividad se apoya en `remember` + `mutableStateOf`, con delegación por propiedad (`by`), de modo que los valores sobreviven a las recomposiciones:

```kotlin
var nombreTexto     by remember { mutableStateOf("") }
var edadTexto       by remember { mutableStateOf("") }
var tipoEntradaTexto by remember { mutableStateOf("") }
var registroState   by remember { mutableStateOf<RegistroState>(RegistroState.Idle) }
```

`remember { GestorAsistentes() }` conserva la instancia de la lógica de negocio entre recomposiciones, por lo que el contador de asistentes registrados se mantiene.

El control de flujo se resuelve con un `when` **exhaustivo y sin `else`**, que reacciona a los tres estados:

| Estado | Comportamiento de la UI | Texto mostrado |
|---|---|---|
| `Idle` | No renderiza nada adicional. | — (solo los campos vacíos) |
| `Success` | Muestra el resumen del registro. | `¡Éxito! Registrado: {nombre}, Edad: {edad}, Entrada: {tipoEntrada}` |
| `Error` | Muestra el mensaje en color rojo. | `El asistente es menor de edad.` / `Faltan datos obligatorios (nombre o tipo de entrada).` |

Además, `obtenerAsistentes()` alimenta una línea de resumen: `Total en el sistema: {n} asistente(s)`, que solo aparece cuando hay al menos un registro.

**Cadena reactiva completa:** evento `onClick` → `toIntOrNull()` → `validarRegistro()` (Fase 1) → `registroState` se actualiza → Compose recompone → el `when` renderiza el estado correspondiente. La UI nunca decide una regla de negocio; solo refleja el resultado.

### 3.3 Manejo seguro de entradas de texto

En el `onClick` del botón se concentra la defensa contra datos inválidos:

```kotlin
val edadLimpia: Int = edadTexto.toIntOrNull() ?: 0
```

- **`.toIntOrNull()`** — conversión segura. A diferencia de `.toInt()`, que lanza `NumberFormatException` y cerraría la app si el usuario escribe letras, devuelve `null` ante cualquier error de formato. La app **nunca crashea por entrada inválida**.
- **Elvis `?:` como respaldo** — provee el valor por defecto (`0`) cuando la conversión devuelve `null` por campo vacío o texto no numérico. La app continúa su flujo normal en lugar de propagar la excepción.
- **Scope function `let` + plantillas de cadena (`$`)** — en la rama `Success`, `state.asistente.let { asistente -> ... }` garantiza que el bloque solo se ejecute con un asistente disponible, y la plantilla `"${asistente.nombre}, Edad: ${asistente.edad}, Entrada: ${asistente.tipoEntrada}"` compone el resumen sin concatenaciones manuales ni conversiones que puedan fallar. Además, el *smart cast* garantizado por el `when` hace que el acceso a `state.asistente` y `state.mensaje` sea seguro sin `!!` ni casts manuales.

Complementariamente, `keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)` restringe el teclado a numeración y `.trim()` normaliza los textos antes de enviarlos a la lógica.

> **Comportamiento a documentar:** al usar `?: 0`, una edad vacía o no numérica se convierte en `0`, que falla la regla de `esMayorDeEdad()` y produce el mensaje *"El asistente es menor de edad."*. En consecuencia, desde la interfaz **nunca** se alcanza el mensaje *"La edad no puede ser nula."*, que queda reservado para invocaciones directas de la lógica con `edad = null`. Las capturas del escenario de error deben mostrar, por tanto, el mensaje real.

---

## 4. Lista de Tareas para el Equipo de Documentación (¡Urgente!)

### 4.1 Redacción APA 7 — párrafo obligatorio (máx. 150 palabras)

- Redacten el párrafo que explica **cómo la `sealed class` mejora la seguridad de la interfaz**, tomando como sustento la **sección 2.2** de este documento.
- **Requisito estricto: máximo 150 palabras.** Redacten entre 120 y 145 palabras para dejar margen.
- Estructure el texto en este orden: (1) qué es una unión cerrada de estados; (2) que permite un `when` exhaustivo sin `else`, obligando al compilador a cubrir todos los casos; (3) que esto evita estados indefinidos y previene errores en tiempo de ejecución.
- **Formato APA 7:** párrafo en bloque, sin viñetas, sin sangría de primera línea;Times New Roman 12 o Arial 11, interlineado doble. Citar el proyecto con formato APA (p. ej.: *SafePass 2026* [Proyecto de software], repositorio GitHub, 2026).
- *Borrador de referencia — 123 palabras, editable y siempre por debajo del límite:*

  > La clase sellada `RegistroState` actúa como una unión cerrada de estados posibles durante el registro de un asistente. Al ser `sealed`, el compilador garantiza que solo existan las subclases declaradas: `Idle`, `Success` y `Error`. Esta restricción permite que la interfaz gráfica ejecute un `when` exhaustivo, sin necesidad de una cláusula `else`, ya que el compilador obliga a manejar todos los casos. Gracias a esto, la UI nunca queda en un estado indefinido ni debe recurrir a valores nulos o sentinelas para representar la ausencia de datos. En lugar de múltiples variables y banderas booleanas dispersas, todo el resultado del registro viaja en un único objeto tipado y seguro, lo que simplifica el renderizado de la pantalla y previene excepciones en tiempo de ejecución.

### 4.2 Captura 1 — Estructura de paquetes y archivos (Android Studio)

- En Android Studio, abra el panel **Project** y seleccione la vista de árbol **Project** (no *Android*) en el desplegable superior, para que se muestren las carpetas y no solo los recursos.
- Ubique el módulo `app` → `src/main/java/com/example/myapplication/`. Debe verse la estructura completa: `Asistente.kt`, `GestorAsistentes.kt`, `SafePassScreen.kt`, `MainActivity.kt` y la carpeta `ui/theme/`.
- Título sugerido: *"Figura 1. Estructura de paquetes y archivos del proyecto SafePass 2026."*
- **Nota para la leyenda:** el paquete es `com.example.myapplication` (nombre por defecto de la plantilla de Android Studio). Si lo denominan "SafePass 2026" en la figura, indiquen explícitamente que corresponde al paquete del proyecto, para que la correspondencia con el código sea inequívoca.

### 4.3 Capturas 2, 3 y 4 — Emulador API 36

Configure un dispositivo virtual con **imagen de sistema API 36** (Device Manager → *Create Device* → API 36), que es la condición para observar el comportamiento Edge-to-Edge descrito en la sección 3.1. Ejecute la app desde Android Studio y capture los tres escenarios **con estos datos exactos**, para que las leyendas coincidan con la evidencia.

| # | Estado | Datos a ingresar | Qué debe verse en pantalla |
|---|---|---|---|
| **2** | `Idle` | Dejar los tres campos vacíos, sin pulsar el botón. | `TopAppBar` "SafePass 2026", tres campos vacíos y el botón "Registrar Asistente". Sin mensaje de resultado ni línea de total. |
| **3** | `Success` | Nombre: `Ana Torres` · Edad: `25` · Entrada: `VIP` → pulsar **Registrar Asistente**. | `¡Éxito! Registrado: Ana Torres, Edad: 25, Entrada: VIP` y `Total en el sistema: 1 asistente(s)`. |
| **4** | `Error` | Nombre: `Luis Gomez` · Edad: `16` · Entrada: `General` → pulsar **Registrar Asistente**. | `El asistente es menor de edad.` en rojo. Sin línea de total. |

- Títulos sugeridos: *"Figura 2. Pantalla inicial (estado Idle)."*, *"Figura 3. Registro exitoso (estado Success)."*, *"Figura 4. Manejo de error de validación (estado Error)."*
- Elija el caso **menor de edad** para la Figura 4 porque ejercita directamente la *extension function* `esMayorDeEdad()` descrita en la sección 2.3. **No** usen un campo de edad vacío esperando el mensaje *"La edad no puede ser nula."*: ese texto no es alcanzable desde la UI (ver la nota de la sección 3.3).
- Capturas con el cursor y el teclado numérico visibles; no use imágenes placeholder ni mockups.

### 4.4 Evidencia GitHub — historial y enlace público

- **Enlace público al repositorio:** `https://github.com/joansantacruz05/SafePass2026`
- Verifique que el repositorio sea **público** y que el código esté subido en la rama `master` antes de adjuntar el enlace.
- **Captura del historial:** abra la pestaña **Commits** del repositorio (o ejecute `git log --oneline` en Android Studio) y capture los **4 commits** de la rama `master`. Los mensajes reales son los siguientes; **no las invente ni las reescriban**, deben coincidir con los de la evidencia:

| # | Commit | Mensaje real | Contenido |
|---|---|---|---|
| 1 | `6e31cfe` | `feat: realización del data model + correccion de codigo.` | `Asistente.kt` — modelo de datos. |
| 2 | `bbf7850` | `feat: validación de lógica, actualización de README.MD` | `GestorAsistentes.kt` — reglas de negocio. |
| 3 | `f15cb87` | `feat: implementación de interfaz y gestión de UI State` | `MainActivity.kt` + `RegistroState`. |
| 4 | `7b0895c` | `feat: finalización de Compose Screen, proyecto completado a la espera de futuras integraciones` | `SafePassScreen.kt` — pantalla Compose. |

- Título sugerido: *"Figura 5. Historial de versiones del proyecto en GitHub (rama master)."*
- En el texto del informe, la equivalencia con los commits de referencia del enunciado es: `feat: data model` → commit 1, `feat: logic validation` → commit 2, `feat: ui state` → commit 3, `feat: compose screen` → commit 4.

### 4.5 Exportación final — verificación del `.zip` antes de subir a Moodle

**Método recomendado:** descarguen una **copia fresca** clonando el repositorio de GitHub y comprimir esa carpeta. Así se garantiza que no se incluyan archivos locales de la máquina.

```bash
git clone https://github.com/joansantacruz05/SafePass2026.git
```

Antes de comprimir, verifiquen la checklist técnica:

| Elemento | Valor exigido | Dónde se comprueba |
|---|---|---|
| **Java** | Versión **21** | `app/build.gradle.kts` → `sourceCompatibility` / `targetCompatibility` / `jvmTarget = "21"` |
| **API level** | `compileSdk = 36`, `targetSdk = 36`, `minSdk = 26` | `app/build.gradle.kts` → `android { }` |
| **Gradle (blindado)** | Wrapper **8.10.2** | `gradle/wrapper/gradle-wrapper.properties` |
| **Android Gradle Plugin** | **8.7.0** | `gradle/libs.versions.toml` → `agp` |
| **Kotlin** | **2.0.0** | `gradle/libs.versions.toml` → `kotlin` |
| **Compose BOM** | **2024.04.01** | `gradle/libs.versions.toml` → `composeBom` |

- **Compilación de prueba (obligatoria):** desde la raíz del proyecto ejecuten `.\gradlew.bat clean build` y confirmen que finaliza con `BUILD SUCCESSFUL` y **sin errores**. Adjunten el resultado en su mensaje de entrega.
- **Excluyan del `.zip`:** `local.properties` (contiene la ruta local del SDK, es específica de cada equipo y ya está en `.gitignore`), las carpetas `.gradle/` y `build/`, y `.idea/`.
- El archivo `gradle-wrapper.jar` **sí debe incluirse**; sin él, el proyecto no compilará en el equipo del docente.
- Denle al archivo un nombre descriptivo, por ejemplo: `SafePass2026_Apellido1_Apellido2.zip`.
