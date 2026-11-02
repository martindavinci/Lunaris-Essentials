package io.github.martindavinci.lunaris.scanner.parse

import io.github.martindavinci.lunaris.scanner.model.CodeFormat
import io.github.martindavinci.lunaris.scanner.model.ParsedContent
import io.github.martindavinci.lunaris.scanner.model.ScannedCode

internal fun parse(text: String, format: CodeFormat = CodeFormat.QR_CODE): ParsedContent =
    ContentParser.parse(ScannedCode(text, format))
