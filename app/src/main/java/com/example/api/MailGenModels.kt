package com.example.api

import org.json.JSONArray
import org.json.JSONObject

data class MailMessage(
    val uid: String = "",
    val from: String = "",
    val subject: String = "",
    val date: String = "",
    val message: String = "",
    val code: String? = null
) {
    /**
     * Tries to find an OTP / verification code either from explicit code field
     * or by parsing common verification code patterns in the message body/subject.
     */
    fun getResolvedCode(): String? {
        if (!code.isNullOrBlank()) {
            return code.trim()
        }

        // Search for patterns like "123456", "123-456", "code is 123456", "code: 1234"
        val textToSearch = "$subject\n$message"
        val regexPatterns = listOf(
            Regex("""(?:code|verification|otp|pin|is|password|passcode)[^\d\n]{1,15}(\b\d{4,8}\b)""", RegexOption.IGNORE_CASE),
            Regex("""\b(\d{3}-\d{3})\b"""),
            Regex("""\b(\d{4,8})\b""")
        )

        for (regex in regexPatterns) {
            val match = regex.find(textToSearch)
            if (match != null) {
                val group = if (match.groupValues.size > 1) match.groupValues[1] else match.value
                if (group.isNotBlank()) return group.trim()
            }
        }
        return null
    }
}

data class MailInboxResult(
    val email: String,
    val isSuccess: Boolean,
    val error: String?,
    val mailSource: String?,
    val messages: List<MailMessage>
)

object MailDataParser {
    private val EMAIL_REGEX = Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}""")

    /**
     * Extracts the first valid email address from any raw string, e.g.
     * "user@outlook.com|pass|rt|cid" -> "user@outlook.com"
     */
    fun extractEmail(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        // If pipe-separated, the first part is usually the email
        if (trimmed.contains("|")) {
            val firstPart = trimmed.substringBefore("|").trim()
            if (EMAIL_REGEX.matches(firstPart)) {
                return firstPart
            }
        }

        // Fallback to finding first email occurrence in text
        val match = EMAIL_REGEX.find(trimmed)
        return match?.value?.trim()
    }

    /**
     * Auto-detect best mode for /api/get-inbox:
     * - If contains only email|password (2 parts) -> "roundcube"
     * - If contains 4 parts with token/secret -> "oauth"
     */
    fun detectMode(raw: String): String {
        val parts = raw.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        return if (parts.size == 2 && parts[0].contains("@")) {
            "roundcube"
        } else {
            "oauth"
        }
    }
}
