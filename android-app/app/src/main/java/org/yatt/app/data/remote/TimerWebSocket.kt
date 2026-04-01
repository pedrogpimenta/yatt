package org.yatt.app.data.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import org.yatt.app.data.SettingsStore
import java.util.concurrent.TimeUnit

class TimerWebSocket(
    private val settingsStore: SettingsStore,
    private val onTimerEvent: () -> Unit
) {
    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(Dispatchers.IO)

    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var shouldReconnect = false
    private var suppressReconnectOnce = false

    suspend fun connect() {
        val token = settingsStore.authTokenFlow.first() ?: return
        shouldReconnect = true
        reconnectJob?.cancel()
        reconnectJob = null
        val baseUrl = ApiService.BASE_URL
        val wsUrl = baseUrl.replaceFirst("http", "ws").trimEnd('/')
        val request = Request.Builder()
            .url(wsUrl)
            .build()

        if (webSocket != null) {
            suppressReconnectOnce = true
            webSocket?.close(1000, null)
        }
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                reconnectJob?.cancel()
                reconnectJob = null
                val authPayload = JSONObject()
                    .put("type", "auth")
                    .put("token", token)
                webSocket.send(authPayload.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val data = JSONObject(text)
                    if (data.optString("type") == "timer") {
                        onTimerEvent()
                    }
                } catch (_: Exception) {
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                handleSocketEnded()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                handleSocketEnded()
            }
        })
    }

    fun disconnect() {
        shouldReconnect = false
        reconnectJob?.cancel()
        reconnectJob = null
        if (webSocket != null) {
            suppressReconnectOnce = true
            webSocket?.close(1000, null)
        }
        webSocket = null
    }

    private fun handleSocketEnded() {
        if (suppressReconnectOnce) {
            suppressReconnectOnce = false
            return
        }
        if (shouldReconnect) {
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            delay(5000)
            connect()
        }
    }
}
