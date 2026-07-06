package tools.lunaris.texteditor.ui.theme

import androidx.compose.ui.graphics.Color

// Dark editor palette.
val EditorBackground = Color(0xFF2B2B2B)
val EditorSurface = Color(0xFF3A3A3A)
val EditorOnSurface = Color(0xFFE6E6E6)
val EditorAccent = Color(0xFF1E9BE9)

// Toolbar action accents: filled circles like the preview button, each glyph a darker shade of its
// own bubble — yellow "open" with a dark-yellow folder, red "close" with a dark-red X.
val ToolbarOpenAccent = Color(0xFFE8B923)
val ToolbarOpenOnAccent = Color(0xFF6B4E00) // dark-yellow folder on the yellow circle
val ToolbarCloseAccent = Color(0xFFE05252)
val ToolbarCloseOnAccent = Color(0xFF6E0F0F) // dark-red X on the red circle
