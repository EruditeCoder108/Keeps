package dev.keeps.library

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.keeps.library.ui.KeepsApp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {
    private var requestedPage by mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedPage = intent.getStringExtra("keepsScreen")
        setContent { KeepsApp(requestedPage = requestedPage, onPageHandled = { requestedPage = null }) }
    }
    override fun onNewIntent(intent: android.content.Intent) { super.onNewIntent(intent); setIntent(intent); requestedPage = intent.getStringExtra("keepsScreen") }
}
