package com.example.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
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

    // Dynamic cache of rangeRid -> country name learned from /getnum or /liveaccess
    private val learnedRangeCountryMap = ConcurrentHashMap<String, String>()

    // Comprehensive ITU-T E.164 international dialing prefix to Flag + Country Name map
    // Sorted by longest prefix first during lookup
    private val DIAL_CODE_COUNTRIES: List<Pair<String, String>> = listOf(
        // 4-digit NANP / Caribbean & specialized prefixes
        "1809" to "🇩🇴 Dominican",
        "1829" to "🇩🇴 Dominican",
        "1849" to "🇩🇴 Dominican",
        "1876" to "🇯🇲 Jamaica",
        "1658" to "🇯🇲 Jamaica",
        "1868" to "🇹🇹 Trinidad",
        "1246" to "🇧🇧 Barbados",
        "1242" to "🇧🇸 Bahamas",
        "1787" to "🇵🇷 Puerto Rico",
        "1939" to "🇵🇷 Puerto Rico",
        // 3-digit country dialing codes
        "880" to "🇧🇩 Bangladesh",
        "977" to "🇳🇵 Nepal",
        "998" to "🇺🇿 Uzbekistan",
        "996" to "🇰🇬 Kyrgyzstan",
        "992" to "🇹🇯 Tajikistan",
        "993" to "🇹🇲 Turkmenistan",
        "994" to "🇦🇿 Azerbaijan",
        "995" to "🇬🇪 Georgia",
        "374" to "🇦🇲 Armenia",
        "375" to "🇧🇾 Belarus",
        "380" to "🇺🇦 Ukraine",
        "373" to "🇲🇩 Moldova",
        "370" to "🇱🇹 Lithuania",
        "371" to "🇱🇻 Latvia",
        "372" to "🇪🇪 Estonia",
        "351" to "🇵🇹 Portugal",
        "352" to "🇱🇺 Luxembourg",
        "353" to "🇮🇪 Ireland",
        "354" to "🇮🇸 Iceland",
        "355" to "🇦🇱 Albania",
        "356" to "🇲🇹 Malta",
        "357" to "🇨🇾 Cyprus",
        "358" to "🇫🇮 Finland",
        "359" to "🇧🇬 Bulgaria",
        "381" to "🇷🇸 Serbia",
        "382" to "🇲🇪 Montenegro",
        "383" to "🇽🇰 Kosovo",
        "385" to "🇭🇷 Croatia",
        "386" to "🇸🇮 Slovenia",
        "387" to "🇧🇦 Bosnia",
        "389" to "🇲🇰 Macedonia",
        "420" to "🇨🇿 Czechia",
        "421" to "🇸🇰 Slovakia",
        "855" to "🇰🇭 Cambodia",
        "856" to "🇱🇦 Laos",
        "852" to "🇭🇰 Hong Kong",
        "853" to "🇲🇴 Macau",
        "886" to "🇹🇼 Taiwan",
        "960" to "🇲🇻 Maldives",
        "961" to "🇱🇧 Lebanon",
        "962" to "🇯🇴 Jordan",
        "963" to "🇸🇾 Syria",
        "964" to "🇮🇶 Iraq",
        "965" to "🇰🇼 Kuwait",
        "966" to "🇸🇦 Saudi Arabia",
        "967" to "🇾🇪 Yemen",
        "968" to "🇴🇲 Oman",
        "970" to "🇵🇸 Palestine",
        "971" to "🇦🇪 UAE",
        "972" to "🇮🇱 Israel",
        "973" to "🇧🇭 Bahrain",
        "974" to "🇶🇦 Qatar",
        "975" to "🇧🇹 Bhutan",
        "976" to "🇲🇳 Mongolia",
        // Africa 3-digit codes (very common in VOLTX SMS)
        "211" to "🇸🇸 S. Sudan",
        "212" to "🇲🇦 Morocco",
        "213" to "🇩🇿 Algeria",
        "216" to "🇹🇳 Tunisia",
        "218" to "🇱🇾 Libya",
        "220" to "🇬🇲 Gambia",
        "221" to "🇸🇳 Senegal",
        "222" to "🇲🇷 Mauritania",
        "223" to "🇲🇱 Mali",
        "224" to "🇬🇳 Guinea",
        "225" to "🇨🇮 Ivory Coast",
        "226" to "🇧🇫 Burkina Faso",
        "227" to "🇳🇪 Niger",
        "228" to "🇹🇬 Togo",
        "229" to "🇧🇯 Benin",
        "230" to "🇲🇺 Mauritius",
        "231" to "🇱🇷 Liberia",
        "232" to "🇸🇱 Sierra Leone",
        "233" to "🇬🇭 Ghana",
        "234" to "🇳🇬 Nigeria",
        "235" to "🇹🇩 Chad",
        "236" to "🇨🇫 CAR",
        "237" to "🇨🇲 Cameroon",
        "238" to "🇨🇻 Cape Verde",
        "239" to "🇸🇹 Sao Tome",
        "240" to "🇬🇶 Eq. Guinea",
        "241" to "🇬🇦 Gabon",
        "242" to "🇨🇬 Congo",
        "243" to "🇨🇩 DR Congo",
        "244" to "🇦🇴 Angola",
        "245" to "🇬🇼 Guinea-Bissau",
        "248" to "🇸🇨 Seychelles",
        "249" to "🇸🇩 Sudan",
        "250" to "🇷🇼 Rwanda",
        "251" to "🇪🇹 Ethiopia",
        "252" to "🇸🇴 Somalia",
        "253" to "🇩🇯 Djibouti",
        "254" to "🇰🇪 Kenya",
        "255" to "🇹🇿 Tanzania",
        "256" to "🇺🇬 Uganda",
        "257" to "🇧🇮 Burundi",
        "258" to "🇲🇿 Mozambique",
        "260" to "🇿🇲 Zambia",
        "261" to "🇲🇬 Madagascar",
        "262" to "🇷🇪 Reunion",
        "263" to "🇿🇼 Zimbabwe",
        "264" to "🇳🇦 Namibia",
        "265" to "🇲🇼 Malawi",
        "266" to "🇱🇸 Lesotho",
        "267" to "🇧🇼 Botswana",
        "268" to "🇸🇿 Eswatini",
        "269" to "🇰🇲 Comoros",
        "291" to "🇪🇷 Eritrea",
        // Latin America 3-digit codes
        "501" to "🇧🇿 Belize",
        "502" to "🇬🇹 Guatemala",
        "503" to "🇸🇻 El Salvador",
        "504" to "🇭🇳 Honduras",
        "505" to "🇳🇮 Nicaragua",
        "506" to "🇨🇷 Costa Rica",
        "507" to "🇵🇦 Panama",
        "509" to "🇭🇹 Haiti",
        "591" to "🇧🇴 Bolivia",
        "592" to "🇬🇾 Guyana",
        "593" to "🇪🇨 Ecuador",
        "595" to "🇵🇾 Paraguay",
        "597" to "🇸🇷 Suriname",
        "598" to "🇺🇾 Uruguay",
        "670" to "🇹🇱 Timor-Leste",
        "673" to "🇧🇳 Brunei",
        "675" to "🇵🇬 PNG",
        "679" to "🇫🇯 Fiji",
        // 2-digit country dialing codes
        "20" to "🇪🇬 Egypt",
        "27" to "🇿🇦 South Africa",
        "30" to "🇬🇷 Greece",
        "31" to "🇳🇱 Netherlands",
        "32" to "🇧🇪 Belgium",
        "33" to "🇫🇷 France",
        "34" to "🇪🇸 Spain",
        "36" to "🇭🇺 Hungary",
        "39" to "🇮🇹 Italy",
        "40" to "🇷🇴 Romania",
        "41" to "🇨🇭 Switzerland",
        "43" to "🇦🇹 Austria",
        "44" to "🇬🇧 UK",
        "45" to "🇩🇰 Denmark",
        "46" to "🇸🇪 Sweden",
        "47" to "🇳🇴 Norway",
        "48" to "🇵🇱 Poland",
        "49" to "🇩🇪 Germany",
        "51" to "🇵🇪 Peru",
        "52" to "🇲🇽 Mexico",
        "53" to "🇨🇺 Cuba",
        "54" to "🇦🇷 Argentina",
        "55" to "🇧🇷 Brazil",
        "56" to "🇨🇱 Chile",
        "57" to "🇨🇴 Colombia",
        "58" to "🇻🇪 Venezuela",
        "60" to "🇲🇾 Malaysia",
        "61" to "🇦🇺 Australia",
        "62" to "🇮🇩 Indonesia",
        "63" to "🇵🇭 Philippines",
        "64" to "🇳🇿 New Zealand",
        "65" to "🇸🇬 Singapore",
        "66" to "🇹🇭 Thailand",
        "77" to "🇰🇿 Kazakhstan",
        "81" to "🇯🇵 Japan",
        "82" to "🇰🇷 South Korea",
        "84" to "🇻🇳 Vietnam",
        "86" to "🇨🇳 China",
        "90" to "🇹🇷 Turkey",
        "91" to "🇮🇳 India",
        "92" to "🇵🇰 Pakistan",
        "93" to "🇦🇫 Afghanistan",
        "94" to "🇱🇰 Sri Lanka",
        "95" to "🇲🇲 Myanmar",
        "98" to "🇮🇷 Iran",
        // 1-digit country dialing codes
        "7" to "🇷🇺 Russia",
        "1" to "🇺🇸 USA/CA"
    ).sortedByDescending { it.first.length }

    /**
     * Resolves the Country Name (with Flag) from a range prefix (e.g. "26134XXX" -> "🇲🇬 Madagascar").
     */
    fun detectCountryFromRange(rawRange: String): String {
        val cleanDigits = cleanRangeId(rawRange).removePrefix("+").filter { it.isDigit() }
        if (cleanDigits.isEmpty()) return ""

        learnedRangeCountryMap[cleanDigits]?.takeIf { it.isNotBlank() }?.let { return it }

        for ((prefix, countryLabel) in DIAL_CODE_COUNTRIES) {
            if (cleanDigits.startsWith(prefix)) {
                return countryLabel
            }
        }
        return "🌐 Intl"
    }

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

                if (country.isNotBlank()) {
                    learnedRangeCountryMap[rid] = country
                }

                VoltxApiResult.Success(
                    data = VoltxAllocatedNumber(
                        fullNumber = fullNum.ifBlank { "+$noPlusNum" },
                        nationalNumber = nationalNum.ifBlank { noPlusNum },
                        noPlusNumber = noPlusNum.ifBlank { fullNum.removePrefix("+") },
                        country = country.ifBlank { detectCountryFromRange(rid) },
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
                                val elem = rangesArr.opt(j)
                                if (elem is JSONObject) {
                                    val r = elem.optString("range", elem.optString("rid", "")).trim()
                                    val c = elem.optString("country", elem.optString("cn", "")).trim()
                                    if (r.isNotEmpty()) {
                                        ranges.add(r)
                                        if (c.isNotEmpty()) {
                                            learnedRangeCountryMap[cleanRangeId(r)] = c
                                        }
                                    }
                                } else {
                                    val r = rangesArr.optString(j, "").trim()
                                    if (r.isNotEmpty()) ranges.add(r)
                                }
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
