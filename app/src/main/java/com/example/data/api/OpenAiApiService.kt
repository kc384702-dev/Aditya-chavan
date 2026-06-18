package com.example.data.api

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

// Gemini API DTOs
data class GeminiPart(val text: String?)
data class GeminiContent(val role: String, val parts: List<GeminiPart>)
data class GeminiSystemInstruction(val parts: List<GeminiPart>)
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiSystemInstruction? = null
)

data class GeminiResponse(val candidates: List<GeminiCandidate>?)
data class GeminiCandidate(val content: GeminiContent?)

data class GeminiErrorDetail(val code: Int?, val message: String?, val status: String?)
data class GeminiErrorWrapper(val error: GeminiErrorDetail?)

class OpenAiApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val geminiClient = client.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    // Data Transfer Objects for JSON serialization
    data class ChatMessageDto(val role: String, val content: String)
    data class ChatCompletionRequest(val model: String, val messages: List<ChatMessageDto>)
    data class ChatChoiceDto(val index: Int?, val message: ChatMessageDto?, val finish_reason: String?)
    data class ChatCompletionResponse(val id: String?, val choices: List<ChatChoiceDto>?, val error: ErrorDto?)
    data class ErrorDto(val message: String?, val type: String?, val code: String?)

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun getChatCompletion(
        baseUrl: String,
        apiKey: String,
        modelName: String,
        messages: List<ChatMessageDto>
    ): String = withContext(Dispatchers.IO) {
        
        // If API Key is blank and we are using standard OpenAI configurations, fallback to Gemini!
        val isDefaultOpenAiWithNoKey = apiKey.isBlank() && 
            (baseUrl.contains("api.openai.com") || baseUrl.isBlank() || modelName == "gpt-4o-mini")
        
        if (isDefaultOpenAiWithNoKey) {
            Log.d("OpenAiApiService", "Default OpenAI settings with blank key detected. Falling back to Gemini.")
            return@withContext callGeminiApi(messages)
        }

        val cleanBaseUrl = baseUrl.trim()
        val finalUrl = when {
            cleanBaseUrl.endsWith("/chat/completions") -> cleanBaseUrl
            cleanBaseUrl.endsWith("/") -> "${cleanBaseUrl}chat/completions"
            else -> "${cleanBaseUrl}/chat/completions"
        }

        val requestBodyObj = ChatCompletionRequest(
            model = modelName,
            messages = messages
        )

        val requestAdapter = moshi.adapter(ChatCompletionRequest::class.java)
        val jsonBody = requestAdapter.toJson(requestBodyObj)

        Log.d("OpenAiApiService", "Sending request to $finalUrl with body: $jsonBody")

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = jsonBody.toRequestBody(mediaType)

        val requestBuilder = Request.Builder()
            .url(finalUrl)
            .post(body)
            .addHeader("Content-Type", "application/json")

        if (apiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $apiKey")
        }

        val request = requestBuilder.build()

        try {
            client.newCall(request).execute().use { response ->
                val responseString = response.body?.string() ?: ""
                Log.d("OpenAiApiService", "Response Code: ${response.code}, Body: $responseString")

                if (!response.isSuccessful) {
                    // Try parsing error
                    val errorAdapter = moshi.adapter(ChatCompletionResponse::class.java)
                    val errorResp = try { errorAdapter.fromJson(responseString) } catch (e: Exception) { null }
                    val errorMsg = errorResp?.error?.message ?: response.message
                    throw IOException("HTTP error ${response.code}: $errorMsg")
                }

                val responseAdapter = moshi.adapter(ChatCompletionResponse::class.java)
                val chatResponse = responseAdapter.fromJson(responseString)
                
                val content = chatResponse?.choices?.firstOrNull()?.message?.content
                if (content != null) {
                    return@withContext content
                } else {
                    val errMsg = chatResponse?.error?.message ?: "Received empty completions choice."
                    throw IOException(errMsg)
                }
            }
        } catch (e: IOException) {
            Log.e("OpenAiApiService", "Network request failed: ${e.message}", e)
            throw e
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun callGeminiApi(messages: List<ChatMessageDto>): String = withContext(Dispatchers.IO) {
        val systemMessages = messages.filter { it.role == "system" }
        val chatMessages = messages.filter { it.role != "system" }

        val geminiContents = chatMessages.map { msg ->
            val mappedRole = if (msg.role == "assistant") "model" else "user"
            GeminiContent(
                role = mappedRole,
                parts = listOf(GeminiPart(text = msg.content))
            )
        }

        val systemInstruction = if (systemMessages.isNotEmpty()) {
            val combinedSystemPrompt = systemMessages.joinToString("\n") { it.content }
            GeminiSystemInstruction(parts = listOf(GeminiPart(text = combinedSystemPrompt)))
        } else {
            null
        }

        val geminiRequest = GeminiRequest(
            contents = geminiContents,
            systemInstruction = systemInstruction
        )

        val requestAdapter = moshi.adapter(GeminiRequest::class.java)
        val jsonPayload = requestAdapter.toJson(geminiRequest)

        val geminiApiKey = try {
            com.example.BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }

        if (geminiApiKey.isBlank() || geminiApiKey == "MY_GEMINI_API_KEY") {
            throw IOException("Gemini API Key is missing. Please configure either your Gemini API Key in the panel, or configure your OpenAI Endpoint and API keys in settings.")
        }

        // Candidates: low-latency flash lite, standard model, and high performance pro preview
        val modelCandidates = listOf(
            "gemini-3.1-flash-lite-preview",
            "gemini-3.5-flash",
            "gemini-3.1-pro-preview"
        )
        
        var lastException: Exception? = null
        for (modelName in modelCandidates) {
            try {
                Log.d("OpenAiApiService", "Attempting request using Gemini Model: $modelName")
                return@withContext executeGeminiCall(modelName, jsonPayload, geminiApiKey)
            } catch (e: Exception) {
                lastException = e
                Log.w("OpenAiApiService", "Gemini Model $modelName failed. Exception description: ${e.message}")
                
                // Do not retry on non-transient, authorization or developer/syntax errors (e.g., 400 Bad Request, 403 Forbidden)
                val errMsg = e.message ?: ""
                val isTransient = errMsg.contains("503") || 
                                  errMsg.contains("504") || 
                                  errMsg.contains("429") || 
                                  e is IOException
                if (!isTransient && (errMsg.contains("400") || errMsg.contains("401") || errMsg.contains("403"))) {
                    throw e
                }
            }
        }
        
        throw lastException ?: IOException("Failed to connect to Gemini API models.")
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun executeGeminiCall(
        modelName: String,
        jsonPayload: String,
        geminiApiKey: String
    ): String = withContext(Dispatchers.IO) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$geminiApiKey"
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonPayload.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()

        geminiClient.newCall(request).execute().use { response ->
            val responseString = response.body?.string() ?: ""
            Log.d("OpenAiApiService", "Gemini response code for model $modelName: ${response.code}")

            if (!response.isSuccessful) {
                val parsedMsg = try {
                    val errorAdapter = moshi.adapter(GeminiErrorWrapper::class.java)
                    val errorObj = errorAdapter.fromJson(responseString)
                    errorObj?.error?.message
                } catch (e: Exception) {
                    null
                }
                
                val displayMsg = when {
                    !parsedMsg.isNullOrBlank() -> parsedMsg
                    response.code == 503 -> "This model is currently experiencing high demand. Spikes in demand are usually temporary. Please try again shortly."
                    response.code == 504 -> "Gateway timeout. The server took too long to answer. Spikes in demand are usually temporary."
                    response.code == 429 -> "Rate limit exceeded. You have made too many requests in a short period. Please try again in a few moments."
                    else -> responseString.substring(0, minOf(responseString.length, 120))
                }
                throw IOException("Gemini API Error ${response.code}: $displayMsg")
            }

            val responseAdapter = moshi.adapter(GeminiResponse::class.java)
            val geminiResponse = responseAdapter.fromJson(responseString)
            val replyText = geminiResponse?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            
            if (replyText != null) {
                return@withContext replyText
            } else {
                throw IOException("No valid text returned from the model.")
            }
        }
    }
}
