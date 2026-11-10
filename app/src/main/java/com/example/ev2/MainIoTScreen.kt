package com.example.ev2

import android.media.AudioManager
import android.media.ToneGenerator
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MainIoTScreen(
    dbHelper: DatabaseHelper,
    currentUser: String = "admin",
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var localIp by remember { mutableStateOf(getLocalIpAddress()) }
    var targetIp by remember { mutableStateOf("") }

    var amenazaActual by remember { mutableStateOf("NORMAL") }
    var ventiladorActual by remember { mutableStateOf("OFF") }
    var eventos by remember { mutableStateOf(dbHelper.getRecentEvents()) }

    // Color del semáforo según amenaza
    val semaforoColor = when (amenazaActual) {
        "GAS" -> Color(0xFF4CAF50)
        "HUMO" -> Color(0xFFFFC107)
        "FUEGO" -> Color(0xFFF44336)
        else -> Color.LightGray
    }

    // Función para reproducir pitidos en background de forma segura
    fun reproducirSonido(amenaza: String) {
        coroutineScope.launch(Dispatchers.IO) {
            var toneGen: ToneGenerator? = null
            try {
                toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                val beeps = when (amenaza) {
                    "GAS" -> 1
                    "HUMO" -> 2
                    "FUEGO" -> 3
                    else -> 0
                }

                repeat(beeps) {
                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 300)
                    delay(500L)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                toneGen?.release()
            }
        }
    }

    // Lógica principal de ejecución de eventos (Locales o Recibidos)
    val procesarEvento: (String, String, String, String) -> Unit = { amenaza, sensor, ventilador, origen ->
        amenazaActual = amenaza
        ventiladorActual = ventilador
        reproducirSonido(amenaza)

        // Registrar en Base de Datos Local
        val fechaHora = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        dbHelper.insertSensorEvent(fechaHora, sensor, amenaza, ventilador, origen)
        eventos = dbHelper.getRecentEvents()
    }

    // Lógica para enviar la trama por Socket (TCP) con soporte de cifrado ISO/IEC 27400
    fun transmitirEvento(amenaza: String, sensor: String, ventilador: String) {
        procesarEvento(amenaza, sensor, ventilador, "TX")

        val ipDestino = targetIp.trim()
        if (ipDestino.isNotBlank()) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    Socket().use { socket ->
                        // Timeout de 3 segundos para evitar congelamientos
                        socket.connect(InetSocketAddress(ipDestino, 8888), 3000)
                        PrintWriter(socket.getOutputStream(), true).use { out ->
                            val tramaPlana = "IOT27400_KEY|$amenaza|$sensor|$ventilador"
                            val tramaCifrada = dbHelper.encryptPayload(tramaPlana)
                            out.println("ENC|$tramaCifrada")
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "Error al enviar a $ipDestino: ${e.localizedMessage ?: "Sin conexión"}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    // Iniciar Servidor Socket en 8888 y liberarlo automáticamente al salir de la pantalla
    DisposableEffect(Unit) {
        var serverSocket: ServerSocket? = null
        val job = coroutineScope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(8888))
                }
                while (isActive) {
                    val clientSocket = serverSocket?.accept() ?: break
                    clientSocket.use { socket ->
                        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                        val rawMessage = reader.readLine()

                        if (rawMessage != null) {
                            // Soporta tanto tramas cifradas (ENC|...) como texto plano directo desde ESP32/Arduino
                            val mensaje = if (rawMessage.startsWith("ENC|")) {
                                dbHelper.decryptPayload(rawMessage.removePrefix("ENC|"))
                            } else {
                                rawMessage
                            }

                            if (mensaje.startsWith("IOT27400_KEY|")) {
                                val partes = mensaje.split("|")
                                if (partes.size >= 4) {
                                    val amenazaRX = partes[1]
                                    val sensorRX = partes[2]
                                    val ventiladorRX = partes[3]

                                    withContext(Dispatchers.Main) {
                                        procesarEvento(amenazaRX, sensorRX, ventiladorRX, "RX")
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    serverSocket?.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        onDispose {
            job.cancel()
            try {
                serverSocket?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // UI con statusBarsPadding() para evitar que la cámara del teléfono tape el encabezado
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "Monitoreo y Control IoT", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "Operador: $currentUser | Canal: TCP/AES-128",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            TextButton(onClick = onLogout) {
                Text("Salir")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Red
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Mi IP (Puerto 8888): $localIp", fontWeight = FontWeight.Bold)
                    TextButton(onClick = { localIp = getLocalIpAddress() }) {
                        Text("Actualizar IP")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = targetIp,
                    onValueChange = { targetIp = it },
                    label = { Text("IP de destino (Otro Celular o ESP32)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Semáforo Visual
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(color = semaforoColor, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = amenazaActual,
                color = if (amenazaActual == "NORMAL") Color.DarkGray else Color.Black,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Ventilador Extractor: $ventiladorActual", fontWeight = FontWeight.SemiBold)

        Spacer(modifier = Modifier.height(16.dp))

        // Botones de Amenazas
        Text("Disparar Alertas (Sensores)", fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                onClick = { transmitirEvento("GAS", "MQ-2", "ON") }
            ) { Text("GAS") }

            Button(
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107), contentColor = Color.Black),
                onClick = { transmitirEvento("HUMO", "MQ-2", "ON") }
            ) { Text("HUMO") }

            Button(
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                onClick = { transmitirEvento("FUEGO", "KY-026", "ON") }
            ) { Text("FUEGO") }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Botones de Control Manual
        Text("Control Manual", fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(onClick = { transmitirEvento(amenazaActual, "MANUAL", "ON") }) {
                Text("Vent. ON")
            }
            OutlinedButton(onClick = { transmitirEvento(amenazaActual, "MANUAL", "OFF") }) {
                Text("Vent. OFF")
            }
            OutlinedButton(onClick = { transmitirEvento("NORMAL", "MANUAL", "OFF") }) {
                Text("Restablecer")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Lista de historial
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Últimos Eventos", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = {
                dbHelper.clearEvents()
                eventos = dbHelper.getRecentEvents()
            }) {
                Text("Limpiar")
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            items(eventos) { evento ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Amenaza: ${evento.amenaza}", fontWeight = FontWeight.Bold)
                            Text(text = evento.origen, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(text = "Sensor: ${evento.sensor} | Vent: ${evento.ventilador}")
                        Text(text = evento.fechaHora, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

// Función auxiliar mejorada para priorizar la IP Wi-Fi (wlan0) sobre datos móviles
fun getLocalIpAddress(): String {
    var fallbackIp: String? = null
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            if (!networkInterface.isUp || networkInterface.isLoopback) continue

            val name = networkInterface.name.lowercase(Locale.ROOT)
            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (!address.isLoopbackAddress && address is Inet4Address) {
                    val ip = address.hostAddress ?: continue
                    // Priorizar interfaz Wi-Fi o Hotspot
                    if (name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("eth")) {
                        return ip
                    }
                    if (fallbackIp == null) {
                        fallbackIp = ip
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return fallbackIp ?: "127.0.0.1"
}