package tools.lunaris.texteditor.document

/** Immutable set of open tabs with one active. All transitions return a new instance. */
data class OpenDocuments(
    val sessions: List<DocumentSession>,
    val activeId: String,
) {
    val active: DocumentSession? get() = sessions.firstOrNull { it.id == activeId }

    fun opened(session: DocumentSession): OpenDocuments =
        copy(sessions = sessions + session, activeId = session.id)

    /**
     * Opens [session], discarding any pristine blank untitled tabs (e.g. the launch scratch tab)
     * so opening a file doesn't leave a stray empty tab beside it. If [session] would be the only
     * remaining tab, all blank scratch tabs drop out.
     */
    fun openedReplacingScratch(session: DocumentSession): OpenDocuments {
        val kept = sessions.filterNot { it.isBlankScratch }
        return copy(sessions = kept + session, activeId = session.id)
    }

    fun activated(id: String): OpenDocuments =
        if (sessions.any { it.id == id }) copy(activeId = id) else this

    fun updateActive(transform: (DocumentSession) -> DocumentSession): OpenDocuments =
        copy(sessions = sessions.map { if (it.id == activeId) transform(it) else it })

    fun replace(id: String, transform: (DocumentSession) -> DocumentSession): OpenDocuments =
        copy(sessions = sessions.map { if (it.id == id) transform(it) else it })

    /** Closes [id]. If it was the last tab, returns a single fresh untitled tab using [freshId]. */
    fun closed(id: String, freshId: String = id): OpenDocuments {
        val remaining = sessions.filterNot { it.id == id }
        if (remaining.isEmpty()) {
            val fresh = DocumentSession(id = freshId)
            return OpenDocuments(listOf(fresh), fresh.id)
        }
        val newActive = if (activeId == id) {
            val closedIndex = sessions.indexOfFirst { it.id == id }
            remaining[closedIndex.coerceIn(0, remaining.size - 1)].id
        } else activeId
        return OpenDocuments(remaining, newActive)
    }

    /**
     * Folds a session restored from storage into the tabs opened since launch (an open-with or
     * share intent can land before the async restore finishes). Restored tabs come first and
     * pristine scratch tabs drop out. A tab for a file the restore also holds yields to the
     * restored copy, which may carry unsaved edits. Whatever the user had active stays active.
     */
    fun mergedWithRestored(restored: OpenDocuments): OpenDocuments {
        val restoredUris = restored.sessions.mapNotNull { it.fileRef?.uri }.toSet()
        val kept = sessions.filterNot { s -> s.isBlankScratch || s.fileRef?.uri?.let { it in restoredUris } == true }
        val current = active
        val newActive = when {
            current == null || current.isBlankScratch -> restored.activeId
            kept.any { it.id == activeId } -> activeId
            else -> restored.sessions.firstOrNull { it.fileRef?.uri == current.fileRef?.uri }?.id ?: restored.activeId
        }
        return OpenDocuments(restored.sessions + kept, newActive)
    }

    companion object {
        fun initial(id: String): OpenDocuments =
            OpenDocuments(listOf(DocumentSession(id = id)), id)
    }
}
