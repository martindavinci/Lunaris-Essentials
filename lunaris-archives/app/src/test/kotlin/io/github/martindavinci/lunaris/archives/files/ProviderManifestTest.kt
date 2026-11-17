package io.github.martindavinci.lunaris.archives.files

import com.google.common.truth.Truth.assertThat
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.w3c.dom.Element

/**
 * The static FileProvider.getUriForFile reads the paths from the manifest's meta-data: the provider
 * builds its own copy only lazily, so the constructor's resource id is not enough.
 */
class ProviderManifestTest {
    @Test fun theFileProviderDeclaresItsPathsInTheManifest() {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val manifest = factory.newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
        val provider = manifest.getElementsByTagName("provider").item(0) as Element
        val meta = provider.getElementsByTagName("meta-data")
        val paths = (0 until meta.length).map { meta.item(it) as Element }
            .associate { it.getAttributeNS(ANDROID, "name") to it.getAttributeNS(ANDROID, "resource") }
        assertThat(paths).containsEntry("android.support.FILE_PROVIDER_PATHS", "@xml/file_paths")
    }

    private companion object { const val ANDROID = "http://schemas.android.com/apk/res/android" }
}
