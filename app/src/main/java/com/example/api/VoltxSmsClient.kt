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

data class VoltxAllocatedNumber(
    val fullNumber: String,
    val nationalNumber: String,
    val noPlusNumber: String,
    val country: String,
    val operator: String,
    val rangeRid: String,
    val allocatedAt: Long = System.currentTimeMillis(),
    val otpCode: String? = null,
    val otpMessage: String? = null,
    val otpTime: Long? = null
)

data class VoltxServiceRange(
    val sid: String,
    val lastAt: Long,
    val ranges: List<String>
) {
    val isFacebook: Boolean
        get() = sid.contains("facebook", ignoreCase = true) ||
                sid.equals("fb", ignoreCase = true) ||
                sid.contains("meta", ignoreCase = true)
}

data class VoltxOtpItem(
    val otpId: String,
    val number: String,
    val message: String,
    val time: Long,
    val extractedCode: String?
)

sealed class VoltxApiResult<out T> {
    data class Success<T>(val data: T, val message: String = "ok") : VoltxApiResult<T>()
    data class Error(val code: Int, val message: String) : VoltxApiResult<Nothing>()
}

object VoltxSmsClient {

    private const val BASE_URL = "https://api.2oo9.cloud/MXS47FLFX0U/tnevs/@public/api"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val OTP_REGEX_LIST = listOf(
        Regex("""(?:FB-|G-)(\d{4,8})""", RegexOption.IGNORE_CASE),
        Regex("""\b(\d{5,8})\b"""),
        Regex("""\b(\d{4})\b""")
    )

    /**
     * Strips trailing 'X' or spaces from a range string (e.g. "22501XXX" -> "22501")
     * as required by POST /getnum.
     */
    fun cleanRangeId(rawRange: String): String {
        return rawRange.trim()
            .replace(Regex("[xX]+$"), "")
            .trim()
    }

