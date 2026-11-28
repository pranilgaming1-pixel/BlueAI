package com.blueai

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        setContent { BlueAIApp(::speak, ::requestCapture) }
    }
    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) tts?.language = Locale.UK
    }
    private fun speak(text: String) { if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "blueai-reply") }
    private val captureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) { /* User consent was received; this prototype does not read or analyze frames. */ }
    }
    private fun requestCapture() {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        captureLauncher.launch(manager.createScreenCaptureIntent())
    }
    override fun onDestroy() { tts?.stop(); tts?.shutdown(); super.onDestroy() }
}

private data class Message(val body: String, val user: Boolean)

@Composable
private fun BlueAIApp(speak: (String)->Unit, requestCapture: ()->Unit) {
    val navy = Color(0xFF071522); val blue = Color(0xFF48B8FF); val panel = Color(0xFF10283B)
    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Ready") }
    var apiKey by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf(Message("Hello! I'm BlueAI. This prototype chats offline, speaks replies, and can request screen-capture permission.", false)) }
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) input = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
    }
    fun send() {
        val text = input.trim()
        if (text.isBlank()) return
        messages.add(Message(text, true)); input = ""
        val reply = basicReply(text)
        messages.add(Message(reply, false)); speak(reply)
    }
    MaterialTheme(colorScheme = darkColorScheme(primary=blue, background=navy, surface=panel)) {
        Surface(Modifier.fillMaxSize(), color=navy) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).background(blue.copy(.18f), CircleShape), contentAlignment=Alignment.Center) { Text("B", color=blue, fontSize=20.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column { Text("BlueAI", color=Color.White, fontSize=24.sp); Text("Personal assistant prototype", color=blue, fontSize=12.sp) }
                }
                Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment=Alignment.Center) {
                    Box(Modifier.size(106.dp).background(blue.copy(.12f),CircleShape),contentAlignment=Alignment.Center) {
                        Box(Modifier.size(80.dp).background(blue.copy(.24f),CircleShape),contentAlignment=Alignment.Center) {
                            Box(Modifier.size(50.dp).background(blue,CircleShape))
                        }
                    }
                }
                Text(status, color=blue, fontSize=12.sp)
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.weight(1f), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(messages) { msg ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement=if(msg.user) Arrangement.End else Arrangement.Start) {
                            Box(Modifier.widthIn(max=300.dp).background(if(msg.user) blue.copy(.22f) else panel, RoundedCornerShape(16.dp)).padding(12.dp)) { Text(msg.body, color=Color.White) }
                        }
                    }
                }
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Button(onClick={
                        val intent=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to BlueAI")
                        }
                        try { speechLauncher.launch(intent) } catch (_: Exception) { status="Speech recognition unavailable" }
                    }, colors=ButtonDefaults.buttonColors(containerColor=panel)) { Text("Voice",color=blue) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick={ requestCapture(); status="Screen permission prompt requested; frames aren't analyzed in this build." }, colors=ButtonDefaults.buttonColors(containerColor=panel)) { Text("Screen",color=blue) }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().background(panel,RoundedCornerShape(24.dp)).padding(8.dp), verticalAlignment=Alignment.CenterVertically) {
                    BasicTextField(input,{input=it},Modifier.weight(1f).padding(8.dp),textStyle=TextStyle(color=Color.White,fontSize=16.sp),cursorBrush=SolidColor(blue),decorationBox={inner->Box { if(input.isEmpty()) Text("Message BlueAI…",color=Color.Gray); inner() }})
                    Button(onClick=::send, colors=ButtonDefaults.buttonColors(containerColor=blue)) { Text("Send",color=navy) }
                }
                Spacer(Modifier.height(6.dp))
                Column {
                    Text("OpenAI API key (required)", color=blue, fontSize=12.sp)
                    BasicTextField(apiKey, { apiKey=it }, Modifier.fillMaxWidth().background(panel,RoundedCornerShape(12.dp)).padding(10.dp), textStyle=TextStyle(color=Color.White,fontSize=13.sp), cursorBrush=SolidColor(blue), decorationBox={inner->Box { if(apiKey.isEmpty()) Text("Paste API key here",color=Color.Gray); inner() }})
                    Text("Uses gpt-4o-mini. API usage may be billed. Screen analysis is not yet implemented.", color=Color.Gray,fontSize=10.sp)
                }
            }
        }
    }
}
private fun basicReply(text:String):String {
    val t=text.lowercase(Locale.ROOT)
    return when {
        t.contains("hello")||t=="hi" -> "Hello! What can I help with?"
        t.contains("your name")||t.contains("who are you") -> "I'm BlueAI, an Android assistant prototype."
        t.contains("screen") -> "This prototype can request screen-capture permission, but it cannot analyze captured images yet."
        t.contains("offline")||t.contains("internet") -> "This prototype uses simple offline responses. A real local language model is not installed yet."
        t.contains("help") -> "I can respond to a few basic prompts, use speech input, speak replies, and request screen-capture consent."
        else -> "I heard: $text. I'm using simple offline rules right now, not a full AI model."
    }
}
