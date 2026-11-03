package com.example.data.repository

import android.content.Context
import com.example.api.VoltxAllocatedNumber
import com.example.api.VoltxOtpItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class NumberCopyFormat(val label: String) {
    NO_PLUS("No +"),
    FULL_PLUS("+Full"),
    NATIONAL("Local")
}

data class VoltxSmsState(
    val apiKey: String = "",
    val selectedRange: String = "",
    val copyFormat: NumberCopyFormat = NumberCopyFormat.FULL_PLUS,
    val allocatedNumbers: List<VoltxAllocatedNumber> = emptyList(),
    val latestOtps: List<VoltxOtpItem> = emptyList()
)

class VoltxSmsRepository private constructor(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<VoltxSmsState> = _state.asStateFlow()

    private fun loadInitialState(): VoltxSmsState {
        val key = prefs.getString(KEY_API_TOKEN, "") ?: ""
        val range = prefs.getString(KEY_SELECTED_RANGE, "") ?: ""
        val numbers = parseSavedNumbers(prefs.getString(KEY_ALLOCATED_NUMBERS, null))
        return VoltxSmsState(
            apiKey = key,
            selectedRange = range,
            copyFormat = NumberCopyFormat.FULL_PLUS,
            allocatedNumbers = numbers
        )
    }

    fun updateApiKey(newKey: String) {
        val trimmed = newKey.trim()
        prefs.edit().putString(KEY_API_TOKEN, trimmed).apply()
        _state.value = _state.value.copy(apiKey = trimmed)
    }

    fun updateSelectedRange(range: String) {
        val trimmed = range.trim()
        prefs.edit().putString(KEY_SELECTED_RANGE, trimmed).apply()
        _state.value = _state.value.copy(selectedRange = trimmed)
    }

    fun cycleCopyFormat(): NumberCopyFormat {
        // Fixed to +Full permanently as requested
        _state.value = _state.value.copy(copyFormat = NumberCopyFormat.FULL_PLUS)
        return NumberCopyFormat.FULL_PLUS
    }

    fun formatNumberForCopy(num: VoltxAllocatedNumber): String {
        val cleanNoPlus = num.noPlusNumber.ifBlank { num.fullNumber.removePrefix("+") }
        return if (num.fullNumber.startsWith("+")) num.fullNumber else "+$cleanNoPlus"
    }

    fun addAllocatedNumber(number: VoltxAllocatedNumber) {
        val current = _state.value.allocatedNumbers.toMutableList()
        // Remove older entry if same number allocated again, and put newest at top
        current.removeAll { it.noPlusNumber == number.noPlusNumber }
        current.add(0, number)
        val capped = current.take(30)
        saveNumbers(capped)
        _state.value = _state.value.copy(allocatedNumbers = capped)
    }

    fun clearAllocatedNumbers() {
        prefs.edit().remove(KEY_ALLOCATED_NUMBERS).apply()
        _state.value = _state.value.copy(allocatedNumbers = emptyList())
    }

    fun removeAllocatedNumber(noPlusNumber: String) {
        val updated = _state.value.allocatedNumbers.filterNot { it.noPlusNumber == noPlusNumber }
        saveNumbers(updated)
        _state.value = _state.value.copy(allocatedNumbers = updated)
    }

    /**
     * Matches incoming /success-otp items with our allocatedNumbers list.
     * Returns any NEWLY matched (or newly arrived) VoltxOtpItem so the UI can alert/toast immediately!
     */
    fun mergeIncomingOtps(otps: List<VoltxOtpItem>): List<VoltxOtpItem> {
        val currentNums = _state.value.allocatedNumbers
        val newlyMatched = mutableListOf<VoltxOtpItem>()

        val updatedNums = currentNums.map { alloc ->
            val cleanTarget = alloc.noPlusNumber.trim().removePrefix("+")
            val match = otps.firstOrNull { otp ->
                val cleanOtpNum = otp.number.trim().removePrefix("+")
                cleanOtpNum == cleanTarget ||
                        (cleanTarget.length >= 8 && cleanOtpNum.endsWith(cleanTarget)) ||
                        (cleanOtpNum.length >= 8 && cleanTarget.endsWith(cleanOtpNum))
            }

            if (match != null) {
                val resolvedCode = match.extractedCode ?: match.message
                if (alloc.otpCode != resolvedCode || alloc.otpTime != match.time) {
                    newlyMatched.add(match)
                }
                alloc.copy(
                    otpCode = resolvedCode,
                    otpMessage = match.message,
                    otpTime = match.time
                )
            } else {
                alloc
            }
        }

        if (updatedNums != currentNums) {
            saveNumbers(updatedNums)
        }
        _state.value = _state.value.copy(
            allocatedNumbers = updatedNums,
            latestOtps = otps
        )
        return newlyMatched
    }

    private fun saveNumbers(list: List<VoltxAllocatedNumber>) {
        val arr = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("fullNumber", item.fullNumber)
                put("nationalNumber", item.nationalNumber)
                put("noPlusNumber", item.noPlusNumber)
                put("country", item.country)
                put("operator", item.operator)
                put("rangeRid", item.rangeRid)
                put("allocatedAt", item.allocatedAt)
                if (item.otpCode != null) put("otpCode", item.otpCode)
                if (item.otpMessage != null) put("otpMessage", item.otpMessage)
                if (item.otpTime != null) put("otpTime", item.otpTime)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_ALLOCATED_NUMBERS, arr.toString()).apply()
    }

    private fun parseSavedNumbers(jsonStr: String?): List<VoltxAllocatedNumber> {
        if (jsonStr.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<VoltxAllocatedNumber>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(
                    VoltxAllocatedNumber(
                        fullNumber = obj.optString("fullNumber", ""),
                        nationalNumber = obj.optString("nationalNumber", ""),
                        noPlusNumber = obj.optString("noPlusNumber", ""),
                        country = obj.optString("country", ""),
                        operator = obj.optString("operator", ""),
                        rangeRid = obj.optString("rangeRid", ""),
                        allocatedAt = obj.optLong("allocatedAt", 0L),
                        otpCode = if (obj.has("otpCode")) obj.optString("otpCode") else null,
                        otpMessage = if (obj.has("otpMessage")) obj.optString("otpMessage") else null,
                        otpTime = if (obj.has("otpTime")) obj.optLong("otpTime") else null
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val PREFS_NAME = "voltx_sms_prefs"
        private const val KEY_API_TOKEN = "mauthapi_token"
        private const val KEY_SELECTED_RANGE = "selected_range"
        private const val KEY_COPY_FORMAT = "copy_format"
        private const val KEY_ALLOCATED_NUMBERS = "allocated_numbers_json"

        @Volatile
        private var instance: VoltxSmsRepository? = null

        fun getInstance(context: Context): VoltxSmsRepository {
            return instance ?: synchronized(this) {
                instance ?: VoltxSmsRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
