package io.github.martindavinci.lunaris.archives

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.martindavinci.lunaris.archives.open.ArchiveSniffer
import io.github.martindavinci.lunaris.archives.open.Incoming
import io.github.martindavinci.lunaris.archives.open.IncomingIntent
import io.github.martindavinci.lunaris.archives.recents.SettingsSource
import io.github.martindavinci.lunaris.archives.session.ArchiveSession
import io.github.martindavinci.lunaris.archives.session.CreateInbox
import io.github.martindavinci.lunaris.archives.session.OpenRequest
import io.github.martindavinci.lunaris.archives.settings.AppSettings
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where an intent from another app sends the user. */
sealed interface StartTarget {
    data class Browser(val requestId: String) : StartTarget
    data object Create : StartTarget
}

/**
 * [settings] is null until read (no theme flash). [opening]: a shared file is being looked at.
 * [target]: a screen to show, cleared by [MainViewModel.targetShown]. [textOnly]: a share without files.
 */
data class MainUiState(
    val settings: AppSettings? = null,
    val opening: Boolean = false,
    val target: StartTarget? = null,
    val textOnly: Boolean = false,
)

@HiltViewModel
class MainViewModel internal constructor(
    settings: Flow<AppSettings>,
    private val register: (OpenRequest) -> String,
    private val putInCreate: (List<String>) -> Unit,
    private val looksLikeArchive: suspend (String) -> Boolean,
) : ViewModel() {
    @Inject constructor(settings: SettingsSource, session: ArchiveSession, inbox: CreateInbox, sniffer: ArchiveSniffer) :
        this(settings.settings, session::register, inbox::put, sniffer::looksLikeArchive)

    private val moves = MutableStateFlow(MainUiState())

    val state: StateFlow<MainUiState> = combine(settings, moves) { current, m -> m.copy(settings = current) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState())

    /** Not [fresh] (a recreated activity, a relaunch from Recents): it was handled already. */
    fun handle(action: String?, data: String?, streams: List<String>, fresh: Boolean) {
        if (!fresh) return
        moves.update { it.copy(opening = true) }
        viewModelScope.launch {
            val single = streams.distinct().singleOrNull()?.takeIf { it.startsWith(CONTENT) }
            val archive = single != null && looksLikeArchive(single)
            val incoming = IncomingIntent.classify(action, data, streams) { archive }
            moves.update { m ->
                when (incoming) {
                    is Incoming.OpenArchive -> m.copy(target = StartTarget.Browser(register(OpenRequest(incoming.uri, null, null, null))))
                    is Incoming.CreateFrom -> m.copy(target = StartTarget.Create).also { putInCreate(incoming.uris) }
                    Incoming.TextOnly -> m.copy(textOnly = true)
                    Incoming.Nothing -> m
                }.copy(opening = false)
            }
        }
    }

    fun targetShown() = moves.update { it.copy(target = null) }

    fun textOnlyShown() = moves.update { it.copy(textOnly = false) }

    private companion object { const val CONTENT = "content:" }
}
