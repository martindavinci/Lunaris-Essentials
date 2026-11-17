package io.github.martindavinci.lunaris.archives.edits

import org.json.JSONArray
import org.json.JSONObject

/** The register as JSON. Reading never fails: a damaged file is an empty register. */
internal object EditCodec {
    fun encode(records: List<EditRecord>): String = JSONArray().apply {
        records.forEach { r ->
            put(JSONObject().apply {
                put(ID, r.id); put(COPY, r.copyPath); put(URI, r.archiveUri); put(KEY, r.archiveKey); put(NAME, r.archiveName)
                put(A_SIZE, r.archiveSize); r.archiveMtime?.let { put(A_MTIME, it) }; put(A_PRINT, r.archiveFingerprint)
                put(FORMAT, r.archiveFormat); put(ENTRY, r.entryPath); put(SIZE, r.size); put(MTIME, r.mtime); put(SHA, r.sha256)
                put(UPDATABLE, r.updatable); put(CREATED, r.createdMillis)
                r.pending?.let { p ->
                    put(PENDING, JSONObject().apply { put(PATH, p.path); put(SIZE, p.size); put(MTIME, p.mtime); put(SHA, p.sha256) })
                }
            })
        }
    }.toString()

    fun decode(json: String): List<EditRecord> {
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { i -> runCatching { record(array.getJSONObject(i)) }.getOrNull() }
    }

    private fun record(o: JSONObject) = EditRecord(
        id = o.getString(ID), copyPath = o.getString(COPY), archiveUri = o.getString(URI), archiveKey = o.getString(KEY),
        archiveName = o.getString(NAME), archiveSize = o.getLong(A_SIZE), archiveMtime = if (o.has(A_MTIME)) o.getLong(A_MTIME) else null,
        archiveFingerprint = o.getString(A_PRINT), archiveFormat = o.getString(FORMAT), entryPath = o.getString(ENTRY),
        size = o.getLong(SIZE), mtime = o.getLong(MTIME), sha256 = o.getString(SHA), updatable = o.getBoolean(UPDATABLE),
        createdMillis = o.getLong(CREATED),
        pending = o.optJSONObject(PENDING)?.let { p -> Snapshot(p.getString(PATH), p.getLong(SIZE), p.getLong(MTIME), p.getString(SHA)) },
    )

    private const val ID = "id"
    private const val COPY = "copy"
    private const val URI = "uri"
    private const val KEY = "key"
    private const val NAME = "name"
    private const val A_SIZE = "archiveSize"
    private const val A_MTIME = "archiveMtime"
    private const val A_PRINT = "archiveFingerprint"
    private const val FORMAT = "format"
    private const val ENTRY = "entry"
    private const val SIZE = "size"
    private const val MTIME = "mtime"
    private const val SHA = "sha256"
    private const val UPDATABLE = "updatable"
    private const val CREATED = "created"
    private const val PENDING = "pending"
    private const val PATH = "path"
}
