package com.example.data.model

data class SheetConfig(
    val columns: List<String> = listOf("A", "B", "C"),
    val buttonSizeDp: Int = 54,
    val isLocalSaveMode: Boolean = true,
    val isHorizontalBubbleLayout: Boolean = false
) {
    companion object {
        const val DEFAULT_COLUMNS_STR = "A,B,C"
        const val MIN_BUTTON_SIZE = 38
        const val MAX_BUTTON_SIZE = 76
        const val DEFAULT_BUTTON_SIZE = 54

        fun parseColumns(raw: String): List<String> {
            val list = raw.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (list.isEmpty()) {
                return listOf("A", "B", "C")
            }
            // Preserve entered order, eliminate exact duplicates gracefully
            val seen = mutableSetOf<String>()
            val result = mutableListOf<String>()
            for (item in list) {
                if (item !in seen) {
                    seen.add(item)
                    result.add(item)
                }
            }
            return if (result.isEmpty()) listOf("A", "B", "C") else result
        }
    }
}

/**
 * Represents a single change for undo/redo history.
 */
data class CellHistoryAction(
    val row: Int,
    val colIndex: Int,
    val previousValue: String?,
    val newValue: String,
    val prevCurrentRow: Int,
    val newCurrentRow: Int,
    val prevLastColIndex: Int,
    val newLastColIndex: Int
)

data class SheetState(
    val config: SheetConfig = SheetConfig(),
    // Map of (row, colIndex) -> cell string value (row is 1-indexed, colIndex is 0-indexed)
    val cells: Map<Pair<Int, Int>, String> = emptyMap(),
    // Current active round/row position (1-indexed)
    val currentRow: Int = 1,
    // Last column index pressed in the active round (-1 if none)
    val lastColIndex: Int = -1
) {
    /**
     * Total maximum row number currently recorded.
     */
    val maxRecordedRow: Int
        get() = (cells.keys.maxOfOrNull { it.first } ?: 1).coerceAtLeast(currentRow)

    val totalFilledCells: Int
        get() = cells.count { it.value.isNotBlank() }
}

sealed class ClipboardInsertResult {
    data class Success(
        val columnName: String,
        val row: Int,
        val content: String
    ) : ClipboardInsertResult()

    data object EmptyClipboard : ClipboardInsertResult()
    data object NoUsableText : ClipboardInsertResult()
    data class Error(val message: String) : ClipboardInsertResult()
}
