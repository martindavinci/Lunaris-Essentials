package io.github.martindavinci.lunaris.scanner.history

import io.github.martindavinci.lunaris.scanner.model.CodeFormat

data class HistoryEntry(val id: Long, val text: String, val format: CodeFormat, val timestampMillis: Long)