    /**
     * Extracts 4-8 digit OTP verification code from SMS body.
     */
    fun extractOtpCode(message: String): String? {
        for (regex in OTP_REGEX_LIST) {
            val match = regex.find(message)
            if (match != null) {
                return match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() } ?: match.value
            }
        }
        return null
    }

    /**
     * 1. POST /getnum
     * Allocates a number from the given range (rid).
     */
    suspend fun allocateNumber(
        apiKey: String,
        rawRid: String
    ): VoltxApiResult<VoltxAllocatedNumber> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        val rid = cleanRangeId(rawRid)

        if (cleanKey.isEmpty()) {
            return@withContext VoltxApiResult.Error(-1, "API Key (mauthapi) সেট করা নেই!")
        }
        if (rid.isEmpty()) {
            return@withContext VoltxApiResult.Error(-1, "রেঞ্জ (rid) সিলেক্ট বা টাইপ করুন!")
        }

        val bodyJson = JSONObject().apply {
            put("rid", rid)
        }

        val request = Request.Builder()
            .url("$BASE_URL/getnum")
            .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("mauthapi", cleanKey)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (bodyStr.isBlank()) {
                    return@withContext VoltxApiResult.Error(response.code, "Empty response (HTTP ${response.code})")
                }

                val json = JSONObject(bodyStr)
                val meta = json.optJSONObject("meta")
                val metaCode = meta?.optInt("code", response.code) ?: response.code
                val metaStatus = meta?.optString("status", "") ?: ""
                val message = json.optString("message", metaStatus)

                if (metaCode == 2946 || metaStatus.equals("not_found", ignoreCase = true)) {
                    return@withContext VoltxApiResult.Error(2946, "Out of stock (এই রেঞ্জে নাম্বার শেষ)")
                }

                val dataObj = json.optJSONObject("data")
                if (metaCode != 200 || dataObj == null) {
                    return@withContext VoltxApiResult.Error(
                        metaCode,
                        message.ifBlank { "Failed to get number (Code $metaCode)" }
                    )
                }

                val fullNum = dataObj.optString("full_number", "")
                val nationalNum = dataObj.optString("national_number", "")
                val noPlusNum = dataObj.optString("no_plus_number", fullNum.removePrefix("+"))
                val country = dataObj.optString("country", "")
                val operator = dataObj.optString("operator", "")

                VoltxApiResult.Success(
                    data = VoltxAllocatedNumber(
                        fullNumber = fullNum.ifBlank { "+$noPlusNum" },
                        nationalNumber = nationalNum.ifBlank { noPlusNum },
                        noPlusNumber = noPlusNum.ifBlank { fullNum.removePrefix("+") },
                        country = country,
                        operator = operator,
                        rangeRid = rid
                    ),
                    message = message
                )
            }
        } catch (e: IOException) {
            VoltxApiResult.Error(-2, "Network error: ${e.message}")
        } catch (e: Exception) {
            VoltxApiResult.Error(-3, "Parse error: ${e.message}")
        }
    }

    /**
     * 2. GET /liveaccess
     * Fetches active services and their hit ranges, sorted with Facebook first!
     */
    suspend fun getLiveAccessRanges(
        apiKey: String
    ): VoltxApiResult<List<VoltxServiceRange>> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            return@withContext VoltxApiResult.Error(-1, "API Key (mauthapi) সেট করা নেই!")
        }

        val request = Request.Builder()
            .url("$BASE_URL/liveaccess")
            .get()
            .header("mauthapi", cleanKey)
            .header("Accept", "application/json")
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (bodyStr.isBlank()) {
                    return@withContext VoltxApiResult.Error(response.code, "Empty response (HTTP ${response.code})")
                }

                val json = JSONObject(bodyStr)
                val meta = json.optJSONObject("meta")
                val metaCode = meta?.optInt("code", response.code) ?: response.code
                val message = json.optString("message", "")

                val dataObj = json.optJSONObject("data")
                if (metaCode != 200 || dataObj == null) {
                    return@withContext VoltxApiResult.Error(
                        metaCode,
                        message.ifBlank { "Error loading ranges (Code $metaCode)" }
                    )
                }

                val servicesArray = dataObj.optJSONArray("services")
                val resultList = mutableListOf<VoltxServiceRange>()
                if (servicesArray != null) {
                    for (i in 0 until servicesArray.length()) {
                        val item = servicesArray.optJSONObject(i) ?: continue
                        val sid = item.optString("sid", "Unknown")
                        val lastAt = item.optLong("last_at", 0L)
                        val rangesArr = item.optJSONArray("ranges")
                        val ranges = mutableListOf<String>()
                        if (rangesArr != null) {
                            for (j in 0 until rangesArr.length()) {
                                val r = rangesArr.optString(j, "").trim()
                                if (r.isNotEmpty()) ranges.add(r)
                            }
                        }
                        if (ranges.isNotEmpty()) {
                            resultList.add(VoltxServiceRange(sid = sid, lastAt = lastAt, ranges = ranges))
                        }
                    }
                }

                // Sort so Facebook services appear at the very top, then by most recent lastAt
                val sorted = resultList.sortedWith(
                    compareByDescending<VoltxServiceRange> { it.isFacebook }
                        .thenByDescending { it.lastAt }
                )

                VoltxApiResult.Success(sorted, message)
            }
        } catch (e: IOException) {
            VoltxApiResult.Error(-2, "Network error: ${e.message}")
        } catch (e: Exception) {
            VoltxApiResult.Error(-3, "Parse error: ${e.message}")
        }
    }

    /**
     * 3. GET /success-otp
     * Fetches the user's last 50 successful OTPs.
     */
    suspend fun getSuccessOtps(
        apiKey: String
    ): VoltxApiResult<List<VoltxOtpItem>> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            return@withContext VoltxApiResult.Error(-1, "API Key (mauthapi) সেট করা নেই!")
        }

        val request = Request.Builder()
            .url("$BASE_URL/success-otp")
            .get()
            .header("mauthapi", cleanKey)
            .header("Accept", "application/json")
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (bodyStr.isBlank()) {
                    return@withContext VoltxApiResult.Error(response.code, "Empty response (HTTP ${response.code})")
                }

                val json = JSONObject(bodyStr)
                val meta = json.optJSONObject("meta")
                val metaCode = meta?.optInt("code", response.code) ?: response.code
                val message = json.optString("message", "")

                val dataObj = json.optJSONObject("data")
                if (metaCode != 200 || dataObj == null) {
                    return@withContext VoltxApiResult.Error(
                        metaCode,
                        message.ifBlank { "Error checking OTP (Code $metaCode)" }
                    )
                }

                val otpsArray = dataObj.optJSONArray("otps")
                val otpList = mutableListOf<VoltxOtpItem>()
                if (otpsArray != null) {
                    for (i in 0 until otpsArray.length()) {
                        val obj = otpsArray.optJSONObject(i) ?: continue
                        val otpId = obj.optString("otp_id", "")
                        val number = obj.optString("number", "")
                        val msg = obj.optString("message", "")
                        val time = obj.optLong("time", 0L)
                        val code = extractOtpCode(msg)

                        if (number.isNotBlank() || msg.isNotBlank()) {
                            otpList.add(
                                VoltxOtpItem(
                                    otpId = otpId,
                                    number = number,
                                    message = msg,
                                    time = time,
                                    extractedCode = code
                                )
                            )
                        }
                    }
                }

                VoltxApiResult.Success(otpList, message)
            }
        } catch (e: IOException) {
            VoltxApiResult.Error(-2, "Network error: ${e.message}")
        } catch (e: Exception) {
            VoltxApiResult.Error(-3, "Parse error: ${e.message}")
        }
    }
}
