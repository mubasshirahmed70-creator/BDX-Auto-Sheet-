package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.api.MailDataParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.util.UUID

data class HotmailItem(
    val id: String = UUID.randomUUID().toString(),
    val rawData: String,
    val email: String,
    val isUsed: Boolean = false,
    val usedTimestamp: Long = 0L,
    val addedTimestamp: Long = System.currentTimeMillis()
)

data class HotmailStockState(
    val items: List<HotmailItem> = emptyList(),
    val currentLoadedId: String? = null
) {
    val availableItems: List<HotmailItem>
        get() = items.filter { !it.isUsed }

    val usedItems: List<HotmailItem>
        get() = items.filter { it.isUsed }

    val totalCount: Int
        get() = items.size

    val availableCount: Int
        get() = availableItems.size

    val usedCount: Int
        get() = usedItems.size

    val currentLoadedItem: HotmailItem?
        get() = items.find { it.id == currentLoadedId }

    val nextAvailableItem: HotmailItem?
        get() = availableItems.firstOrNull()
}

class HotmailStockRepository private constructor(private val context: Context) {

    private val _stockState = MutableStateFlow(HotmailStockState())
    val stockState: StateFlow<HotmailStockState> = _stockState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)
    private val storageFile: File by lazy { File(context.filesDir, "hotmail_stock.json") }

    init {
        loadFromDisk()
    }

    /**
     * Imports hotmails from an InputStream (such as from a user selected .txt file).
     * Returns Pair(importedCount, duplicateCount).
     */
    @Synchronized
    fun importFromInputStream(inputStream: InputStream): Pair<Int, Int> {
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        val lines = reader.readLines()
        return processLines(lines)
    }

    /**
     * Imports hotmails from a raw multiline text string.
     * Returns Pair(importedCount, duplicateCount).
     */
    @Synchronized
    fun importFromText(text: String): Pair<Int, Int> {
        val lines = text.split("\r\n", "\n", "\r")
        return processLines(lines)
    }

    /**
     * Reads a TXT file from Android Content Uri.
     */
    @Synchronized
    fun importFromUri(uri: Uri): Pair<Int, Int> {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                importFromInputStream(stream)
            } ?: Pair(0, 0)
        } catch (e: Exception) {
            Log.e("HotmailStockRepo", "Failed to import from URI: ${e.message}", e)
            Pair(0, 0)
        }
    }

    private fun processLines(lines: List<String>): Pair<Int, Int> {
        val currentItems = _stockState.value.items.toMutableList()
        val existingRaws = currentItems.map { it.rawData.trim() }.toHashSet()
        val existingEmails = currentItems.map { it.email.lowercase().trim() }.toHashSet()

        var imported = 0
        var duplicates = 0

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue

            // Check if exact raw line or exact email already in stock
            val extractedEmail = MailDataParser.extractEmail(trimmed) ?: trimmed.substringBefore("|").trim()
            val lowerEmail = extractedEmail.lowercase().trim()

            if (existingRaws.contains(trimmed) || existingEmails.contains(lowerEmail)) {
                duplicates++
                continue
            }

            val item = HotmailItem(
                rawData = trimmed,
                email = extractedEmail,
                isUsed = false
            )
            currentItems.add(item)
            existingRaws.add(trimmed)
            if (lowerEmail.isNotEmpty()) existingEmails.add(lowerEmail)
            imported++
        }

        if (imported > 0) {
            _stockState.value = _stockState.value.copy(items = currentItems)
            saveToDisk()
        }

        return Pair(imported, duplicates)
    }

    /**
     * Returns the currently loaded item if it is available and not used,
     * or selects and sets the first available unused item as active.
     */
    @Synchronized
    fun getCurrentOrFirstAvailable(): HotmailItem? {
        val current = _stockState.value.currentLoadedItem
        if (current != null && !current.isUsed) {
            return current
        }
        val first = _stockState.value.availableItems.firstOrNull()
        if (first != null) {
            _stockState.value = _stockState.value.copy(currentLoadedId = first.id)
            saveToDisk()
        }
        return first
    }

    /**
     * Retrieves the next unused hotmail in the queue and sets it as active.
     * Cycles to the next available item in the queue.
     */
    @Synchronized
    fun getAndAdvanceNextMail(): HotmailItem? {
        val available = _stockState.value.availableItems
        if (available.isEmpty()) return null

        val currentId = _stockState.value.currentLoadedId
        val next = if (currentId == null) {
            available.firstOrNull()
        } else {
            val currentIndex = available.indexOfFirst { it.id == currentId }
            if (currentIndex in 0 until available.size - 1) {
                available[currentIndex + 1]
            } else {
                available.firstOrNull()
            }
        }

        if (next != null) {
            _stockState.value = _stockState.value.copy(currentLoadedId = next.id)
            saveToDisk()
        }
        return next
    }

    /**
     * Marks the currently loaded hotmail as used/completed and advances to the next available item.
     * Returns the newly loaded HotmailItem or null if queue is empty.
     */
    @Synchronized
    fun markCurrentAsUsedAndGetNext(): HotmailItem? {
        val currentId = _stockState.value.currentLoadedId
        if (currentId != null) {
            val currentItems = _stockState.value.items
            val newItems = currentItems.map { item ->
                if (item.id == currentId) {
                    item.copy(isUsed = true, usedTimestamp = System.currentTimeMillis())
                } else item
            }
            val nextAvailable = newItems.firstOrNull { !it.isUsed }
            _stockState.value = _stockState.value.copy(
                items = newItems,
                currentLoadedId = nextAvailable?.id
            )
            saveToDisk()
            return nextAvailable
        }
        return _stockState.value.availableItems.firstOrNull()
    }

    /**
     * Sets the currently active hotmail item.
     */
    @Synchronized
    fun setCurrentLoaded(item: HotmailItem?) {
        _stockState.value = _stockState.value.copy(currentLoadedId = item?.id)
        saveToDisk()
    }

    /**
     * Marks a hotmail as USED and discarded from active stock (e.g. after OTP is retrieved).
     */
    @Synchronized
    fun markMailAsUsed(emailOrRaw: String) {
        val target = emailOrRaw.trim()
        if (target.isBlank()) return

        val currentItems = _stockState.value.items
        var updated = false

        val newItems = currentItems.map { item ->
            val match = item.rawData.equals(target, ignoreCase = true) ||
                    item.email.equals(target, ignoreCase = true) ||
                    target.contains(item.email, ignoreCase = true)

            if (match && !item.isUsed) {
                updated = true
                item.copy(isUsed = true, usedTimestamp = System.currentTimeMillis())
            } else {
                item
            }
        }

        if (updated) {
            val nextLoadedId = if (_stockState.value.currentLoadedId != null &&
                newItems.find { it.id == _stockState.value.currentLoadedId }?.isUsed == true
            ) {
                // If currently loaded item was just marked used, load next available
                newItems.firstOrNull { !it.isUsed }?.id
            } else {
                _stockState.value.currentLoadedId
            }

            _stockState.value = _stockState.value.copy(
                items = newItems,
                currentLoadedId = nextLoadedId
            )
            saveToDisk()
        }
    }

    /**
     * Marks the currently loaded hotmail as used/skipped.
     */
    @Synchronized
    fun markCurrentAsUsed() {
        val currentId = _stockState.value.currentLoadedId ?: return
        val currentItems = _stockState.value.items
        val newItems = currentItems.map { item ->
            if (item.id == currentId) {
                item.copy(isUsed = true, usedTimestamp = System.currentTimeMillis())
            } else item
        }
        val nextAvailable = newItems.firstOrNull { !it.isUsed }
        _stockState.value = _stockState.value.copy(
            items = newItems,
            currentLoadedId = nextAvailable?.id
        )
        saveToDisk()
    }

    /**
     * Resets all used hotmails back to active/available status.
     */
    @Synchronized
    fun resetAllUsedToAvailable() {
        val newItems = _stockState.value.items.map { it.copy(isUsed = false, usedTimestamp = 0L) }
        _stockState.value = _stockState.value.copy(
            items = newItems,
            currentLoadedId = newItems.firstOrNull()?.id
        )
        saveToDisk()
    }

    /**
     * Clears all hotmail stock completely.
     */
    @Synchronized
    fun clearAll() {
        _stockState.value = HotmailStockState()
        saveToDisk()
    }

    /**
     * Clears only used hotmails, keeping available ones.
     */
    @Synchronized
    fun clearUsedOnly() {
        val remaining = _stockState.value.items.filter { !it.isUsed }
        _stockState.value = _stockState.value.copy(items = remaining)
        saveToDisk()
    }

    private fun loadFromDisk() {
        try {
            if (!storageFile.exists()) return
            val jsonStr = storageFile.readText(Charsets.UTF_8)
            if (jsonStr.isBlank()) return

            val root = JSONObject(jsonStr)
            val currentLoadedId = if (root.has("currentLoadedId") && !root.isNull("currentLoadedId")) {
                root.getString("currentLoadedId")
            } else null

            val array = root.optJSONArray("items") ?: JSONArray()
            val list = mutableListOf<HotmailItem>()

            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id", UUID.randomUUID().toString())
                val rawData = obj.optString("rawData", "")
                val email = obj.optString("email", "")
                val isUsed = obj.optBoolean("isUsed", false)
                val usedTimestamp = obj.optLong("usedTimestamp", 0L)
                val addedTimestamp = obj.optLong("addedTimestamp", 0L)

                if (rawData.isNotBlank()) {
                    list.add(
                        HotmailItem(
                            id = id,
                            rawData = rawData,
                            email = email.ifBlank { MailDataParser.extractEmail(rawData) ?: "" },
                            isUsed = isUsed,
                            usedTimestamp = usedTimestamp,
                            addedTimestamp = addedTimestamp
                        )
                    )
                }
            }

            _stockState.value = HotmailStockState(
                items = list,
                currentLoadedId = currentLoadedId
            )
        } catch (e: Exception) {
            Log.e("HotmailStockRepo", "Failed to load hotmail stock from disk", e)
        }
    }

    private fun saveToDisk() {
        scope.launch {
            try {
                val state = _stockState.value
                val root = JSONObject()
                root.put("currentLoadedId", state.currentLoadedId ?: JSONObject.NULL)

                val array = JSONArray()
                for (item in state.items) {
                    val obj = JSONObject().apply {
                        put("id", item.id)
                        put("rawData", item.rawData)
                        put("email", item.email)
                        put("isUsed", item.isUsed)
                        put("usedTimestamp", item.usedTimestamp)
                        put("addedTimestamp", item.addedTimestamp)
                    }
                    array.put(obj)
                }
                root.put("items", array)

                val tempFile = File(context.filesDir, "hotmail_stock.json.tmp")
                tempFile.writeText(root.toString(2), Charsets.UTF_8)
                tempFile.renameTo(storageFile)
            } catch (e: Exception) {
                Log.e("HotmailStockRepo", "Failed to save hotmail stock to disk", e)
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: HotmailStockRepository? = null

        fun getInstance(context: Context): HotmailStockRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: HotmailStockRepository(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
}
