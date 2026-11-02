package io.github.martindavinci.lunaris.scanner.feature.result

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.scanner.model.CodeFormat
import io.github.martindavinci.lunaris.scanner.model.ScannedCode
import io.github.martindavinci.lunaris.scanner.session.CurrentResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResultViewModelTest {
    private val currentResult = CurrentResult()
    private val first = ScannedCode("WIFI:S:home;T:WPA;P:secret123;;", CodeFormat.QR_CODE)
    private val second = ScannedCode("WIFI:S:office;T:WPA;P:another123;;", CodeFormat.QR_CODE)

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun noCurrentResult_hasNoCode() {
        assertThat(ResultViewModel(currentResult).state.value.code).isNull()
    }

    @Test fun startsFromTheCurrentResult() {
        currentResult.set(first)
        assertThat(ResultViewModel(currentResult).state.value.code).isEqualTo(first)
    }

    @Test fun passwordStartsHidden_andTogglesBothWays() {
        currentResult.set(first)
        val viewModel = ResultViewModel(currentResult)
        assertThat(viewModel.state.value.secretRevealed).isFalse()

        viewModel.onAction(ResultUiAction.ToggleSecret)
        assertThat(viewModel.state.value.secretRevealed).isTrue()

        viewModel.onAction(ResultUiAction.ToggleSecret)
        assertThat(viewModel.state.value.secretRevealed).isFalse()
    }

    @Test fun aNewCode_hidesThePasswordAgain() {
        currentResult.set(first)
        val viewModel = ResultViewModel(currentResult)
        viewModel.onAction(ResultUiAction.ToggleSecret)

        currentResult.set(second)
        assertThat(viewModel.state.value.code).isEqualTo(second)
        assertThat(viewModel.state.value.secretRevealed).isFalse()

        viewModel.onAction(ResultUiAction.ToggleSecret)
        assertThat(viewModel.state.value.secretRevealed).isTrue()
    }
}
