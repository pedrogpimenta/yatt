package org.yatt.app.data.remote

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.yatt.app.R
import java.io.IOException
import kotlinx.coroutines.Dispatchers

data class DeviceCodeInfo(
    val userCode: String,
    val deviceCode: String,
    val verificationUri: String,
    val expiresIn: Int,
    val interval: Int,
    val message: String
)

data class TokenResult(
    val accessToken: String,
    val refreshToken: String?,
    val expiresIn: Int
)

class OneDriveAuthService(private val context: Context) {
    private val client = OkHttpClient()

    private val clientId: String
        get() = context.getString(R.string.onedrive_client_id)

    private val tenant: String
        get() = context.getString(R.string.onedrive_tenant)

    private val scopes = "offline_access Files.ReadWrite.AppFolder User.Read"

    suspend fun requestDeviceCode(): DeviceCodeInfo = withContext(Dispatchers.IO) {
        ensureConfigured()
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("scope", scopes)
            .build()
        val request = Request.Builder()
            .url("https://login.microsoftonline.com/$tenant/oauth2/v2.0/devicecode")
            .post(body)
            .build()
        val response = client.newCall(request).execute()
        val raw = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            response.close()
            throw IOException(raw.ifBlank { "Failed to request device code" })
        }
        response.close()
        val json = JSONObject(raw)
        return@withContext DeviceCodeInfo(
            userCode = json.getString("user_code"),
            deviceCode = json.getString("device_code"),
            verificationUri = json.getString("verification_uri"),
            expiresIn = json.getInt("expires_in"),
            interval = json.optInt("interval", 5),
            message = json.optString("message", "")
        )
    }

    suspend fun pollForToken(deviceCodeInfo: DeviceCodeInfo): TokenResult = withContext(Dispatchers.IO) {
        ensureConfigured()
        val deadline = System.currentTimeMillis() + deviceCodeInfo.expiresIn * 1000L
        var interval = deviceCodeInfo.interval.coerceAtLeast(5)
        while (System.currentTimeMillis() < deadline) {
            val result = requestToken(
                mapOf(
                    "client_id" to clientId,
                    "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                    "device_code" to deviceCodeInfo.deviceCode
                )
            )
            if (result.first != null) {
                return@withContext result.first!!
            }
            val error = result.second
            when (error) {
                "authorization_pending" -> {
                    delay(interval * 1000L)
                }
                "slow_down" -> {
                    interval += 5
                    delay(interval * 1000L)
                }
                "expired_token" -> throw IOException("Device code expired")
                else -> throw IOException(error ?: "Failed to authorize")
            }
        }
        throw IOException("Device code expired")
    }

    suspend fun refreshAccessToken(refreshToken: String): TokenResult = withContext(Dispatchers.IO) {
        ensureConfigured()
        val result = requestToken(
            mapOf(
                "client_id" to clientId,
                "grant_type" to "refresh_token",
                "refresh_token" to refreshToken
            )
        )
        return@withContext result.first ?: throw IOException(result.second ?: "Failed to refresh token")
    }

    private fun requestToken(params: Map<String, String>): Pair<TokenResult?, String?> {
        val bodyBuilder = FormBody.Builder()
        params.forEach { (key, value) -> bodyBuilder.add(key, value) }
        val request = Request.Builder()
            .url("https://login.microsoftonline.com/$tenant/oauth2/v2.0/token")
            .post(bodyBuilder.build())
            .build()
        val response = client.newCall(request).execute()
        val raw = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            response.close()
            val error = runCatching { JSONObject(raw).optString("error") }.getOrNull()
            return Pair(null, error ?: raw.ifBlank { "Token request failed" })
        }
        response.close()
        val json = JSONObject(raw)
        return Pair(
            TokenResult(
                accessToken = json.getString("access_token"),
                refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() },
                expiresIn = json.getInt("expires_in")
            ),
            null
        )
    }

    private fun ensureConfigured() {
        if (clientId.isBlank() || clientId == "YOUR_CLIENT_ID") {
            throw IOException("OneDrive client ID is not configured")
        }
    }
}
