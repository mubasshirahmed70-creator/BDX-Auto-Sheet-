package com.example.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object MailGenClient {

    private const val BASE_URL = "https://mailgen.shop/api/get-inbox"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    /**
     * Executes POST /api/get-inbox with data and mode.
     */
    suspend fun getInbox(
        data: String,
        mode: String = "oauth"
    ): MailInboxResult = withContext(Dispatchers.IO) {
        val trimmedData = data.trim()
        val detectedEmail = MailDataParser.extractEmail(trimmedData) ?: ""

        val requestJson = JSONObject().apply {
            put("data", trimmedData)
            put("mode", mode)
        }

        val requestBody = requestJson.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(BASE_URL)
            .post(requestBody)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("User-Agent", "BDX-AutoSheet/1.0")
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val responseBodyStr = response.body?.string().orEmpty()
                val statusCode = response.code

                if (!response.isSuccessful && responseBodyStr.isBlank()) {
                    return@withContext MailInboxResult(
                        email = detectedEmail,
                        isSuccess = false,
                        error = "Server HTTP error $statusCode",
                        mailSource = mode,
                        messages = emptyList()
                    )
                }

                try {
                    val json = JSONObject(responseBodyStr)

                    val error = if (json.has("error") && !json.isNull("error")) {
                        json.optString("error")
                    } else null

                    val success = if (json.has("success")) {
                        json.optBoolean("success", true)
                    } else {
                        error == null
                    }

                    val email = if (json.has("email") && !json.isNull("email")) {
                        json.optString("email")
                    } else detectedEmail

                    val mailSource = json.optString("mailSource", mode)

                    val messagesList = mutableListOf<MailMessage>()
                    val messagesArray = json.optJSONArray("messages")
                    if (messagesArray != null) {
                        for (i in 0 until messagesArray.length()) {
                            val msgObj = messagesArray.optJSONObject(i) ?: continue
                            val uid = msgObj.optString("uid", "")
                            val from = msgObj.optString("from", "")
                            val subject = msgObj.optString("subject", "")
                            val date = msgObj.optString("date", "")
                            val message = if (msgObj.has("message")) msgObj.optString("message", "") else msgObj.optString("preview", "")
                            val code = if (msgObj.has("code") && !msgObj.isNull("code")) msgObj.optString("code") else null

                            messagesList.add(
                                MailMessage(
                                    uid = uid,
                                    from = from,
                                    subject = subject,
                                    date = date,
                                    message = message,
                                    code = code
                                )
                            )
                        }
                    }

                    MailInboxResult(
                        email = email,
                        isSuccess = success && error.isNullOrBlank(),
                        error = error,
                        mailSource = mailSource,
                        messages = messagesList
                    )
                } catch (e: Exception) {
                    MailInboxResult(
                        email = detectedEmail,
                        isSuccess = false,
                        error = "Failed to parse response: ${e.message ?: "Unknown error"}",
                        mailSource = mode,
                        messages = emptyList()
                    )
                }
            }
        } catch (e: IOException) {
            MailInboxResult(
                email = detectedEmail,
                isSuccess = false,
                error = "Network connection failed: ${e.message}",
                mailSource = mode,
                messages = emptyList()
            )
        } catch (e: Exception) {
            MailInboxResult(
                email = detectedEmail,
                isSuccess = false,
                error = "Unexpected error: ${e.message}",
                mailSource = mode,
                messages = emptyList()
            )
        }
    }
}
