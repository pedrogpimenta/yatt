package org.yatt.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.yatt.app.data.SettingsStore
import org.yatt.app.data.local.TimerEntity
import org.yatt.app.data.model.Project
import org.yatt.app.data.model.SyncSession
import org.yatt.app.data.model.UserProfile
import java.io.IOException

class ApiService(private val settingsStore: SettingsStore) {
    private val client = OkHttpClient()

    suspend fun login(email: String, password: String): String {
        val payload = JSONObject()
            .put("email", email)
            .put("password", password)
        val response = requestJson("auth/login", "POST", payload)
        return response.getString("token")
    }

    suspend fun register(email: String, password: String): String {
        val payload = JSONObject()
            .put("email", email)
            .put("password", password)
        val response = requestJson("auth/register", "POST", payload)
        return response.getString("token")
    }

    suspend fun getMe(): UserProfile {
        val response = requestJson("auth/me", "GET")
        return UserProfile(
            id = response.getLong("id"),
            email = response.getString("email"),
            createdAt = response.getString("created_at")
        )
    }

    suspend fun getPreferences(): Int {
        val response = requestJson("auth/preferences", "GET")
        return response.optInt("dayStartHour", 0)
    }

    suspend fun updatePreferences(dayStartHour: Int) {
        val payload = JSONObject().put("dayStartHour", dayStartHour)
        requestJson("auth/preferences", "PATCH", payload)
    }

    suspend fun changePassword(currentPassword: String, newPassword: String) {
        val payload = JSONObject()
            .put("currentPassword", currentPassword)
            .put("newPassword", newPassword)
        requestJson("auth/change-password", "POST", payload)
    }

    suspend fun getTimers(): List<TimerEntity> {
        val response = requestArray("timers", "GET")
        return jsonToTimers(response)
    }

    suspend fun getTags(): List<String> {
        val response = requestArray("timers/tags", "GET")
        val result = ArrayList<String>(response.length())
        for (i in 0 until response.length()) {
            result.add(response.getString(i))
        }
        return result
    }

    suspend fun createTimer(
        startTime: String,
        endTime: String?,
        tag: String?,
        description: String? = null
    ): TimerEntity {
        val payload = JSONObject()
            .put("start_time", startTime)
            .apply {
                if (endTime != null) put("end_time", endTime)
                if (tag != null) put("tag", tag)
                if (description != null) put("description", description)
            }
        val response = requestJson("timers", "POST", payload)
        return jsonToTimer(response)
    }

    suspend fun updateTimer(id: String, startTime: String?, endTime: String?, tag: String?, description: String? = null): TimerEntity {
        val payload = JSONObject()
        if (startTime != null) payload.put("start_time", startTime)
        if (endTime != null) payload.put("end_time", endTime)
        if (tag != null) payload.put("tag", tag)
        // When doing a full update (start/end set) or explicitly clearing, send description (null clears it)
        if (startTime != null || endTime != null) {
            payload.put("description", description ?: JSONObject.NULL)
        } else if (description != null) {
            payload.put("description", description)
        }
        val response = requestJson("timers/$id", "PATCH", payload)
        return jsonToTimer(response)
    }

    suspend fun stopTimer(id: String): TimerEntity {
        val response = requestJson("timers/$id/stop", "POST")
        return jsonToTimer(response)
    }

    suspend fun deleteTimer(id: String) {
        requestJson("timers/$id", "DELETE")
    }

    suspend fun getProjects(): List<Project> {
        val response = requestArray("projects", "GET")
        val result = ArrayList<Project>(response.length())
        for (i in 0 until response.length()) {
            result.add(jsonToProject(response.getJSONObject(i)))
        }
        return result
    }

    suspend fun createProject(name: String, type: String?, clientName: String?, clientId: Long?): Project {
        val payload = JSONObject()
            .put("name", name)
            .apply {
                if (type != null) put("type", type)
                if (clientName != null) put("clientName", clientName)
                if (clientId != null) put("clientId", clientId)
            }
        val response = requestJson("projects", "POST", payload)
        return jsonToProject(response)
    }

    suspend fun updateProject(id: Long, name: String, type: String?, clientName: String?, clientId: Long?): Project {
        val payload = JSONObject()
            .put("name", name)
            .put("type", type ?: JSONObject.NULL)
            .put("clientName", clientName ?: JSONObject.NULL)
            .put("clientId", clientId ?: JSONObject.NULL)
        val response = requestJson("projects/$id", "PATCH", payload)
        return jsonToProject(response)
    }

    suspend fun deleteProject(id: Long) {
        requestJson("projects/$id", "DELETE")
    }

    suspend fun createSyncSession(deviceId: String, timers: List<TimerEntity>): SyncSession {
        val payload = JSONObject()
            .put("deviceId", deviceId)
            .put("timers", timersToJson(timers))
        val response = requestJson("sync/create", "POST", payload)
        return SyncSession(
            syncCode = response.getString("syncCode"),
            expiresInSeconds = response.optInt("expiresIn", 600)
        )
    }

