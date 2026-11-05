package io.github.martindavinci.lunaris.scanner.document

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DocumentPhotosTest {
    @Test fun exif_turnsAndMirrors_mirrorFirst() {
        assertThat(ExifTurn.of(1)).isEqualTo(ExifTurn(0, false))
        assertThat(ExifTurn.of(2)).isEqualTo(ExifTurn(0, true))
        assertThat(ExifTurn.of(3)).isEqualTo(ExifTurn(180, false))
        assertThat(ExifTurn.of(4)).isEqualTo(ExifTurn(180, true))
        assertThat(ExifTurn.of(5)).isEqualTo(ExifTurn(270, true))
        assertThat(ExifTurn.of(6)).isEqualTo(ExifTurn(90, false))
        assertThat(ExifTurn.of(7)).isEqualTo(ExifTurn(90, true))
        assertThat(ExifTurn.of(8)).isEqualTo(ExifTurn(270, false))
        assertThat(ExifTurn.of(0)).isEqualTo(ExifTurn(0, false))
    }

    @Test fun sampling_keepsAtLeastTheSizeWeWorkAt() {
        assertThat(workSampleSize(4000, 3000, 4000)).isEqualTo(1)
        assertThat(workSampleSize(5000, 3750, 4000)).isEqualTo(1)
        assertThat(workSampleSize(8000, 6000, 4000)).isEqualTo(2)
        assertThat(workSampleSize(9248, 6936, 4000)).isEqualTo(2)
        assertThat(workSampleSize(3000, 16000, 4000)).isEqualTo(4)
        assertThat(workSampleSize(100, 50, 4000)).isEqualTo(1)
    }

    @Test fun workSize_shrinksOnlyWhatIsTooLong() {
        assertThat(workSize(4500, 3000, 4000)).isEqualTo(PageSize(4000, 2667))
        assertThat(workSize(3000, 4500, 4000)).isEqualTo(PageSize(2667, 4000))
        assertThat(workSize(1000, 500, 4000)).isEqualTo(PageSize(1000, 500))
    }

    @Test fun inbox_givesAPhotoOnlyOnce() {
        val inbox = DocumentInbox()
        val photo = ImportedPhoto("path", PageSize(1, 1))

        inbox.put(photo)

        assertThat(inbox.take()).isEqualTo(photo)
        assertThat(inbox.take()).isNull()
    }
}
