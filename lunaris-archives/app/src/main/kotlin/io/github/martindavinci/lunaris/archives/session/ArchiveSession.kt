package io.github.martindavinci.lunaris.archives.session

import io.github.martindavinci.lunaris.archives.archive.ArchiveIndex
import io.github.martindavinci.lunaris.archives.archive.ArchiveKind
import io.github.martindavinci.lunaris.archives.archive.Encryption
import io.github.martindavinci.lunaris.archives.engine.ArchiveEngine
import io.github.martindavinci.lunaris.archives.engine.ArchiveProblem
import io.github.martindavinci.lunaris.archives.engine.EngineAccessDeniedException
import io.github.martindavinci.lunaris.archives.engine.EngineRoute
import io.github.martindavinci.lunaris.archives.engine.EngineStoppedException
import io.github.martindavinci.lunaris.archives.engine.ListResult
import io.github.martindavinci.lunaris.archives.safety.Limits
import java.util.UUID
import io.github.martindavinci.lunaris.archives.di.Background
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Singleton

/**
 * The archives open in the app. Opening walks the routes (libarchive, then the reader that a
 * reroute or a password asks for). The password an archive was opened with stays with that open
 * archive, in memory, until it is closed, so a tap on a protected entry does not ask again;
 * "Remember until I close the app" also keeps it in [PasswordMemory] for later openings.
 */
@Singleton
class ArchiveSession @Inject constructor(
    private val engine: ArchiveEngine,
    private val cache: IndexCache,
    private val passwords: PasswordMemory,
    private val incoming: IncomingCopy,
    /** Where the index cache is read and written: 200 000 entries take a while to parse. */
    @param:Background private val io: CoroutineDispatcher = Dispatchers.Unconfined,
) {
    private val open = object : LinkedHashMap<String, OpenArchive>(CAPACITY, LOAD_FACTOR, true) {}
    private val openPasswords = HashMap<String, CharArray>()
    private val requests = HashMap<String, OpenRequest>()

    @Synchronized
    fun register(request: OpenRequest): String = newId().also { requests[it] = request }

    @Synchronized
    fun request(id: String): OpenRequest? = requests[id]

    @Synchronized
    fun get(id: String): OpenArchive? = open[id]

    /** A copy of the password [id] was opened with, or null. */
    @Synchronized
    fun passwordFor(id: String): CharArray? = openPasswords[id]?.copyOf()

    /** A password typed for an entry of [id] worked: keep a copy with the open archive, and for later if asked. */
    @Synchronized
    fun keepPassword(id: String, password: CharArray, remember: Boolean) {
        val archive = open[id] ?: return
        openPasswords.put(id, password.copyOf())?.fill(WIPED)
        if (remember) passwords.put(archive.source.key, password)
    }

    /** The password kept for [id] was wrong after all: it is asked again next time. */
    @Synchronized
    fun forgetPassword(id: String) {
        val archive = open[id] ?: return
        openPasswords.remove(id)?.fill(WIPED)
        passwords.forget(archive.source.key)
    }

    /** How many archives deep an archive opened inside [parentId] would be. */
    @Synchronized
    fun depth(parentId: String?): Int = depthOf(parentId)

    /** Drops the index after the archive changed under us: the next opening lists it again. */
    fun forgetIndex(source: ArchiveSource) = cache.remove(source.key)

    suspend fun open(source: ArchiveSource, parentId: String? = null, nestedEntry: Long? = null, id: String = newId()): OpenOutcome {
        if (depthOf(parentId) >= Limits.MAX_NESTING) return OpenOutcome.Problem(ArchiveProblem.UNSUPPORTED_FEATURE)
        when (source.kindHint) {
            ArchiveKind.UNKNOWN -> return OpenOutcome.Problem(ArchiveProblem.NOT_ARCHIVE)
            ArchiveKind.ZIP_SPLIT -> return OpenOutcome.Problem(ArchiveProblem.SPLIT_ARCHIVE)
            else -> Unit
        }
        val remembered = passwords.get(source.key)
        withContext(io) { cache.read(source.key) }?.let { (route, index) ->
            return OpenOutcome.Opened(store(id, source, route, index, parentId, nestedEntry, remembered))
        }
        return drive(id, PendingOpen(source, EngineRoute.LIBARCHIVE, parentId, nestedEntry), remembered, remember = false)
    }

    /** The caller wipes [password] after this returns; anything kept here is a copy. */
    suspend fun unlock(pending: PendingOpen, password: CharArray, remember: Boolean, id: String = newId()): OpenOutcome =
        drive(id, pending, password, remember)

    @Synchronized
    fun close(id: String) {
        val archive = open.remove(id) ?: return
        openPasswords.remove(id)?.fill(WIPED)
        incoming.forget(archive.source.key)
        open.values.filter { it.parentId == id }.map { it.id }.forEach(::close)
    }

    /** The app is closing: every archive, its password and its incoming copy go. */
    @Synchronized
    fun closeAll() {
        open.keys.toList().forEach(::close)
        openPasswords.values.forEach { it.fill(WIPED) }
        openPasswords.clear()
    }

    private suspend fun drive(id: String, start: PendingOpen, password: CharArray?, remember: Boolean): OpenOutcome {
        var pending = start
        repeat(MAX_ROUNDS) {
            val result = try {
                engine.list(pending.source, pending.route, password)
            } catch (_: EngineAccessDeniedException) {
                return OpenOutcome.NeedsCopy(pending.source.uri, pending.source.size)
            } catch (_: EngineStoppedException) {
                return OpenOutcome.Problem(ArchiveProblem.READER_STOPPED)
            }
            when (result) {
                is ListResult.Listed -> {
                    if (password != null && remember) passwords.put(pending.source.key, password)
                    if (result.index.encryption != Encryption.HEADERS) withContext(io) { cache.write(pending.source.key, pending.route, result.index) }
                    return OpenOutcome.Opened(store(id, pending.source, pending.route, result.index, pending.parentId, pending.nestedEntry, password))
                }
                is ListResult.Reroute -> pending = pending.copy(route = result.route)
                is ListResult.NeedsPassword -> {
                    pending = pending.copy(route = result.route)
                    if (password == null) return OpenOutcome.NeedsPassword(pending)
                }
                ListResult.WrongPassword -> return OpenOutcome.WrongPassword(pending)
                is ListResult.Problem -> return OpenOutcome.Problem(result.problem)
            }
        }
        return OpenOutcome.Problem(ArchiveProblem.DAMAGED)
    }

    @Synchronized
    private fun store(
        id: String, source: ArchiveSource, route: EngineRoute, index: ArchiveIndex,
        parentId: String?, nestedEntry: Long?, password: CharArray?,
    ): OpenArchive {
        val archive = OpenArchive(id, source, route, index, parentId, nestedEntry)
        open[id] = archive
        password?.let { openPasswords.put(id, it.copyOf())?.fill(WIPED) }
        while (open.size > CAPACITY) close(open.keys.first())
        return archive
    }

    @Synchronized
    private fun depthOf(parentId: String?): Int {
        var depth = 0
        var current = parentId
        while (current != null && depth <= Limits.MAX_NESTING) {
            depth++
            current = open[current]?.parentId
        }
        return depth
    }

    private fun newId() = UUID.randomUUID().toString()

    private companion object {
        const val MAX_ROUNDS = 3
        const val CAPACITY = Limits.INDEXES_IN_MEMORY * (Limits.MAX_NESTING + 1)
        const val LOAD_FACTOR = 0.75f
        val WIPED = Char(0)
    }
}
