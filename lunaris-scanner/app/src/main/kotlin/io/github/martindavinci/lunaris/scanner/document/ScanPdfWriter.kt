package io.github.martindavinci.lunaris.scanner.document

import java.io.OutputStream
import java.util.Locale

private const val CATALOG = 1
private const val PAGES = 2
private const val FIRST_PAGE_OBJECT = 3
private const val OBJECTS_PER_PAGE = 3
private const val FREE_ENTRY = "0000000000 65535 f \n"
private const val OFFSET_DIGITS = 10
private const val HEADER = "%PDF-1.4\n"

/** Tells other tools that the file is binary, as the PDF standard advises. */
private val BINARY_MARK = byteArrayOf(0x25, 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), 0x0A)

/**
 * Writes a PDF one page at a time, each holding its JPEG at exactly the page size, so that only one
 * page has to be in memory. There is no author, date or software name inside.
 */
internal class ScanPdfWriter(private val out: OutputStream) {
    private val offsets = sortedMapOf<Int, Long>()
    private val kids = mutableListOf<Int>()
    private var written = 0L

    init {
        write(HEADER)
        write(BINARY_MARK)
        writeObject(CATALOG, "<< /Type /Catalog /Pages $PAGES 0 R >>")
    }

    fun addJpegPage(jpeg: ByteArray, pixels: PageSize, points: PagePoints) {
        val page = FIRST_PAGE_OBJECT + kids.size * OBJECTS_PER_PAGE
        val image = page + 1
        val content = page + 2
        val width = decimal(points.width)
        val height = decimal(points.height)
        kids += page
        writeObject(
            page,
            "<< /Type /Page /Parent $PAGES 0 R /MediaBox [0 0 $width $height] " +
                "/Resources << /XObject << /Im0 $image 0 R >> >> /Contents $content 0 R >>",
        )
        offsets[image] = written
        write(
            "$image 0 obj\n<< /Type /XObject /Subtype /Image /Width ${pixels.width} /Height ${pixels.height} " +
                "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpeg.size} >>\nstream\n",
        )
        write(jpeg)
        write("\nendstream\nendobj\n")
        val drawing = "q $width 0 0 $height 0 0 cm /Im0 Do Q"
        writeObject(content, "<< /Length ${drawing.length} >>\nstream\n$drawing\nendstream")
    }

    fun finish() {
        writeObject(PAGES, "<< /Type /Pages /Kids [${kids.joinToString(" ") { "$it 0 R" }}] /Count ${kids.size} >>")
        val table = written
        val size = offsets.size + 1
        val entries = offsets.values.joinToString("") { "${it.toString().padStart(OFFSET_DIGITS, '0')} 00000 n \n" }
        write("xref\n0 $size\n$FREE_ENTRY$entries")
        write("trailer\n<< /Size $size /Root $CATALOG 0 R >>\nstartxref\n$table\n%%EOF\n")
        out.flush()
    }

    private fun writeObject(number: Int, body: String) {
        offsets[number] = written
        write("$number 0 obj\n$body\nendobj\n")
    }

    private fun write(text: String) = write(text.toByteArray(Charsets.ISO_8859_1))

    private fun write(bytes: ByteArray) {
        out.write(bytes)
        written += bytes.size
    }

    private fun decimal(value: Float): String = String.format(Locale.ROOT, "%.2f", value)
}
