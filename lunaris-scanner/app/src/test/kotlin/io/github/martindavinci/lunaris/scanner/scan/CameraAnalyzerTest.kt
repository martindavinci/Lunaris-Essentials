package io.github.martindavinci.lunaris.scanner.scan

import com.google.common.truth.Truth.assertThat
import com.google.zxing.BarcodeFormat
import io.github.martindavinci.lunaris.scanner.model.CodeFormat
import io.github.martindavinci.lunaris.scanner.model.ScannedCode
import org.junit.Test

class CameraAnalyzerTest {
    private var now = 0L
    private val found = mutableListOf<ScannedCode>()
    private val analyzer = CameraAnalyzer(CodeDecoder(), clock = { now }) { found += it }

    private val qr = codeImage("frame", BarcodeFormat.QR_CODE)
    private val qrCode = ScannedCode("frame", CodeFormat.QR_CODE)
    private val barcode = codeImage("5901234123457", BarcodeFormat.EAN_13)
    private val barcodeCode = ScannedCode("5901234123457", CodeFormat.EAN_13)

    private fun analyze(image: CodeImage, rotationDegrees: Int = 0) =
        analyzer.analyzeFrame(image.luminance, image.width, image.height, rotationDegrees)

    private fun analyzeTwice(image: CodeImage, rotationDegrees: Int = 0) {
        analyze(image, rotationDegrees)
        analyze(image, rotationDegrees)
    }

    @Test fun beforeResume_framesAreIgnored() {
        analyze(qr)

        assertThat(found).isEmpty()
    }

    @Test fun firstHit_isEmitted() {
        analyzer.resume()

        analyze(qr)

        assertThat(found).containsExactly(qrCode)
    }

    @Test fun afterAHit_itPausesUntilResumed() {
        analyzer.resume()
        analyze(qr)

        analyze(barcode)
        assertThat(found).containsExactly(qrCode)

        analyzer.resume()
        analyzeTwice(barcode)
        assertThat(found).containsExactly(qrCode, barcodeCode).inOrder()
    }

    @Test fun pause_stopsEmitting() {
        analyzer.resume()
        analyzer.pause()

        analyze(qr)

        assertThat(found).isEmpty()
    }

    @Test fun frameWithoutCode_keepsScanning() {
        analyzer.resume()
        val blank = ByteArray(BLANK_SIDE * BLANK_SIDE) { WHITE_LUMA }

        analyzer.analyzeFrame(blank, BLANK_SIDE, BLANK_SIDE, rotationDegrees = 0)
        analyze(qr)

        assertThat(found).containsExactly(qrCode)
    }

    @Test fun barcodeAcrossTheRows_isRead() {
        analyzer.resume()

        analyzeTwice(barcode.rotated())

        assertThat(found).containsExactly(barcodeCode)
    }

    @Test fun barcodeLevelOnScreen_isReadInAQuarterTurnedFrame() {
        analyzer.resume()

        analyzeTwice(barcode.rotated(), rotationDegrees = 90)

        assertThat(found).containsExactly(barcodeCode)
    }

    @Test fun barcodeAlongTheRows_isReadWhateverTheRotation() {
        analyzer.resume()

        analyzeTwice(barcode, rotationDegrees = 270)

        assertThat(found).containsExactly(barcodeCode)
    }

    @Test fun codeStillInViewOnReturn_doesNotReopen() {
        analyzer.resume()
        analyze(qr)

        now = LONG_AFTER
        analyzer.resume()
        analyze(qr)

        assertThat(found).containsExactly(qrCode)
    }

    @Test fun anotherCodeOnReturn_isEmitted() {
        analyzer.resume()
        analyze(qr)

        now = LONG_AFTER
        analyzer.resume()
        analyzeTwice(barcode)

        assertThat(found).containsExactly(qrCode, barcodeCode).inOrder()
    }

    @Test fun codeFoundWhilePausing_doesNotCountAsSeenOnReturn() {
        analyzer.resume()
        analyzer.pause()
        analyzer.deliver(qrCode)
        assertThat(found).isEmpty()

        analyzer.resume()
        analyze(qr)

        assertThat(found).containsExactly(qrCode)
    }

    @Test fun weakBarcodeReadOnce_isNotEmitted() {
        analyzer.resume()

        analyze(barcode)

        assertThat(found).isEmpty()
    }

    @Test fun differentWeakReads_doNotAgree() {
        analyzer.resume()

        analyzer.deliver(ScannedCode("01234565", CodeFormat.UPC_E))
        analyzer.deliver(barcodeCode)

        assertThat(found).isEmpty()
    }

    @Test fun barcodeWithAStrongCheck_isEmittedAtOnce() {
        val code128 = ScannedCode("CODE128", CodeFormat.CODE_128)
        analyzer.resume()

        analyzer.deliver(code128)

        assertThat(found).containsExactly(code128)
    }

    private companion object {
        const val BLANK_SIDE = 100
        const val LONG_AFTER = 60_000L
    }
}