    suspend fun joinSyncSession(syncCode: String, deviceId: String, timers: List<TimerEntity>): List<TimerEntity> {
        val payload = JSONObject()
            .put("syncCode", syncCode)
            .put("deviceId", deviceId)
            .put("timers", timersToJson(timers))
        val response = requestJson("sync/join", "POST", payload)
        val timersJson = response.optJSONArray("timers") ?: JSONArray()
        return jsonToTimers(timersJson)
    }

    suspend fun getSyncStatus(syncCode: String): Pair<String, List<TimerEntity>> {
        val response = requestJson("sync/status/$syncCode", "GET")
        val status = response.getString("status")
        val timersJson = response.optJSONArray("timers") ?: JSONArray()
        return status to jsonToTimers(timersJson)
    }

    private suspend fun requestJson(
        path: String,
        method: String,
        payload: JSONObject? = null
    ): JSONObject = withContext(Dispatchers.IO) {
        val response = execute(path, method, payload)
        if (response.isEmpty()) {
            return@withContext JSONObject()
        }
        return@withContext JSONObject(response)
    }

    private suspend fun requestArray(
        path: String,
        method: String,
        payload: JSONObject? = null
    ): JSONArray = withContext(Dispatchers.IO) {
        val response = execute(path, method, payload)
        if (response.isEmpty()) {
            return@withContext JSONArray()
        }
        return@withContext JSONArray(response)
    }

    private suspend fun execute(path: String, method: String, payload: JSONObject?): String {
        val baseUrl = settingsStore.preferencesFlow.first().apiBaseUrl.trimEnd('/')
        val token = settingsStore.authTokenFlow.first()
        val url = "$baseUrl/$path"
        val body = payload?.toString()?.toRequestBody("application/json; charset=utf-8".toMediaType())
        val requestBuilder = Request.Builder()
            .url(url)
            .method(method, when (method) {
                "POST", "PATCH", "PUT" -> body ?: "".toRequestBody("application/json; charset=utf-8".toMediaType())
                else -> null
            })

        if (!token.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        val request = requestBuilder.build()
        val response = client.newCall(request).execute()
        if (response.code == 401) {
            response.close()
            settingsStore.clearAuthToken()
            settingsStore.setLocalMode(false)
            throw ApiException("Unauthorized")
        }
        if (response.code == 204) {
            response.close()
            return ""
        }
        val responseBody = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            response.close()
            val errorMessage = runCatching { JSONObject(responseBody).optString("error") }.getOrNull()
            throw ApiException(errorMessage ?: "Request failed with ${response.code}")
        }
        response.close()
        return responseBody
    }

    private fun jsonToProject(json: JSONObject): Project {
        val id = json.getLong("id")
        val name = json.getString("name")
        val type = if (json.isNull("type")) null else json.optString("type").takeIf { it.isNotEmpty() }
        val clientId = if (json.isNull("client_id")) null else json.optLong("client_id").takeIf { it != 0L }
        val clientName = if (json.isNull("client_name")) null else json.optString("client_name").takeIf { it.isNotEmpty() }
        return Project(id = id, name = name, type = type, clientId = clientId, clientName = clientName)
    }

    private fun jsonToTimer(json: JSONObject): TimerEntity {
        val idValue = json.get("id").toString()
        val endTime = if (json.isNull("end_time")) null else json.getString("end_time")
        val tag = if (json.isNull("tag")) null else json.getString("tag")
        val description = if (json.isNull("description")) null else json.optString("description").takeIf { it.isNotEmpty() }
        val projectId = when {
            json.isNull("project_id") -> null
            else -> json.get("project_id").toString()
        }
        val projectName = if (json.isNull("project_name")) null else json.optString("project_name").takeIf { it.isNotEmpty() }
        val clientName = if (json.isNull("client_name")) null else json.optString("client_name").takeIf { it.isNotEmpty() }
        return TimerEntity(
            id = idValue,
            startTime = json.getString("start_time"),
            endTime = endTime,
            tag = tag,
            description = description,
            projectId = projectId,
            projectName = projectName,
            clientName = clientName
        )
    }

    private fun jsonToTimers(array: JSONArray): List<TimerEntity> {
        val result = ArrayList<TimerEntity>(array.length())
        for (i in 0 until array.length()) {
            result.add(jsonToTimer(array.getJSONObject(i)))
        }
        return result
    }

    private fun timersToJson(timers: List<TimerEntity>): JSONArray {
        val array = JSONArray()
        timers.forEach { timer ->
            val json = JSONObject()
                .put("id", timer.id)
                .put("start_time", timer.startTime)
                .put("end_time", timer.endTime)
                .put("tag", timer.tag)
                .apply { if (timer.description != null) put("description", timer.description) }
            array.put(json)
        }
        return array
    }
}

class ApiException(message: String) : IOException(message)
