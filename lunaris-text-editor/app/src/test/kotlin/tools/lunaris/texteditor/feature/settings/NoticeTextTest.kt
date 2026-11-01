package tools.lunaris.texteditor.feature.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NoticeTextTest {
    @Test fun hardWrappedProse_isJoined() {
        val text = "Permission is hereby granted, free of charge, to any person\nobtaining a copy of this software."
        assertThat(noticeParagraphs(text)).containsExactly(
            "Permission is hereby granted, free of charge, to any person obtaining a copy of this software."
        )
    }

    @Test fun paragraphsAndRules_areSplitAndDropped() {
        val text = "=====================\nComponents\n=====================\n\nFirst.\n\n  \nSecond."
        assertThat(noticeParagraphs(text)).containsExactly("Components", "First.", "Second.").inOrder()
    }

    @Test fun listItems_keepTheirLine_andIndentedContinuationsJoin() {
        val text = "Apache License 2.0\n- AndroidX libraries: activity,\n  collection\n- Dagger"
        assertThat(noticeParagraphs(text)).containsExactly(
            "Apache License 2.0\n- AndroidX libraries: activity, collection\n- Dagger"
        )
    }

    @Test fun labelLines_keepTheirLine() {
        val text = "Files:   r.json\nLicense: https://example.org/LICENSE\nSPDX:    MIT"
        assertThat(noticeParagraphs(text)).containsExactly(text)
    }
}
