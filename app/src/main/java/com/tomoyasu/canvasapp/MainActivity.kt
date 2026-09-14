package com.tomoyasu.canvasapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxWidth()) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    var showCanvas by remember { mutableStateOf(false) }
    var host by remember { mutableStateOf(ConnectionPrefs.loadHost(context)) }
    var port by remember { mutableStateOf(ConnectionPrefs.loadPort(context)) }

    if (showCanvas) {
        CanvasScreen(host = host, port = port, onBack = { showCanvas = false })
    } else {
        ConnectionSettingsScreen(
            host = host,
            port = port,
            onHostChange = { host = it },
            onPortChange = { port = it },
            onConnected = { showCanvas = true }
        )
    }
}

@Composable
fun ConnectionSettingsScreen(
    host: String,
    port: String,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onConnected: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf("未接続") }
    var testing by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("PCのStudioに接続", style = MaterialTheme.typography.titleLarge)
        Text("StudioサーバーのIPアドレスとポートを入力してください。テザリング中はPCの画面か設定で確認できます。")

        OutlinedTextField(
            value = host,
            onValueChange = onHostChange,
            label = { Text("IPアドレス（例: 192.168.43.23）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = port,
            onValueChange = onPortChange,
            label = { Text("ポート") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        var connected by remember { mutableStateOf(false) }

        Button(
            enabled = !testing && host.isNotBlank() && port.isNotBlank(),
            onClick = {
                testing = true
                connected = false
                status = "接続を確認しています…"
                ConnectionPrefs.save(context, host, port)
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        StudioClient.testConnection(host, port)
                    }
                    status = when (result) {
                        is StudioClient.TestResult.Success -> {
                            connected = true
                            "接続成功（盤 ${result.boardCount}件を確認）"
                        }
                        is StudioClient.TestResult.Failure -> "接続できません: ${result.message}"
                    }
                    testing = false
                }
            }
        ) {
            Text("保存して接続テスト")
        }

        Text(status)

        if (connected) {
            Button(onClick = onConnected) { Text("盤を開く") }
        }
    }
}
