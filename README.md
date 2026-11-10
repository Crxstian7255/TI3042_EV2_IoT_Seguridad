# Sistema Móvil de Monitoreo y Control de Alertas IoT/OT (TI3042 - Unidad 2)



**Asignatura:** Aplicaciones Móviles para IoT (TI3042) — INACAP

**Estudiante:** Cristian Alfredo Marín Aqueveque

**Repositorio:** `https://github.com/Crxstian7255/TI3042_EV2_IoT_Seguridad`

**Tecnologías:** Android Studio, Kotlin, Jetpack Compose (Material 3), SQLite (`SQLiteOpenHelper`), Sockets TCP/IP sobre Wi-Fi (`ServerSocket` / `Socket`), Criptografía SHA-256 y AES-128 (Estándar ISO/IEC 27400).



## 1. Identificación de la Problemática y Sistemas a Conectar (Criterio 2.1.1.1)



En entornos industriales y domésticos de Tecnología Operacional (**OT**), las fugas de gas inflamable (GLP/metano), la acumulación de humo y los focos de incendio requieren detección temprana y respuesta inmediata sin depender de conexión a internet externa (la cual puede caer durante una emergencia industrial).



Este proyecto resuelve dicha problemática interconectando en una red local inalámbrica (**Wi-Fi P2P**) dos nodos:

1. **Nodo Sensor / Actuador (Emulado en Android o físico mediante ESP32/Arduino Wi-Fi):** Monitorea el **Sensor MQ-2** (detección de Gas y Humo), el **Sensor Infrarrojo KY-026** (detección de Flama/Fuego) y controla un **Ventilador Extractor** para mitigación activa.

2. **Consola Móvil del Operador (Aplicación Android):** Permite a operadores autenticados recibir alertas en tiempo real, visualizar la criticidad mediante un semáforo industrial, recibir alarmas sonoras diferenciadas, controlar manualmente el extractor y auditar todos los eventos (`TX` y `RX`) en una base de datos local **SQLite**.



## 2. Funcionalidades Clave de la Aplicación (Criterio 2.1.1.2)



* **Autenticación y Registro de Operadores (`LoginScreen.kt`):** Control de acceso obligatorio antes de operar la planta, con almacenamiento seguro en SQLite (`IoTApp.db`). Credenciales de prueba por defecto: **Usuario:** `admin` | **Contraseña:** `1234`.

* **Transmisión y Recepción P2P por Sockets TCP (`MainIoTScreen.kt`):** Cada teléfono levanta un servidor en el **puerto 8888** (`ServerSocket`) para escuchar alertas entrantes (`RX`) y actúa como cliente (`Socket`) para disparar alertas o comandos (`TX`) hacia la IP del otro dispositivo.

* **Semáforo Visual y Alarma Auditiva (`ToneGenerator`):** Indicador cromático según amenaza (Gris = Normal, Verde = Gas, Amarillo = Humo, Rojo = Fuego) acompañado de secuencias acústicas en el canal de alarma de hardware (`STREAM_ALARM`: 1 pitido = Gas, 2 pitidos = Humo, 3 pitidos = Fuego).

* **Control Automático y Manual de Mitigación:** Encendido automático del ventilador extractor (`ON`) ante cualquier amenaza detectada, más botonera manual (`Vent. ON`, `Vent. OFF`, `Restablecer`).

* **Bitácora de Auditoría Local (`DatabaseHelper.kt`):** Registro persistente con marca de tiempo (`yyyy-MM-dd HH:mm:ss`), sensor origen (`MQ-2`, `KY-026`, `MANUAL`), tipo de amenaza, estado del ventilador y dirección del flujo (`TX` o `RX`).



## 3. Justificación de Herramientas de Desarrollo (Criterio 2.1.1.3)



| Componente | Herramienta Seleccionada | Justificación Técnica conforme a Estándares Android |

| :--- | :--- | :--- |

| **Lenguaje e IDE** | **Kotlin + Android Studio** | Lenguaje oficial recomendado por Google con seguridad nula (*Null Safety*) y soporte nativo para concurrencia estructurada. |

| **Interfaz de Usuario (UI)** | **Jetpack Compose (Material 3)** | Estándar declarativo moderno de Android que actualiza el semáforo y la lista de eventos reactivamente (`mutableStateOf`) sin manipular vistas XML manualmente. |

| **Concurrencia y Red** | **Kotlin Coroutines (`Dispatchers.IO`)** | Evita el bloqueo del hilo principal (`Main Thread`) y previene excepciones `NetworkOnMainThreadException` durante la escucha continua del `ServerSocket(8888)`. |

| **Persistencia de Datos** | **SQLite (`SQLiteOpenHelper`)** | En entornos industriales OT, la autenticación y la bitácora de auditoría deben funcionar **100% offline** dentro de la planta, garantizando autonomía total frente a caídas de internet. |



## 4. Selección y Justificación del Método de Comunicación Inalámbrica (Criterio 2.1.2.5)



Se seleccionó **Wi-Fi (Sockets TCP/IP en Red Local LAN/Hotspot)** por sobre Bluetooth y MQTT en la nube con base en los tres criterios operativos del proyecto:



| Criterio | Wi-Fi (Sockets TCP Local) — Seleccionado | Bluetooth Clásico / BLE | MQTT (Broker en la Nube) |

| :--- | :--- | :--- | :--- |

