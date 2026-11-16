package io.github.martindavinci.lunaris.archives.create

/** 0 (empty) to 4, for the strength bar; reads the characters without making a String of them. */
object PasswordStrength {
    private const val LONG = 10
    private const val VERY_LONG = 16
    private const val SCORE_FAIR = 3
    private const val SCORE_GOOD = 5
    private const val WEAK = 1
    private const val FAIR = 2
    private const val GOOD = 3
    private const val STRONG = 4

    fun level(password: CharArray): Int {
        if (password.isEmpty()) return 0
        val score = listOf(
            password.size >= LONG,
            password.size >= VERY_LONG,
            password.any(Char::isLowerCase),
            password.any(Char::isUpperCase),
            password.any(Char::isDigit),
            password.any { !it.isLetterOrDigit() },
        ).count { it }
        return when {
            score < SCORE_FAIR -> WEAK
            score == SCORE_FAIR -> FAIR
            score <= SCORE_GOOD -> GOOD
            else -> STRONG
        }
    }
}
