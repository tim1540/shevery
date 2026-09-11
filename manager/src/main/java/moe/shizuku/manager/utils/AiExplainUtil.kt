package moe.shizuku.manager.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.shizuku.manager.module.ModuleSettings
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object AiExplainUtil {

    suspend fun explainFailure(
        contextStr: String,
        inputDetail: String,
        outputLog: String,
        apiKey: String
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext "Google AI Studio API Key is empty! Please configure it in Shevery Settings."
        }
        try {
            val selectedModel = ModuleSettings.getComputGeminiModel()
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$selectedModel:generateContent?key=$apiKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")

            val currentLocale = java.util.Locale.getDefault()
            val prompt = "CRITICAL: You must write the entire explanation in the following language: ${currentLocale.displayName} (locale code: ${currentLocale.toLanguageTag()}).\n\n" +
                    "An error or failure occurred in the application context: $contextStr.\n" +
                    "Input / Action details:\n$inputDetail\n\n" +
                    "Output / Error Log:\n$outputLog\n\n" +
                    "Explain this failure in a clear, concise, and helpful developer-focused way, and suggest how to resolve it."

            val requestBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                })
            }

            conn.outputStream.use { os ->
                os.write(requestBody.toString().toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                val text = json.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                text.trim()
            } else {
                val errText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "No details."
                "Gemini API returned error code $responseCode: $errText"
            }
        } catch (e: Exception) {
            "Failed to reach Gemini API: ${e.message ?: "Connection error."}"
        }
    }

    suspend fun generateCommand(
        prompt: String,
        apiKey: String
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext "Error: API Key is empty!"
        }
        try {
            val selectedModel = ModuleSettings.getComputGeminiModel()
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$selectedModel:generateContent?key=$apiKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")

            val requestPrompt = "You are a shell command assistant. Generate a shell command based on the following user prompt.\n" +
                    "CRITICAL: Return ONLY the raw shell command, without any markdown formatting (do not wrap in ``` or `), explanations, or trailing text. The output should be directly executable in a shell.\n\n" +
                    "Prompt: $prompt"

            val requestBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", requestPrompt)
                            })
                        })
                    })
                })
            }

            conn.outputStream.use { os ->
                os.write(requestBody.toString().toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                val text = json.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                text.trim()
            } else {
                val errText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "No details."
                "Error: Gemini API returned error code $responseCode: $errText"
            }
        } catch (e: Exception) {
            "Error: Failed to reach Gemini API: ${e.message ?: "Connection error."}"
        }
    }

    suspend fun fetchAvailableFlashModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext emptyList()
        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            if (conn.responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                val modelsArray = json.optJSONArray("models") ?: return@withContext emptyList()
                val flashModels = mutableListOf<String>()

                for (i in 0 until modelsArray.length()) {
                    val modelObj = modelsArray.optJSONObject(i) ?: continue
                    val rawName = modelObj.optString("name", "")
                    val modelId = rawName.removePrefix("models/")
                    val supportedMethods = modelObj.optJSONArray("supportedGenerationMethods")
                    var supportsGenerateContent = false
                    if (supportedMethods != null) {
                        for (j in 0 until supportedMethods.length()) {
                            if (supportedMethods.optString(j) == "generateContent") {
                                supportsGenerateContent = true
                                break
                            }
                        }
                    }
                    if (!supportsGenerateContent) continue

                    val lower = modelId.lowercase()
                    if (lower.contains("flash") && !lower.contains("preview") && !lower.contains("exp")) {
                        flashModels.add(modelId)
                    }
                }

                flashModels.distinct().sortedWith { a, b ->
                    val vA = Regex("""gemini-(\d+(?:\.\d+)?)""").find(a)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                    val vB = Regex("""gemini-(\d+(?:\.\d+)?)""").find(b)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                    val cmp = vB.compareTo(vA)
                    if (cmp != 0) cmp else {
                        val aIsLite = if (a.contains("lite", ignoreCase = true) || a.contains("8b", ignoreCase = true)) 1 else 0
                        val bIsLite = if (b.contains("lite", ignoreCase = true) || b.contains("8b", ignoreCase = true)) 1 else 0
                        val liteCmp = aIsLite.compareTo(bIsLite)
                        if (liteCmp != 0) liteCmp else a.compareTo(b)
                    }
                }
            } else {
                emptyList()
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
