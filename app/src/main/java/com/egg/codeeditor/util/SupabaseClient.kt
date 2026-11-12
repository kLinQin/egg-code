package com.egg.codeeditor.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class SupabaseUser(
    val id: String,
    val email: String?,
    val name: String?,
    val role: String
)

data class SupabaseNote(
    val id: Long,
    val title: String,
    val category: String,
    val mainCategory: String,
    val updatedAt: String,
    val color: String?,
    val isPublic: Boolean
)

object SupabaseClient {
    private const val SUPABASE_URL = "https://oeqqzkjxrphrsmgosxxq.supabase.co"
    private const val ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im9lcXF6a2p4cnBocnNtZ29zeHhxIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODUyNDQ1MjksImV4cCI6MjEwMDgyMDUyOX0.H8RI8YhyFGd_WHxb0UALNTeuTPjNWWAkmznDH2rac_0"

    private const val PREFS = "egg_supabase"
    private const val KEY_ACCESS = "access_token"
    private const val KEY_REFRESH = "refresh_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_EMAIL = "email"
    private const val KEY_NAME = "name"
    private const val KEY_ROLE = "role"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    @Volatile private var appContext: Context? = null

    fun init(context: Context) { appContext = context.applicationContext }

    var currentUser: SupabaseUser? = null
        private set

    private fun prefs() = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun jsonBody(o: JSONObject) = o.toString().toRequestBody("application/json".toMediaType())

    private fun requestBuilder(path: String, method: String, body: RequestBody?, auth: Boolean): Request.Builder {
        val b = Request.Builder().url(SUPABASE_URL + path)
        when (method) {
            "GET" -> b.get()
            "POST" -> b.post(body ?: jsonBody(JSONObject()))
            "PATCH" -> b.patch(body!!)
            "DELETE" -> b.delete(body)
        }
        b.header("apikey", ANON_KEY)
        b.header("Content-Type", "application/json")
        val tok = accessToken()
        if (auth && tok != null) b.header("Authorization", "Bearer $tok")
        return b
    }

    private fun execute(req: Request.Builder): Result<JSONObject> = runCatching {
        client.newCall(req.build()).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}: ${body.take(200)}")
            if (body.isBlank()) JSONObject() else JSONObject(body)
        }
    }

    private fun accessToken(): String? = prefs()?.getString(KEY_ACCESS, null)

    private fun parseUser(o: JSONObject): SupabaseUser {
        val meta = o.optJSONObject("user_metadata") ?: JSONObject()
        val appMeta = o.optJSONObject("app_metadata") ?: JSONObject()
        val name = meta.optString("name").ifBlank { null }
        val role = if (appMeta.optString("role") == "admin") "admin" else "visitor"
        return SupabaseUser(o.optString("id"), o.optString("email").ifBlank { null }, name, role)
    }

    private fun applySession(o: JSONObject) {
        val access = o.optString("access_token")
        val refresh = o.optString("refresh_token")
        val expiresIn = o.optLong("expires_in", 3600)
        val u = o.optJSONObject("user")
        val e = prefs()?.edit() ?: return
        if (access.isNotBlank()) e.putString(KEY_ACCESS, access)
        if (refresh.isNotBlank()) e.putString(KEY_REFRESH, refresh)
        e.putLong("expires_at", System.currentTimeMillis() + expiresIn * 1000)
        if (u != null) {
            val user = parseUser(u)
            currentUser = user
            e.putString(KEY_USER_ID, user.id)
            e.putString(KEY_EMAIL, user.email ?: "")
            e.putString(KEY_NAME, user.name ?: "")
            e.putString(KEY_ROLE, user.role)
        } else {
            val id = prefs()?.getString(KEY_USER_ID, "") ?: ""
            currentUser = if (id.isBlank()) null else SupabaseUser(
                id,
                prefs()?.getString(KEY_EMAIL, ""),
                prefs()?.getString(KEY_NAME, "")?.ifBlank { null },
                prefs()?.getString(KEY_ROLE, "visitor") ?: "visitor"
            )
        }
        e.apply()
    }

    suspend fun restoreSession(): Boolean = withContext(Dispatchers.IO) {
        val rt = prefs()?.getString(KEY_REFRESH, null) ?: return@withContext false
        val body = JSONObject().put("refresh_token", rt)
        val resp = execute(requestBuilder("/auth/v1/token?grant_type=refresh_token", "POST", jsonBody(body), auth = false))
        resp.mapCatching { o -> applySession(o); currentUser }.getOrNull() != null
    }

    suspend fun signInWithPassword(email: String, password: String): Result<SupabaseUser> = withContext(Dispatchers.IO) {
        val body = JSONObject().put("email", email).put("password", password)
        execute(requestBuilder("/auth/v1/token?grant_type=password", "POST", jsonBody(body), auth = false))
            .mapCatching { o -> applySession(o); currentUser!! }
    }

    suspend fun signUp(email: String, password: String, name: String): Result<SupabaseUser> = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("email", email)
            .put("password", password)
            .put("data", JSONObject().put("name", name))
        execute(requestBuilder("/auth/v1/signup", "POST", jsonBody(body), auth = false))
            .mapCatching { o ->
                val sess = o.optJSONObject("session")
                if (sess != null) { applySession(sess); currentUser!! }
                else throw IOException("Email verification required")
            }
    }

    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        val r = execute(requestBuilder("/auth/v1/logout", "POST", jsonBody(JSONObject()), auth = true))
        prefs()?.edit()?.clear()?.apply()
        currentUser = null
        r.mapCatching { Unit }
    }

    suspend fun listNotes(): Result<List<SupabaseNote>> = withContext(Dispatchers.IO) {
        val sel = "id,title,category,main_category,updated_at,color,is_public"
        val url = "/rest/v1/notes?select=$sel&order=is_pinned.desc,created_at.desc&limit=100"
        val req = requestBuilder(url, "GET", null, auth = currentUser != null)
        runCatching {
            client.newCall(req.build()).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}: ${body.take(200)}")
                val arr = JSONArray(body)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    SupabaseNote(
                        o.optLong("id"),
                        o.optString("title"),
                        o.optString("category"),
                        o.optString("main_category"),
                        o.optString("updated_at"),
                        o.optString("color").ifBlank { null },
                        o.optBoolean("is_public")
                    )
                }
            }
        }
    }

    suspend fun getNoteContent(id: Long): String? = withContext(Dispatchers.IO) {
        val url = "/rest/v1/notes?select=content&id=eq.$id"
        runCatching {
            client.newCall(requestBuilder(url, "GET", null, auth = currentUser != null).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val arr = JSONArray(resp.body?.string() ?: "[]")
                if (arr.length() == 0) null else arr.getJSONObject(0).optString("content")
            }
        }.getOrNull()
    }

    suspend fun updateNoteContent(id: Long, content: String): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject().put("content", content)
        val url = "/rest/v1/notes?id=eq.$id"
        val req = requestBuilder(url, "PATCH", jsonBody(body), auth = true).header("Prefer", "return=minimal").build()
        runCatching { client.newCall(req).execute().use { it.isSuccessful } }.getOrDefault(false)
    }

    fun isSupabaseNote(uri: Uri): Boolean = uri.scheme == "supabase"

    fun noteIdFromUri(uri: Uri): Long? = uri.path?.substringAfterLast('/')?.toLongOrNull()
}
