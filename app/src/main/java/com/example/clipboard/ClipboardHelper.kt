package com.example.clipboard

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.text.Html
import android.util.Log

sealed class ClipboardReadResult {
    data class Text(val content: String) : ClipboardReadResult()
    data object Empty : ClipboardReadResult()
    data object NonTextContent : ClipboardReadResult()
    data class Error(val error: String) : ClipboardReadResult()
}

object ClipboardHelper {

    fun readCurrentText(context: Context): ClipboardReadResult {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return ClipboardReadResult.Error("Clipboard service unavailable")

            // 1. Check primary clip first
            if (clipboard.hasPrimaryClip()) {
                val clip = clipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    for (i in 0 until clip.itemCount) {
                        val item = clip.getItemAt(i)

                        // Direct text
                        val text = item.text?.toString()
                        if (!text.isNullOrEmpty()) {
                            return ClipboardReadResult.Text(text)
                        }

                        // Coerced text (handles URIs, Styled Text, Intents)
                        val coerced = item.coerceToText(context)?.toString()
                        if (!coerced.isNullOrEmpty()) {
                            return ClipboardReadResult.Text(coerced)
                        }

                        // HTML text
                        val html = item.htmlText
                        if (!html.isNullOrEmpty()) {
                            val parsed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
                            } else {
                                @Suppress("DEPRECATION")
                                Html.fromHtml(html).toString()
                            }
                            if (parsed.isNotEmpty()) {
                                return ClipboardReadResult.Text(parsed)
                            }
                        }
                    }
                }
            }

            // 2. Legacy clipboard fallback for compatibility
            @Suppress("DEPRECATION")
            if (clipboard.hasText()) {
                @Suppress("DEPRECATION")
                val legacyText = clipboard.text?.toString()
                if (!legacyText.isNullOrEmpty()) {
                    return ClipboardReadResult.Text(legacyText)
                }
            }

            ClipboardReadResult.Empty
        } catch (e: SecurityException) {
            Log.w("ClipboardHelper", "Clipboard access restricted: ${e.message}")
            ClipboardReadResult.Empty
        } catch (e: Exception) {
            Log.e("ClipboardHelper", "Error reading clipboard", e)
            ClipboardReadResult.Error(e.localizedMessage ?: "Failed to read clipboard")
        }
    }
}
