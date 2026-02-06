package org.yatt.app.data.remote

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

data class OneDriveMetadata(
    val eTag: String?,
    val downloadUrl: String?
)

class OneDriveClient {
    private val client = OkHttpClient()
    private val baseUrl = "https://graph.microsoft.com/v1.0"
    private val fileName = "yatt-sync.json"

    fun fetchMetadata(accessToken: String): OneDriveMetadata? {
        val request = Request.Builder()
            .url("$baseUrl/me/drive/special/approot:/$fileName")
            .addHeader("Authorization", "Bearer $accessToken")
            .build()
        val response = client.newCall(request).execute()
        if (response.code == 404) {
            response.close()
            return null
        }
        val raw = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            response.close()
            throw IOException(raw.ifBlank { "Failed to fetch OneDrive metadata" })
        }
        response.close()
        val json = JSONObject(raw)
        return OneDriveMetadata(
            eTag = json.optString("eTag"),
            downloadUrl = json.optString("@microsoft.graph.downloadUrl")
        )
    }

    fun download(downloadUrl: String): String {
        val request = Request.Builder().url(downloadUrl).build()
        val response = client.newCall(request).execute()
        val raw = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            response.close()
            throw IOException(raw.ifBlank { "Failed to download OneDrive data" })
        }
        response.close()
        return raw
    }

    fun upload(accessToken: String, content: String, eTag: String?): OneDriveMetadata {
        val request = Request.Builder()
            .url("$baseUrl/me/drive/special/approot:/$fileName:/content")
            .addHeader("Authorization", "Bearer $accessToken")
            .apply {
                if (!eTag.isNullOrBlank()) {
                    addHeader("If-Match", eTag)
                }
            }
            .put(content.toRequestBody("application/json".toMediaType()))
            .build()
        val response = client.newCall(request).execute()
        val raw = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            response.close()
            throw IOException(raw.ifBlank { "Failed to upload OneDrive data" })
        }
        response.close()
        val json = JSONObject(raw)
        return OneDriveMetadata(
            eTag = json.optString("eTag"),
            downloadUrl = json.optString("@microsoft.graph.downloadUrl")
        )
    }
}
