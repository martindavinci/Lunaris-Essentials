package io.github.martindavinci.lunaris.archives.feature.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.martindavinci.lunaris.archives.create.ArchiveNameRules
import io.github.martindavinci.lunaris.archives.create.CreateItem
import io.github.martindavinci.lunaris.archives.create.CreateRunner
import io.github.martindavinci.lunaris.archives.create.CreateSources
import io.github.martindavinci.lunaris.archives.create.CreateState
import io.github.martindavinci.lunaris.archives.create.SourceFile
import io.github.martindavinci.lunaris.archives.create.SourceTree
import io.github.martindavinci.lunaris.archives.create.ZipPaths
import io.github.martindavinci.lunaris.archives.session.ArchiveSession
import io.github.martindavinci.lunaris.archives.session.CreateInbox
import io.github.martindavinci.lunaris.archives.session.OpenRequest
import io.github.martindavinci.lunaris.archives.di.Background
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Frames 13–15: what goes in, how, and the run. The password only passes through, in CreateAt. */
@HiltViewModel
class CreateViewModel @Inject constructor(
    tree: SourceTree,
    private val runner: CreateRunner,
    inbox: CreateInbox,
    private val session: ArchiveSession,
    /** Where picked files and folders are read: a big folder over SAF takes seconds. */
    @param:Background private val background: CoroutineDispatcher,
) : ViewModel() {
    private val sources = CreateSources(tree)
    private val files = LinkedHashMap<String, List<SourceFile>>()
    private var nameEdited = false
    private var folderName: String? = null
    private var passwordOk = true
    private val _state = MutableStateFlow(CreateUiState())
    val state: StateFlow<CreateUiState> = _state.asStateFlow()

    init {
        inbox.take().takeIf { it.isNotEmpty() }?.let { addFiles(it) }
        viewModelScope.launch { runner.state.collect { s -> _state.update { it.copy(state = s) } } }
    }

    fun onAction(action: CreateAction) {
        when (action) {
            is CreateAction.AddFiles -> addFiles(action.uris)
            is CreateAction.AddFolder -> addFolder(action.uri)
            is CreateAction.Remove -> { files.remove(action.item.id); refresh() }
            is CreateAction.SetName -> { nameEdited = true; _state.update { it.copy(name = action.name) }; refresh() }
            is CreateAction.SetLevel -> _state.update { it.copy(level = action.level) }
            is CreateAction.SetProtect -> { passwordOk = !action.on; _state.update { it.copy(protect = action.on, strength = 0, mismatch = false) }; refresh() }
            is CreateAction.PasswordChanged -> {
                passwordOk = action.matches && action.level > 0
                _state.update { it.copy(strength = action.level, mismatch = !action.matches) }
                refresh()
            }
            is CreateAction.CreateAt -> create(action.uri, action.password)
            CreateAction.RequestStop -> _state.update { it.copy(confirmStop = true) }
            CreateAction.KeepGoing -> _state.update { it.copy(confirmStop = false) }
            CreateAction.Stop -> { runner.stop(); _state.update { it.copy(confirmStop = false) } }
            CreateAction.OpenCreated -> openCreated()
            CreateAction.Navigated -> _state.update { it.copy(openRequest = null) }
            CreateAction.FailureShown -> runner.reset()
            CreateAction.Share -> Unit   // the route sends the new file; nothing changes here
        }
    }

    private fun addFiles(uris: List<String>) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val found = withContext(background) { sources.fromFiles(uris) }
            found.forEach { file -> files[file.uri] = listOf(file) }
            _state.update { it.copy(pickedFiles = true) }
            refresh()
        }
    }

    private fun addFolder(uri: String) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val (name, found) = withContext(background) { sources.fromFolder(uri) }
            if (found.isNotEmpty()) files[uri] = found
            folderName = name.takeIf { files.size == 1 && found.isNotEmpty() }
            _state.update { it.copy(pickedFolder = true) }
            refresh()
        }
    }

    private fun refresh() {
        val items = files.map { (id, list) ->
            val folder = list.size > 1 || list.firstOrNull()?.pathInZip?.contains('/') == true
            sources.item(list, if (folder) list.first().pathInZip.substringBefore('/') else list.first().pathInZip, folder).copy(id = id)
        }
        _state.update { s ->
            val name = if (nameEdited) s.name else suggestedName(items)
            val nameOk = ArchiveNameRules.isValid(name)
            s.copy(
                items = items, totalBytes = items.sumOf { it.sizeBytes ?: 0 }, name = name,
                nameError = nameEdited && !nameOk, canCreate = items.isNotEmpty() && nameOk && passwordOk,
            )
        }
    }

    private fun suggestedName(items: List<CreateItem>): String = when {
        items.isEmpty() -> ""
        items.size == 1 && items.single().isFolder -> items.single().name
        else -> ArchiveNameRules.suggested(items.map { it.name }, null)
    }

    private fun create(uri: String, password: CharArray?) {
        val all = files.values.flatten()
        val paths = ZipPaths.unique(all.map { it.pathInZip })
        val unique = all.zip(paths) { file, path -> file.copy(pathInZip = path) }
        val s = _state.value
        runner.start(unique, uri, ArchiveNameRules.fileName(s.name), s.level, password, sources.skipped)
    }

    private fun openCreated() {
        val done = _state.value.state as? CreateState.Finished ?: return
        val id = session.register(OpenRequest(done.uri, null, null, null))
        runner.reset()
        _state.update { it.copy(openRequest = id) }
    }
}
