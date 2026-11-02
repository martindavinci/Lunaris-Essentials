package io.github.martindavinci.lunaris.scanner.feature.create

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.scanner.model.CodeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateViewModelTest {
    private data class Render(val text: String, val format: CodeFormat, val sizePx: Int)

    private val dispatcher = StandardTestDispatcher()
    private val renders = mutableListOf<Render>()
    private val image = CodeImage(intArrayOf(CodeImage.DARK), 1, 1)
    private var renderedImage: CodeImage? = image
    private val files = FakeFiles()

    private class FakeFiles : CodeImageFiles {
        val saved = mutableListOf<Pair<String, CodeImage>>()
        val discarded = mutableListOf<String>()
        var saveWorks = true
        var shareUri: String? = "content://share/code.png"

        override suspend fun savePng(uri: String, image: CodeImage): Boolean {
            if (saveWorks) saved += uri to image
            return saveWorks
        }

        override suspend fun discard(uri: String) {
            discarded += uri
        }

        override suspend fun cachePngForSharing(image: CodeImage): String? = shareUri
    }

    private fun viewModel() = CreateViewModel(
        renderer = { text, format, sizePx ->
            renders += Render(text, format, sizePx)
            renderedImage
        },
        files = files,
    )

    private fun CreateViewModel.type(text: String) =
        onAction(CreateAction.EditForm(state.value.form.copy(text = text)))

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun preview_waitsForTheDebounce_andEncodesOnlyTheLastEdit() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(CreateAction.PreviewSized(PREVIEW_PX))
        vm.type("a")
        runCurrent()
        advanceTimeBy(PREVIEW_DEBOUNCE_MS - 1)
        runCurrent()
        assertThat(renders).isEmpty()

        vm.type("ab")
        runCurrent()
        advanceTimeBy(PREVIEW_DEBOUNCE_MS - 1)
        runCurrent()
        assertThat(renders).isEmpty()

        advanceUntilIdle()
        assertThat(renders).containsExactly(Render("ab", CodeFormat.QR_CODE, PREVIEW_PX))
        assertThat(vm.state.value.preview).isSameInstanceAs(image)
    }

    @Test fun preview_waitsUntilTheAreaIsMeasured() = runTest(dispatcher) {
        val vm = viewModel()
        vm.type("a")
        advanceUntilIdle()
        assertThat(renders).isEmpty()

        vm.onAction(CreateAction.PreviewSized(PREVIEW_PX))
        advanceUntilIdle()
        assertThat(renders).hasSize(1)
    }

    @Test fun preview_clearsAtOnceWhenTheFormIsEmptied() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(CreateAction.PreviewSized(PREVIEW_PX))
        vm.type("a")
        advanceUntilIdle()
        assertThat(vm.state.value.preview).isNotNull()

        vm.type("")
        runCurrent()
        assertThat(vm.state.value.preview).isNull()
        assertThat(vm.state.value.result).isEqualTo(PayloadResult.Incomplete)
    }

    @Test fun preview_reportsAnEncodingFailure() = runTest(dispatcher) {
        renderedImage = null
        val vm = viewModel()
        vm.onAction(CreateAction.PreviewSized(PREVIEW_PX))
        vm.type("a")
        advanceUntilIdle()

        assertThat(vm.state.value.encodeFailed).isTrue()
        assertThat(vm.state.value.preview).isNull()
    }

    @Test fun selectType_locksStructuredTypesToQr() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(CreateAction.SelectType(CreateType.URL))

        assertThat(vm.state.value.formats).containsExactly(CodeFormat.QR_CODE)
        assertThat(vm.state.value.form.format).isEqualTo(CodeFormat.QR_CODE)
    }

    @Test fun save_writesAFreshLargeImage_andSaysSo() = runTest(dispatcher) {
        val vm = viewModel()
        vm.type("a")
        vm.onAction(CreateAction.SaveTo("content://doc"))
        advanceUntilIdle()

        assertThat(renders).contains(Render("a", CodeFormat.QR_CODE, EXPORT_SIZE_PX))
        assertThat(files.saved).containsExactly("content://doc" to image)
        assertThat(vm.state.value.message).isEqualTo(CreateMessage.SAVED)

        vm.onAction(CreateAction.MessageShown)
        assertThat(vm.state.value.message).isNull()
    }

    @Test fun save_reportsAFailure() = runTest(dispatcher) {
        files.saveWorks = false
        val vm = viewModel()
        vm.type("a")
        vm.onAction(CreateAction.SaveTo("content://doc"))
        advanceUntilIdle()

        assertThat(vm.state.value.message).isEqualTo(CreateMessage.SAVE_FAILED)
        assertThat(files.discarded).containsExactly("content://doc")
    }

    @Test fun save_withoutAValidCode_reportsItAndRemovesTheEmptyFile() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(CreateAction.SaveTo("content://doc"))
        advanceUntilIdle()

        assertThat(files.saved).isEmpty()
        assertThat(files.discarded).containsExactly("content://doc")
        assertThat(vm.state.value.message).isEqualTo(CreateMessage.SAVE_FAILED)
    }

    @Test fun share_handsOverTheUriOnce() = runTest(dispatcher) {
        val vm = viewModel()
        vm.type("a")
        vm.onAction(CreateAction.Share)
        advanceUntilIdle()
        assertThat(vm.state.value.shareUri).isEqualTo(files.shareUri)

        vm.onAction(CreateAction.ShareHandled)
        assertThat(vm.state.value.shareUri).isNull()
    }

    @Test fun share_reportsAFailure() = runTest(dispatcher) {
        files.shareUri = null
        val vm = viewModel()
        vm.type("a")
        vm.onAction(CreateAction.Share)
        advanceUntilIdle()

        assertThat(vm.state.value.shareUri).isNull()
        assertThat(vm.state.value.message).isEqualTo(CreateMessage.SHARE_FAILED)
    }

    private companion object {
        const val PREVIEW_PX = 600
    }
}
