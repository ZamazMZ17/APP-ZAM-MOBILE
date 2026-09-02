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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
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
private val ZamBubble = Color(0xFF17212D)

private enum class AppScreen { CHAT, SETTINGS }
private data class ChatMessage(val fromZam: Boolean, val text: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ZamLinkApp(this) }
    }
}

@Composable
private fun ZamLinkApp(context: Context) {
    val activity = context as ComponentActivity
    var endpoint by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("Sin enlazar") }
    var result by remember { mutableStateOf("Conecta tu laptop para enviar órdenes a Zam.") }
    var isListening by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf(AppScreen.CHAT) }
    val messages = remember {
        mutableStateListOf(ChatMessage(true, "Hola. Soy Zam. Conéctame desde Ajustes y mándame una orden cuando quieras."))
    }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoiceCapture(context, { isListening = it }, { command = it }, { result = it })
        else result = "Necesito permiso de micrófono para transcribir tu voz."
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = Green, surface = Panel, background = Ink), typography = Typography(defaultFontFamily = FontFamily.SansSerif)) {
        Scaffold(
            containerColor = Ink,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                NavigationBar(containerColor = Panel) {
                    NavigationBarItem(selected = screen == AppScreen.CHAT, onClick = { screen = AppScreen.CHAT }, icon = { Icon(Icons.Outlined.ChatBubbleOutline, "Chat") }, label = { Text("Chat") })
                    NavigationBarItem(selected = screen == AppScreen.SETTINGS, onClick = { screen = AppScreen.SETTINGS }, icon = { Icon(Icons.Outlined.Settings, "Ajustes") }, label = { Text("Ajustes") })
                }
            }
        ) { inset ->
            if (screen == AppScreen.CHAT) {
                ChatScreen(
                    messages = messages, command = command, onCommandChange = { command = it }, isListening = isListening, state = state,
                    onVoice = {
                        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                            startVoiceCapture(context, { isListening = it }, { command = it }, { result = it })
                        else microphonePermission.launch(android.Manifest.permission.RECORD_AUDIO)
                    },
                    onSend = {
                        val order = command.trim()
                        if (order.isNotBlank()) {
                            messages.add(ChatMessage(false, order))
                            command = ""
                            executor.execute {
                                val answer = if (endpoint.isBlank()) "Aún no estoy conectado. Ve a Ajustes y agrega la dirección de Ekars."
                                else sendCommand(endpoint, order)
                                activity.runOnUiThread { messages.add(ChatMessage(true, answer)) }
                            }
                        }
                    }, modifier = Modifier.padding(inset)
                )
            } else {
                SettingsScreen(
                    endpoint = endpoint, onEndpointChange = { endpoint = it.trimEnd('/') }, state = state, result = result,
                    onTest = {
                        executor.execute {
                            val ok = ping(endpoint)
                            activity.runOnUiThread {
                                state = if (ok) "Conectado" else "No se pudo conectar"
                                result = if (ok) "Ekars está listo para recibir órdenes." else "Revisa la dirección o inicia el servidor de Ekars."
                            }
                        }
                    }, onUpdate = { checkAndDownloadUpdate(context) }, modifier = Modifier.padding(inset)
                )
            }
        }
    }
}

@Composable
private fun ChatScreen(
    messages: List<ChatMessage>, command: String, onCommandChange: (String) -> Unit, isListening: Boolean, state: String,
    onVoice: () -> Unit, onSend: () -> Unit, modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.cat_icon_launcher), "Perfil de Zam", Modifier.size(46.dp).clip(CircleShape).background(Color(0xFF10241E)), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Zam", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(if (state == "Conectado") "En línea · Ekars conectado" else "Asistente de Ekars", color = if (state == "Conectado") Green else Muted, fontSize = 12.sp)
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(messages) { message -> ChatBubble(message) }
        }
        Surface(color = Panel, modifier = Modifier.fillMaxWidth().imePadding()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(command, onCommandChange, Modifier.fillMaxWidth(), minLines = 1, maxLines = 4,
                    placeholder = { Text("Escribe una orden para Zam…") }, leadingIcon = { Icon(Icons.Outlined.ChatBubbleOutline, null) })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onVoice, Modifier.weight(1f), enabled = !isListening) { Icon(Icons.Outlined.Mic, null); Spacer(Modifier.width(7.dp)); Text(if (isListening) "Escuchando" else "Voz") }
                    Button(onSend, Modifier.weight(1f), enabled = command.isNotBlank()) { Text("Enviar"); Spacer(Modifier.width(7.dp)); Icon(Icons.Outlined.Send, null) }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val alignment = if (message.fromZam) Alignment.Start else Alignment.End
    val color = if (message.fromZam) ZamBubble else Color(0xFF176B50)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Card(colors = CardDefaults.cardColors(containerColor = color), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(0.82f)) {
            Text(message.text, color = Color.White, modifier = Modifier.padding(14.dp))
        }
    }
}

@Composable
private fun SettingsScreen(
    endpoint: String, onEndpointChange: (String) -> Unit, state: String, result: String,
    onTest: () -> Unit, onUpdate: () -> Unit, modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.cat_icon_launcher), "Zam", Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF10241E)), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(12.dp))
            Column { Text("Ajustes", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold); Text("Conexión y aplicación", color = Muted, fontSize = 12.sp) }
        }
        Text("CONEXIÓN", color = Muted, fontSize = 12.sp, letterSpacing = 1.sp)
        Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Link, null, tint = Green); Spacer(Modifier.width(10.dp)); Text(state, color = Color.White, fontWeight = FontWeight.Medium) }
                OutlinedTextField(endpoint, onEndpointChange, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Dirección segura de Ekars") }, placeholder = { Text("https://ekars.tu-dominio.com") })
                Button(onTest, Modifier.fillMaxWidth()) { Text("Probar conexión") }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) { Text(result, color = Muted, modifier = Modifier.padding(14.dp), fontSize = 14.sp) }
        Text("APLICACIÓN", color = Muted, fontSize = 12.sp, letterSpacing = 1.sp)
        OutlinedButton(onUpdate, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.SystemUpdate, null); Spacer(Modifier.width(8.dp)); Text("Buscar actualización") }
    }
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
