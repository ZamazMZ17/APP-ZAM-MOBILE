package com.zamazmz17.zammobile

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.concurrent.Executors

private val Ink = Color(0xFF080B10)
private val Panel = Color(0xFF111720)
private val Green = Color(0xFF35D49A)
private val Muted = Color(0xFF9AA7B5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ZamLinkApp(this) }
    }
}

@Composable
private fun ZamLinkApp(context: Context) {
    var endpoint by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("Sin enlazar") }
    var result by remember { mutableStateOf("Conecta tu laptop para enviar órdenes a Zam.") }
    var isListening by remember { mutableStateOf(false) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoiceCapture(context, { isListening = it }, { command = it }, { result = it })
        else result = "Necesito permiso de micrófono para transcribir tu voz."
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = Green, surface = Panel, background = Ink)) {
        Surface(color = Ink, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(CircleShape).background(Green), contentAlignment = Alignment.Center) {
                        Text("Z", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("ZAM LINK", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Text(state, color = if (state == "Conectado") Green else Muted, fontSize = 12.sp)
                    }
                }
                OutlinedTextField(endpoint, { endpoint = it.trimEnd('/') }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Dirección segura de Ekars") }, placeholder = { Text("https://ekars.tu-dominio.com") })
                Button(onClick = {
                    executor.execute {
                        val ok = ping(endpoint)
                        state = if (ok) "Conectado" else "No se pudo conectar"
                        result = if (ok) "Ekars está listo para recibir órdenes." else "Revisa la dirección o inicia el servidor de Ekars."
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("PROBAR CONEXIÓN") }
                TextButton({ checkAndDownloadUpdate(context) }, Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Buscar actualización", color = Green)
                }
                Text("ORDEN PARA ZAM", color = Muted, fontSize = 12.sp, letterSpacing = 1.sp)
                OutlinedTextField(command, { command = it }, Modifier.fillMaxWidth(), minLines = 3,
                    placeholder = { Text("Ej.: toma una captura y dime qué está abierto") })
                OutlinedButton(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            startVoiceCapture(context, { isListening = it }, { command = it }, { result = it })
                        } else microphonePermission.launch(android.Manifest.permission.RECORD_AUDIO)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isListening
                ) { Text(if (isListening) "ESCUCHANDO…" else "HABLAR Y TRANSCRIBIR") }
                Button(onClick = {
                    val order = command; command = ""; result = "Enviando orden…"
                    executor.execute { result = sendCommand(endpoint, order) }
                }, enabled = command.isNotBlank() && endpoint.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("ENVIAR A ZAM") }
                Text("ACCESOS RÁPIDOS", color = Muted, fontSize = 12.sp, letterSpacing = 1.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    QuickAction("Pantalla") { command = "Toma una captura de pantalla y descríbela." }
                    QuickAction("Estado") { command = "Dime el estado actual del sistema." }
                    QuickAction("Escuchar") { command = "Activa el modo escucha." }
                }
                Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
                    Text(result, color = Color.White, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable private fun RowScope.QuickAction(label: String, click: () -> Unit) {
    OutlinedButton(click, Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text(label, fontSize = 11.sp) }
}

private fun ping(base: String): Boolean = try {
    base.isNotBlank() && OkHttpClient().newCall(Request.Builder().url("$base/health").build()).execute().use { it.isSuccessful }
} catch (_: Exception) { false }

private fun sendCommand(base: String, command: String): String = try {
    val body = JSONObject().put("command", command).toString().toRequestBody("application/json".toMediaType())
    OkHttpClient().newCall(Request.Builder().url("$base/api/v1/commands").post(body).build()).execute().use {
        if (it.isSuccessful) JSONObject(it.body?.string().orEmpty()).optString("message", "Orden enviada a Ekars.")
        else "Ekars rechazó la orden (${it.code})."
    }
} catch (_: Exception) { "No se pudo enviar la orden. Comprueba que Ekars esté conectado." }

private fun startVoiceCapture(
    context: Context,
    onListening: (Boolean) -> Unit,
    onTranscript: (String) -> Unit,
    onError: (String) -> Unit
) {
    if (!SpeechRecognizer.isRecognitionAvailable(context)) {
        onError("El reconocimiento de voz no está disponible en este celular.")
        return
    }
    val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    onListening(true)
    recognizer.setRecognitionListener(object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onPartialResults(partialResults: Bundle?) {
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onTranscript)
        }
        override fun onResults(results: Bundle?) {
            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onTranscript)
            onListening(false)
            recognizer.destroy()
        }
        override fun onError(error: Int) {
            onListening(false)
            onError("No pude entender el audio. Inténtalo otra vez.")
            recognizer.destroy()
        }
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    })
    recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-PE")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    })
}

private fun checkAndDownloadUpdate(context: Context) {
    Executors.newSingleThreadExecutor().execute {
        try {
            val api = Request.Builder().url("https://api.github.com/repos/ZamazMZ17/APP-ZAM-MOBILE/releases/latest").build()
            OkHttpClient().newCall(api).execute().use { response ->
                val assets = JSONObject(response.body?.string().orEmpty()).optJSONArray("assets") ?: return@use
                repeat(assets.length()) { i ->
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name").endsWith(".apk")) {
                        val request = DownloadManager.Request(Uri.parse(asset.getString("browser_download_url")))
                            .setTitle("Actualización de ZAM Link")
                            .setDescription("Descargando nueva versión")
                            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "zam-link-update.apk")
                            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                        return@use
                    }
                }
            }
        } catch (_: Exception) { }
    }
}