| **Distancia (Cobertura)** | **Alta (30 m a +100 m):** Cubre plantas industriales completas mediante routers o puntos de acceso existentes. | **Baja (< 10 m):** Insuficiente para monitorear galpones o salas de máquinas a distancia segura del fuego/gas. | **Global (con Internet):** Depende de salida a internet; si el enlace WAN cae en un incendio, el sistema queda incomunicado. |

| **Estabilidad y Fiabilidad** | **Muy Alta (Orientado a conexión):** El protocolo TCP garantiza entrega ordenada de paquetes y acuse de recibo (ACK) sin intermediarios. | **Media:** Propenso a desconexiones por obstáculos metálicos o interferencia en entornos industriales. | **Alta pero dependiente de terceros:** Requiere mantener un servidor Broker intermedio activo. |

| **Consumo de Energía** | **Moderado/Alto:** Óptimo para consolas de operadores en turno y nodos sensores alimentados por red eléctrica industrial, donde se prioriza **baja latencia (< 50 ms)** en alertas críticas. | **Muy Bajo:** Ideal para sensores de batería pequeña, pero sacrifica alcance en planta. | **Moderado:** Mantiene conexiones *Keep-Alive* constantes hacia internet. |



## 5. Arquitectura de Pantallas y Mockup UI (Criterio 2.1.2.6)



El flujo de navegación consta de dos pantallas principales gestionadas reactivamente en `MainActivity.kt` según el estado de sesión (`loggedUser`):



* **Pantalla 1 (`LoginScreen.kt`):** Título "Acceso IoT", campos `OutlinedTextField` para Usuario y Contraseña (con `PasswordVisualTransformation` para ocultar caracteres), botón primario **"INICIAR SESIÓN"**, botón secundario **"REGISTRAR OPERADOR"** y pie de cumplimiento **ISO/IEC 27400**.

* **Pantalla 2 (`MainIoTScreen.kt`):**

  * **Cabecera:** Nombre del operador activo, indicador de canal seguro (`TCP/AES-128`) y botón `Salir`.

  * **Tarjeta de Red:** Visualización de `Mi IP (Puerto 8888)` detectada automáticamente en la interfaz Wi-Fi (`wlan0`), botón `Actualizar IP` y campo de texto para `IP de destino`.

  * **Centro de Monitoreo:** Semáforo circular dinámico (`100.dp`) e indicador de estado `Ventilador Extractor: ON/OFF`.

  * **Paneles de Acción:** Fila de simulación de sensores (`GAS`, `HUMO`, `FUEGO`) y fila de `Control Manual` (`Vent. ON`, `Vent. OFF`, `Restablecer`).

  * **Historial de Auditoría:** `LazyColumn` con los últimos 15 registros almacenados en SQLite y botón `Limpiar`.



## 6. Seguridad IoT implementada según Norma ISO/IEC 27400 (Criterios 2.1.3.9, 2.1.3.10 y 2.1.4.14)



1. **Cifrado de Credenciales en Reposo (`hashSHA256`):** Las contraseñas de los operadores jamás se guardan en texto plano. Se procesan mediante `java.security.MessageDigest` con el algoritmo **SHA-256**, almacenando únicamente el digest hexadecimal de 64 caracteres en la tabla `usuarios`.

2. **Cifrado de Datos Sensibles en Tránsito (`encryptPayload` / `decryptPayload`):** Las tramas enviadas por el Socket TCP (`IOT27400_KEY|AMENAZA|SENSOR|VENTILADOR`) se cifran mediante **AES-128 (`AES/ECB/PKCS5Padding`) + Base64** antes de viajar por la red Wi-Fi (`ENC|<payload_cifrado>`), protegiendo la confidencialidad e integridad en la red local.

3. **Control de Acceso y Permisos Mínimos (`AndroidManifest.xml`):** Solo se solicitan los permisos estrictamente necesarios para la operación en red local (`INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`) bajo el principio de mínimo privilegio.

4. **Trazabilidad y No Repudio (`eventos_iot`):** Toda alerta emitida (`TX`) o recibida (`RX`) queda registrada inmediatamente en SQLite con fecha y hora exacta para auditoría post-incidente.



## 7. Estructura del Código Fuente



* [app/src/main/AndroidManifest.xml](https://www.google.com/search?q=app/src/main/AndroidManifest.xml): Configuración de permisos de red (`INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`).

* [app/src/main/java/com/example/ev2/MainActivity.kt](https://www.google.com/search?q=app/src/main/java/com/example/ev2/MainActivity.kt): Punto de entrada y control de sesión entre `LoginScreen` y `MainIoTScreen`.

* [app/src/main/java/com/example/ev2/LoginScreen.kt](https://www.google.com/search?q=app/src/main/java/com/example/ev2/LoginScreen.kt): Interfaz de autenticación y registro de operadores.

* [app/src/main/java/com/example/ev2/MainIoTScreen.kt](https://www.google.com/search?q=app/src/main/java/com/example/ev2/MainIoTScreen.kt): Servidor TCP (`ServerSocket` 8888), cliente TCP (`Socket`), semáforo visual, alarma sonora (`ToneGenerator`) y bitácora de eventos.

* [app/src/main/java/com/example/ev2/DatabaseHelper.kt](https://www.google.com/search?q=app/src/main/java/com/example/ev2/DatabaseHelper.kt): Persistencia local SQLite (`usuarios` y `eventos_iot`), hash criptográfico SHA-256 y cifrado AES-128.

